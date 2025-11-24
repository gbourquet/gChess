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
package com.gchess.infrastructure.websocket.manager

import com.gchess.infrastructure.websocket.dto.WebSocketMessage
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.UserId
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages WebSocket connections for spectators observing games.
 *
 * Architecture:
 * - Map<GameId, MutableSet<Pair<UserId, WebSocketSession>>> - Multiple spectators per game
 * - Each spectator identified by UserId (not PlayerId, as they're not participants)
 * - Thread-safe using ConcurrentHashMap with synchronized set operations
 *
 * Responsibilities:
 * - Register/unregister spectators for games
 * - Broadcast messages to all spectators of a game
 * - Get spectator count per game
 * - Clean up dead connections
 */
class SpectatorConnectionManager {
    private val logger = LoggerFactory.getLogger(SpectatorConnectionManager::class.java)
    // GameId -> Set of (UserId, WebSocketSession) pairs
    private val spectators = ConcurrentHashMap<GameId, MutableSet<Pair<UserId, WebSocketSession>>>()
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    /**
     * Register a spectator for a game.
     * If the user is already spectating the game, their session will be replaced.
     */
    fun register(gameId: GameId, userId: UserId, session: WebSocketSession) {
        spectators.compute(gameId) { _, existingSet ->
            val set = existingSet ?: mutableSetOf()
            // Remove old session for this user (if exists)
            set.removeIf { (id, _) -> id == userId }
            // Add new session
            set.add(Pair(userId, session))
            logger.info("User $userId joined as spectator for game $gameId (spectators: ${set.size})")
            set
        }
    }

    /**
     * Unregister a spectator from a game.
     * @return true if the spectator was removed, false if not found
     */
    fun unregister(gameId: GameId, userId: UserId): Boolean {
        var removed = false
        spectators.computeIfPresent(gameId) { _, set ->
            removed = set.removeIf { (id, _) -> id == userId }
            if (removed) {
                logger.info("User $userId left as spectator from game $gameId (spectators: ${set.size})")
            }
            // Remove the game entry if no more spectators
            if (set.isEmpty()) null else set
        }
        return removed
    }

    /**
     * Broadcast a message to all spectators of a game.
     * @return number of spectators who received the message
     */
    suspend fun broadcastToSpectators(gameId: GameId, message: WebSocketMessage): Int {
        val gameSpectators = spectators[gameId] ?: return 0

        var sentCount = 0
        val deadSpectators = mutableListOf<UserId>()

        for ((userId, session) in gameSpectators) {
            try {
                val json = json.encodeToString(WebSocketMessage.serializer(), message)
                session.send(Frame.Text(json))
                sentCount++
            } catch (e: Exception) {
                logger.error("Failed to send message to spectator $userId for game $gameId", e)
                deadSpectators.add(userId)
            }
        }

        // Clean up dead connections
        if (deadSpectators.isNotEmpty()) {
            deadSpectators.forEach { userId -> unregister(gameId, userId) }
        }

        logger.debug("Broadcast ${message.type} to $sentCount spectators of game $gameId")
        return sentCount
    }

    /**
     * Get the number of spectators for a game.
     */
    fun getSpectatorCount(gameId: GameId): Int {
        return spectators[gameId]?.size ?: 0
    }

    /**
     * Get all gameIds that have spectators.
     */
    fun getGamesWithSpectators(): Set<GameId> {
        return spectators.keys.toSet()
    }
}
