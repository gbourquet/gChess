package com.gchess.matchmaking.infrastructure.adapter.driver

import com.gchess.matchmaking.infrastructure.adapter.driver.dto.MatchmakingMessage
import com.gchess.shared.domain.model.UserId
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages WebSocket connections for users in the matchmaking queue.
 *
 * Architecture:
 * - Map<UserId, WebSocketSession> - One connection per user
 * - Last connection replaces previous one (single active connection per user)
 * - Thread-safe using ConcurrentHashMap
 *
 * Responsibilities:
 * - Register/unregister users in matchmaking
 * - Send messages to specific users
 * - Clean up dead connections
 */
class MatchmakingConnectionManager {
    private val logger = LoggerFactory.getLogger(MatchmakingConnectionManager::class.java)
    private val connections = ConcurrentHashMap<UserId, WebSocketSession>()
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    /**
     * Register a user's WebSocket session for matchmaking.
     * If the user already has a session, it will be replaced with the new one.
     */
    fun register(userId: UserId, session: WebSocketSession) {
        val previous = connections.put(userId, session)
        if (previous != null) {
            logger.warn("User $userId replaced existing matchmaking connection")
        } else {
            logger.info("User $userId joined matchmaking (connections: ${connections.size})")
        }
    }

    /**
     * Unregister a user's WebSocket session.
     * @return true if the user was removed, false if not found
     */
    suspend fun unregister(userId: UserId): Boolean {
        val connection = connections.remove(userId)
        val removed = connection != null
        if (removed) {
            connection!!.close(CloseReason(CloseReason.Codes.NORMAL, "Matchmaking terminated"))
            logger.info("User $userId left matchmaking (connections: ${connections.size})")
        }
        return removed
    }

    /**
     * Send a message to a specific user.
     * @return true if sent successfully, false if user not connected
     */
    suspend fun send(userId: UserId, message: MatchmakingMessage): Boolean {
        val session = connections[userId]
        if (session == null) {
            logger.warn("Cannot send message to $userId: not connected")
            return false
        }

        return try {
            val json = json.encodeToString(MatchmakingMessage.serializer(), message)
            session.send(Frame.Text(json))
            logger.debug("Sent ${message.type} to user $userId")
            true
        } catch (e: Exception) {
            logger.error("Failed to send message to $userId", e)
            // Remove dead connection
            connections.remove(userId)
            false
        }
    }

    /**
     * Check if a user is currently connected.
     */
    fun isConnected(userId: UserId): Boolean {
        return connections.containsKey(userId)
    }

    /**
     * Get the number of active connections.
     */
    fun connectionCount(): Int {
        return connections.size
    }
}