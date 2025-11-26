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
package com.gchess.matchmaking.infrastructure.adapter.driver

import com.gchess.matchmaking.application.usecase.JoinMatchmakingUseCase
import com.gchess.matchmaking.application.usecase.LeaveMatchmakingUseCase
import com.gchess.matchmaking.infrastructure.adapter.driver.dto.AuthFailedMessage
import com.gchess.matchmaking.infrastructure.adapter.driver.dto.AuthSuccessMessage
import com.gchess.matchmaking.infrastructure.adapter.driver.dto.JoinQueueMessage
import com.gchess.matchmaking.infrastructure.adapter.driver.dto.MatchmakingErrorMessage
import com.gchess.matchmaking.infrastructure.adapter.driver.dto.MatchmakingMessage
import com.gchess.shared.infrastructure.websocket.WebSocketJwtAuth
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

/**
 * Configure all WebSocket routes.
 * Phase 1 implementation: Basic infrastructure with authentication and connection management.
 */
fun Application.configureMatchmakingWebSocketRoutes() {
    val logger = LoggerFactory.getLogger("WebSocketRoutes")
    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    // Inject connection managers
    val matchmakingManager by inject<MatchmakingConnectionManager>()

    // Inject use cases
    val joinMatchmakingUseCase by inject<JoinMatchmakingUseCase>()
    val leaveMatchmakingUseCase by inject<LeaveMatchmakingUseCase>()

    routing {
        // ========== Matchmaking WebSocket ==========
        webSocket("/ws/matchmaking") {
            logger.info("New WebSocket connection attempt on /ws/matchmaking")

            // Authenticate
            val userId = WebSocketJwtAuth.authenticate(
                call = call,
                session = this,
                onSuccess = { userId ->
                    val message = AuthSuccessMessage(userId = userId.toString())
                    val jsonMessage = json.encodeToString(MatchmakingMessage.serializer(), message)
                    send(Frame.Text(jsonMessage))
                },
                onFailure = { reason ->
                    val message = AuthFailedMessage(reason = reason)
                    val jsonMessage = json.encodeToString(MatchmakingMessage.serializer(), message)
                    send(Frame.Text(jsonMessage))
                }
            )
            if (userId == null) {
                logger.warn("Authentication failed, closing connection")
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication required"))
                return@webSocket
            }

            // Register connection
            matchmakingManager.register(userId, this)

            try {
                // Listen for incoming messages
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        logger.debug("Received message from user $userId: $text")

                        try {
                            // Parse message
                            when (val message = json.decodeFromString<MatchmakingMessage>(text)) {
                                is JoinQueueMessage -> {
                                    logger.info("User $userId joining matchmaking queue")

                                    // Call the use case
                                    val result = joinMatchmakingUseCase.execute(userId)

                                    if (result.isFailure) {
                                        // Send error notification and close ws
                                        val error = result.exceptionOrNull()!!
                                        val errorMsg = MatchmakingErrorMessage(
                                            code = "MATCHMAKING_ERROR",
                                            message = error.message ?: "Unknown error"
                                        )
                                        matchmakingManager.send(userId, errorMsg)
                                        logger.warn("Matchmaking failed for user $userId: ${error.message}")
                                    } else {
                                        // Success - notifications already sent by the use case
                                        logger.info("Matchmaking request processed for user $userId")
                                    }
                                }

                                else -> {
                                    logger.warn("Unknown message type received from user $userId: ${message.type}")
                                    val errorMsg = MatchmakingErrorMessage(
                                        code = "UNKNOWN_MESSAGE_TYPE",
                                        message = "Unknown message type: ${message.type}"
                                    )
                                    matchmakingManager.send(userId, errorMsg)
                                    close(CloseReason(CloseReason.Codes.NORMAL, errorMsg.message))

                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Error parsing message from user $userId", e)
                            val errorMsg = MatchmakingErrorMessage(
                                code = "INVALID_MESSAGE",
                                message = "Failed to parse message"
                            )
                            matchmakingManager.send(userId, errorMsg)
                            close(CloseReason(CloseReason.Codes.NORMAL, errorMsg.message))
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in matchmaking WebSocket for user $userId", e)
            } finally {
                // Unregister on disconnection and remove from queue
                matchmakingManager.unregister(userId)
                leaveMatchmakingUseCase.execute(userId)
                logger.info("User $userId disconnected from matchmaking")
            }
        }
    }
}
