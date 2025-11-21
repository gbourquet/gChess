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
package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.matchmaking.domain.model.Match
import com.gchess.matchmaking.domain.port.MatchRepository
import com.gchess.shared.domain.model.PlayerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of MatchRepository.
 *
 * This implementation:
 * - Stores matches indexed by player ID (2 entries per match: one for each player)
 * - Uses ConcurrentHashMap for thread-safe operations
 * - TTL (Time To Live) is managed by the Match entity itself via isExpired()
 * - Automatically removes expired matches on cleanup
 */
class InMemoryMatchRepository : MatchRepository {

    // Maps PlayerId to Match (2 entries per match: whitePlayer and blackPlayer)
    private val matchesByPlayer = ConcurrentHashMap<PlayerId, Match>()

    override suspend fun save(match: Match): Unit = withContext(Dispatchers.IO) {
        // Save the match under both player IDs
        matchesByPlayer[match.whitePlayerId] = match
        matchesByPlayer[match.blackPlayerId] = match
    }

    override suspend fun findByPlayer(playerId: PlayerId): Match? = withContext(Dispatchers.IO) {
        matchesByPlayer[playerId]
    }

    override suspend fun delete(playerId: PlayerId): Unit = withContext(Dispatchers.IO) {
        // Find the match for this player
        val match = matchesByPlayer[playerId]

        if (match != null) {
            // Remove both entries (white and black player)
            matchesByPlayer.remove(match.whitePlayerId)
            matchesByPlayer.remove(match.blackPlayerId)
        }
    }

    override suspend fun deleteExpiredMatches(): Unit = withContext(Dispatchers.IO) {
        // Find all expired matches
        val expiredMatches = matchesByPlayer.values
            .filter { it.isExpired() }
            .distinctBy { it.gameId } // Avoid processing same match twice

        // Delete each expired match (removes both player entries)
        expiredMatches.forEach { match ->
            matchesByPlayer.remove(match.whitePlayerId)
            matchesByPlayer.remove(match.blackPlayerId)
        }
    }
}
