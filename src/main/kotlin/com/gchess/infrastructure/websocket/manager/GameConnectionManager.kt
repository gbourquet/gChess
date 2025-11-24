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

import com.gchess.chess.domain.model.Game
import com.gchess.infrastructure.websocket.dto.WebSocketMessage
import com.gchess.shared.domain.model.PlayerId
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages WebSocket connections for players participating in games.
 *
 * Architecture:
 * - Map<PlayerId, WebSocketSession> - One connection per player participation
 * - Multi-device: A user can have multiple connections (different games/PlayerId)
 * - One PlayerId = One participation = One connection
 * - Thread-safe using ConcurrentHashMap
 *
 * Key Design:
 * - Indexed by PlayerId (ephemeral, per-game identity)
 * - Enables multi-device: same UserId can have N connections (different games)
 * - Perfect isolation: each WebSocket receives only events from ITS game
 *
 * Responsibilities:
 * - Register/unregister players in games
 * - Send messages to specific players
 * - Broadcast messages to all players in a game
 * - Clean up dead connections
 */
class GameConnectionManager {
    private val logger = LoggerFactory.getLogger(GameConnectionManager::class.java)
    private val connections = ConcurrentHashMap<PlayerId, WebSocketSession>()
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    /**
     * Register a player's WebSocket session for a game.
     * If the playerId already has a session, it will be replaced (reconnection).
     */
    fun register(playerId: PlayerId, session: WebSocketSession) {
        val previous = connections.put(playerId, session)
        if (previous != null) {
            logger.warn("Player $playerId replaced existing game connection (reconnection)")
        } else {
            logger.info("Player $playerId connected to game (connections: ${connections.size})")
        }
    }

    /**
     * Unregister a player's WebSocket session.
     * @return true if the player was removed, false if not found
     */
    fun unregister(playerId: PlayerId): Boolean {
        val removed = connections.remove(playerId) != null
        if (removed) {
            logger.info("Player $playerId disconnected from game (connections: ${connections.size})")
        }
        return removed
    }

    /**
     * Send a message to a specific player.
     * @return true if sent successfully, false if player not connected
     */
    suspend fun send(playerId: PlayerId, message: WebSocketMessage): Boolean {
        val session = connections[playerId]
        if (session == null) {
            logger.debug("Cannot send message to $playerId: not connected")
            return false
        }

        return try {
            val json = json.encodeToString(WebSocketMessage.serializer(), message)
            session.send(Frame.Text(json))
            logger.debug("Sent ${message.type} to player $playerId")
            true
        } catch (e: Exception) {
            logger.error("Failed to send message to $playerId", e)
            // Remove dead connection
            connections.remove(playerId)
            false
        }
    }

    /**
     * Broadcast a message to all players in a game.
     * Sends to both whitePlayer and blackPlayer (if connected).
     *
     * @return Pair(whiteSent, blackSent) - true if sent successfully
     */
    suspend fun broadcastToGame(game: Game, message: WebSocketMessage): Pair<Boolean, Boolean> {
        val whitePlayerId = game.whitePlayer.id
        val blackPlayerId = game.blackPlayer.id

        val whiteSent = send(whitePlayerId, message)
        val blackSent = send(blackPlayerId, message)

        logger.debug("Broadcast ${message.type} to game ${game.id}: white=$whiteSent, black=$blackSent")

        return Pair(whiteSent, blackSent)
    }

    /**
     * Check if a player is currently connected.
     */
    fun isConnected(playerId: PlayerId): Boolean {
        return connections.containsKey(playerId)
    }

    /**
     * Get the number of active connections.
     */
    fun connectionCount(): Int {
        return connections.size
    }
}
