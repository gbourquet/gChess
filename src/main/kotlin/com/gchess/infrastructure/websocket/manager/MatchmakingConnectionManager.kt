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
import com.gchess.shared.domain.model.UserId
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
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
    fun unregister(userId: UserId): Boolean {
        val removed = connections.remove(userId) != null
        if (removed) {
            logger.info("User $userId left matchmaking (connections: ${connections.size})")
        }
        return removed
    }

    /**
     * Send a message to a specific user.
     * @return true if sent successfully, false if user not connected
     */
    suspend fun send(userId: UserId, message: WebSocketMessage): Boolean {
        val session = connections[userId]
        if (session == null) {
            logger.warn("Cannot send message to $userId: not connected")
            return false
        }

        return try {
            val json = json.encodeToString(WebSocketMessage.serializer(), message)
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
