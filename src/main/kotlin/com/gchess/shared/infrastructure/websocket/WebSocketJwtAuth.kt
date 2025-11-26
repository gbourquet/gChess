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
package com.gchess.shared.infrastructure.websocket

import com.gchess.infrastructure.config.JwtConfig
import com.gchess.shared.domain.model.UserId
import io.ktor.server.application.ApplicationCall
import io.ktor.websocket.WebSocketSession
import org.slf4j.LoggerFactory

/**
 * Shared WebSocket JWT authentication utility for all bounded contexts.
 *
 * This class provides generic JWT authentication for WebSocket connections.
 * Each bounded context is responsible for sending its own authentication messages
 * via the provided callbacks.
 *
 * Design:
 * - Generic: No dependencies on any bounded context (Chess, Matchmaking, User)
 * - Callbacks: Each context provides its own success/failure message handlers
 * - Shared Kernel: Part of the technical infrastructure shared across contexts
 *
 * When migrating to microservices:
 * - This class can be extracted to a shared library (e.g., gchess-shared-lib)
 * - Each microservice imports it as a Maven/Gradle dependency
 */
object WebSocketJwtAuth {
    private val logger = LoggerFactory.getLogger(WebSocketJwtAuth::class.java)

    /**
     * Authenticate a WebSocket connection using JWT.
     *
     * The JWT token can be provided in two ways:
     * 1. As a query parameter: ?token=...
     * 2. In the Sec-WebSocket-Protocol header (for browsers that don't support custom headers)
     *
     * @param call The Ktor ApplicationCall
     * @param session The WebSocket session
     * @param onSuccess Callback to send authentication success message (context-specific)
     * @param onFailure Callback to send authentication failure message (context-specific)
     * @return UserId if authentication successful, null otherwise
     */
    suspend fun authenticate(
        call: ApplicationCall,
        session: WebSocketSession,
        onSuccess: suspend (UserId) -> Unit,
        onFailure: suspend (String) -> Unit
    ): UserId? {
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
            onFailure("No authentication token provided")
            return null
        }

        return try {
            val verifier = JwtConfig.makeVerifier()
            val decodedJWT = verifier.verify(token)
            val userIdString = decodedJWT.getClaim("userId").asString()

            if (userIdString == null) {
                logger.warn("WebSocket authentication failed: userId claim missing")
                onFailure("Invalid token: userId claim missing")
                return null
            }

            val userId = UserId.fromString(userIdString)

            // Notify success
            onSuccess(userId)

            logger.info("WebSocket authenticated for user $userId")
            userId
        } catch (e: Exception) {
            logger.warn("WebSocket authentication failed: ${e.message}")
            onFailure("Invalid or expired token")
            null
        }
    }
}
