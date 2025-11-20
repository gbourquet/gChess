package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.matchmaking.domain.model.QueueEntry
import com.gchess.matchmaking.domain.port.MatchmakingQueue
import com.gchess.shared.domain.model.PlayerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * In-memory implementation of MatchmakingQueue.
 *
 * This implementation uses:
 * - ConcurrentLinkedQueue for FIFO ordering (thread-safe)
 * - ConcurrentHashMap for fast player lookups by ID
 * - ReentrantLock to ensure atomicity of addPlayer + findMatch operations
 *
 * Thread-safety strategy:
 * - addPlayer() and findMatch() are protected by a lock to ensure atomic operations
 * - removePlayer() is also protected to avoid race conditions
 * - Read operations (isPlayerInQueue, getQueueSize) are lock-free for performance
 */
class InMemoryMatchmakingQueue : MatchmakingQueue {

    private val lock = ReentrantLock()
    private val queue = ConcurrentLinkedQueue<QueueEntry>()
    private val indexByPlayer = ConcurrentHashMap<PlayerId, QueueEntry>()

    override suspend fun addPlayer(playerId: PlayerId): QueueEntry = withContext(Dispatchers.IO) {
        lock.withLock {
            // Check if player already in queue
            if (indexByPlayer.containsKey(playerId)) {
                throw IllegalStateException("Player $playerId is already in the queue")
            }

            // Create queue entry
            val entry = QueueEntry(
                playerId = playerId,
                joinedAt = Clock.System.now()
            )

            // Add to both queue and index
            queue.add(entry)
            indexByPlayer[playerId] = entry

            entry
        }
    }

    override suspend fun removePlayer(playerId: PlayerId): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val entry = indexByPlayer.remove(playerId)
            if (entry != null) {
                queue.remove(entry)
                true
            } else {
                false
            }
        }
    }

    override suspend fun findMatch(): Pair<QueueEntry, QueueEntry>? = withContext(Dispatchers.IO) {
        lock.withLock {
            // Need at least 2 players
            if (queue.size < 2) {
                return@withContext null
            }

            // Poll the two oldest entries (FIFO)
            val player1Entry = queue.poll() ?: return@withContext null
            val player2Entry = queue.poll() ?: run {
                // If second poll fails, put first player back
                queue.add(player1Entry)
                return@withContext null
            }

            // Remove from index
            indexByPlayer.remove(player1Entry.playerId)
            indexByPlayer.remove(player2Entry.playerId)

            // Return the match
            Pair(player1Entry, player2Entry)
        }
    }

    override suspend fun isPlayerInQueue(playerId: PlayerId): Boolean = withContext(Dispatchers.IO) {
        indexByPlayer.containsKey(playerId)
    }

    override suspend fun getQueueSize(): Int = withContext(Dispatchers.IO) {
        queue.size
    }
}
