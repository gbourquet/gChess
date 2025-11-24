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
package com.gchess.infrastructure.websocket.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.gchess.infrastructure.config.JwtConfig
import com.gchess.infrastructure.websocket.dto.AuthFailedMessage
import com.gchess.infrastructure.websocket.dto.AuthSuccessMessage
import com.gchess.infrastructure.websocket.dto.WebSocketMessage
import com.gchess.shared.domain.model.UserId
import io.ktor.server.application.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Helper object for WebSocket JWT authentication.
 */
object WebSocketAuth {
    private val logger = LoggerFactory.getLogger(WebSocketAuth::class.java)
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    /**
     * Authenticate a WebSocket connection using JWT.
     *
     * The JWT token can be provided in two ways:
     * 1. As a query parameter: ?token=...
     * 2. In the Sec-WebSocket-Protocol header (for browsers that don't support custom headers)
     *
     * @return UserId if authentication successful, null otherwise
     */
    suspend fun authenticate(call: ApplicationCall, session: WebSocketSession): UserId? {
        // Try to get token from query parameter
        var token = call.request.queryParameters["token"]

        // If not found, try to get from Sec-WebSocket-Protocol header
        if (token == null) {
            // Some WebSocket clients send the token in this header
            // Format: "Bearer <token>" or just "<token>"
            val protocolHeader = call.request.headers["Sec-WebSocket-Protocol"]
            token = protocolHeader?.removePrefix("Bearer ")?.trim()
        }

        if (token == null) {
            logger.warn("WebSocket authentication failed: no token provided")
            sendAuthFailed(session, "No authentication token provided")
            return null
        }

        return try {
            val verifier = JwtConfig.makeVerifier()
            val decodedJWT = verifier.verify(token)
            val userIdString = decodedJWT.getClaim("userId").asString()

            if (userIdString == null) {
                logger.warn("WebSocket authentication failed: userId claim missing")
                sendAuthFailed(session, "Invalid token: userId claim missing")
                return null
            }

            val userId = UserId.fromString(userIdString)

            // Send authentication success message
            sendAuthSuccess(session, userId)

            logger.info("WebSocket authenticated for user $userId")
            userId
        } catch (e: Exception) {
            logger.warn("WebSocket authentication failed: ${e.message}")
            sendAuthFailed(session, "Invalid or expired token")
            null
        }
    }

    /**
     * Send authentication success message to the client.
     */
    private suspend fun sendAuthSuccess(session: WebSocketSession, userId: UserId) {
        try {
            val message = AuthSuccessMessage(userId = userId.toString()) as WebSocketMessage
            val jsonMessage = json.encodeToString(WebSocketMessage.serializer(), message)
            session.send(Frame.Text(jsonMessage))
        } catch (e: Exception) {
            logger.error("Failed to send auth success message", e)
        }
    }

    /**
     * Send authentication failed message to the client.
     */
    private suspend fun sendAuthFailed(session: WebSocketSession, reason: String) {
        try {
            val message = AuthFailedMessage(reason = reason) as WebSocketMessage
            val jsonMessage = json.encodeToString(WebSocketMessage.serializer(), message)
            session.send(Frame.Text(jsonMessage))
        } catch (e: Exception) {
            logger.error("Failed to send auth failed message", e)
        }
    }
}
