package com.gchess.chess.infrastructure.adapter.driver

import com.gchess.chess.domain.model.Game
import com.gchess.chess.infrastructure.adapter.driver.dto.GameWebSocketMessage
import com.gchess.shared.domain.model.PlayerId
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
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
    suspend fun send(playerId: PlayerId, message: GameWebSocketMessage): Boolean {
        val session = connections[playerId]
        if (session == null) {
            logger.debug("Cannot send message to $playerId: not connected")
            return false
        }

        return try {
            val json = json.encodeToString(GameWebSocketMessage.serializer(), message)
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
    suspend fun broadcastToGame(game: Game, message: GameWebSocketMessage): Pair<Boolean, Boolean> {
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