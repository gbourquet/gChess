/*
 * Copyright (c) 2024-2025 Guillaume Bourquet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.gchess.bot.domain.service

import com.gchess.chess.domain.model.Move
import java.util.concurrent.ConcurrentHashMap

/**
 * Type of node in the search tree.
 * Used to interpret the score stored in the Transposition Table.
 */
enum class TTNodeType {
    /** Exact score (PV node) */
    EXACT,
    /** Lower bound (beta cutoff, score >= stored value) */
    LOWERBOUND,
    /** Upper bound (alpha failed, score <= stored value) */
    UPPERBOUND
}

/**
 * Entry in the Transposition Table.
 *
 * @property hash Zobrist hash of the position
 * @property depth Depth at which this position was searched
 * @property score Evaluation score
 * @property bestMove Best move found (null if no move)
 * @property nodeType Type of node (EXACT, LOWERBOUND, UPPERBOUND)
 */
data class TTEntry(
    val hash: Long,
    val depth: Int,
    val score: Int,
    val bestMove: Move?,
    val nodeType: TTNodeType
)

/**
 * Thread-safe Transposition Table for chess engine.
 *
 * Stores previously evaluated positions to avoid redundant computation.
 * Essential for Lazy SMP parallel search.
 *
 * @property maxSizeMB Maximum size in megabytes (default 256 MB)
 */
class TranspositionTable(maxSizeMB: Int = 256) {
    // Estimate: each entry is ~80 bytes (hash, depth, score, Move object, enum)
    private val maxEntries = (maxSizeMB * 1024L * 1024L) / 80L

    private val table = ConcurrentHashMap<Long, TTEntry>(maxEntries.toInt())

    /**
     * Store an entry in the transposition table.
     * Uses "always replace" strategy for simplicity.
     * Could be enhanced with depth-preferred or age-based replacement.
     */
    fun store(hash: Long, depth: Int, score: Int, bestMove: Move?, nodeType: TTNodeType) {
        // Simple always-replace strategy
        // More sophisticated: replace if depth >= existing depth
        val entry = TTEntry(hash, depth, score, bestMove, nodeType)
        table[hash] = entry

        // Optional: enforce size limit (evict oldest entries if needed)
        if (table.size > maxEntries) {
            // Simple eviction: remove first entry (not optimal, but prevents unbounded growth)
            val firstKey = table.keys.firstOrNull()
            if (firstKey != null) {
                table.remove(firstKey)
            }
        }
    }

    /**
     * Probe the transposition table for a position.
     *
     * @param hash Zobrist hash of the position
     * @param depth Current search depth
     * @param alpha Current alpha value
     * @param beta Current beta value
     * @return Pair<score, bestMove> if usable entry found, null otherwise
     */
    fun probe(hash: Long, depth: Int, alpha: Int, beta: Int): Pair<Int, Move?>? {
        val entry = table[hash] ?: return null

        // Verify hash match (handles collisions)
        if (entry.hash != hash) return null

        // Only use entry if searched to at least the same depth
        if (entry.depth < depth) {
            // Still return best move for move ordering
            return null
        }

        // Check if score is usable based on node type
        val score = entry.score
        val usableScore = when (entry.nodeType) {
            TTNodeType.EXACT -> {
                // Exact score: always usable
                score
            }
            TTNodeType.LOWERBOUND -> {
                // Lower bound: score >= entry.score
                if (score >= beta) score else null
            }
            TTNodeType.UPPERBOUND -> {
                // Upper bound: score <= entry.score
                if (score <= alpha) score else null
            }
        }

        return if (usableScore != null) {
            Pair(usableScore, entry.bestMove)
        } else {
            // Score not usable, but return best move for move ordering
            null
        }
    }

    /**
     * Get best move from TT without score (for move ordering).
     */
    fun getBestMove(hash: Long): Move? {
        return table[hash]?.bestMove
    }

    /**
     * Clear all entries from the table.
     */
    fun clear() {
        table.clear()
    }

    /**
     * Get current number of entries.
     */
    fun size(): Int = table.size

    /**
     * Get fill ratio (0.0 to 1.0).
     */
    fun fillRatio(): Double = table.size.toDouble() / maxEntries.toDouble()
}
