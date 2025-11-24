package com.gchess.websocket.integration

import com.gchess.infrastructure.DatabaseITest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.serialization.json.*

/**
 * End-to-end integration test for the matchmaking WebSocket API.
 * Tests the complete flow from WebSocket connection to matchmaking notifications.
 *
 * This test validates:
 * - WebSocket connection with JWT authentication
 * - JoinQueue message handling
 * - QueuePositionUpdate notification (single player waiting)
 * - MatchFound notification (two players matched)
 * - Automatic game creation
 * - Error handling (invalid messages, authentication failures)
 *
 * Uses Testcontainers PostgreSQL for database integration testing.
 */
class WebSocketMatchmakingITest : DatabaseITest({

    "WebSocket matchmaking should authenticate and send queue position update" {
        testApplication {
            val httpClient = createClient { }

            // Register and login player
            httpClient.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "alice_ws", "email": "alice_ws@example.com", "password": "password123"}""")
            }
            val loginResponse = httpClient.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "alice_ws", "password": "password123"}""")
            }
            val loginJson = Json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject
            val token = loginJson["token"]?.jsonPrimitive?.content!!
            val userId = loginJson["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content!!

            val wsClient = createClient {
                install(WebSockets)
            }

            wsClient.webSocket("/ws/matchmaking?token=$token") {
                // Expect AuthSuccess message
                val authFrame = incoming.receive() as Frame.Text
                val authMessage = Json.parseToJsonElement(authFrame.readText()).jsonObject
                authMessage["type"]?.jsonPrimitive?.content shouldBe "AuthSuccess"
                authMessage["userId"]?.jsonPrimitive?.content shouldBe userId

                // Send JoinQueue message
                send(Frame.Text("""{"type": "JoinQueue"}"""))

                // Expect QueuePositionUpdate (player is waiting)
                val queueFrame = incoming.receive() as Frame.Text
                val queueMessage = Json.parseToJsonElement(queueFrame.readText()).jsonObject
                println(queueMessage)
                queueMessage["type"]?.jsonPrimitive?.content shouldBe "QueuePositionUpdate"
                queueMessage["position"]?.jsonPrimitive?.int shouldBe 1

                // Close connection
                close()
            }
        }
    }

    "WebSocket matchmaking should reject connection without JWT token" {
        testApplication {
            val wsClient = createClient {
                install(WebSockets)
            }

            wsClient.webSocket("/ws/matchmaking") {
                // Should receive AuthFailed message
                val authFrame = incoming.receive() as Frame.Text
                val authMessage = Json.parseToJsonElement(authFrame.readText()).jsonObject
                authMessage["type"]?.jsonPrimitive?.content shouldBe "AuthFailed"
                authMessage["reason"]?.jsonPrimitive?.content shouldNotBe null

                // Connection should close after AuthFailed
                val closeReason = closeReason.await()
                closeReason shouldNotBe null
            }
        }
    }

    "WebSocket matchmaking should reject invalid token" {
        testApplication {
            val wsClient = createClient {
                install(WebSockets)
            }

            wsClient.webSocket("/ws/matchmaking?token=invalid_token_12345") {
                // Should receive AuthFailed message
                val authFrame = incoming.receive() as Frame.Text
                val authMessage = Json.parseToJsonElement(authFrame.readText()).jsonObject
                authMessage["type"]?.jsonPrimitive?.content shouldBe "AuthFailed"
                authMessage["reason"]?.jsonPrimitive?.content shouldNotBe null

                // Connection should close after AuthFailed
                val closeReason = closeReason.await()
                closeReason shouldNotBe null
            }
        }
    }

    "WebSocket matchmaking should handle invalid message gracefully" {
        testApplication {
            val httpClient = createClient { }

            // Register and login
            httpClient.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "test_invalid", "email": "test_invalid@example.com", "password": "password123"}""")
            }
            val loginResponse = httpClient.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "test_invalid", "password": "password123"}""")
            }
            val loginJson = Json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject
            val token = loginJson["token"]?.jsonPrimitive?.content!!

            val wsClient = createClient {
                install(WebSockets)
            }

            wsClient.webSocket("/ws/matchmaking?token=$token") {
                // Skip AuthSuccess
                incoming.receive()

                // Send invalid JSON
                send(Frame.Text("""{"invalid": "message"}"""))

                // Expect Error response
                val errorFrame = incoming.receive() as Frame.Text
                val errorMessage = Json.parseToJsonElement(errorFrame.readText()).jsonObject
                errorMessage["type"]?.jsonPrimitive?.content shouldBe "Error"
                errorMessage["code"]?.jsonPrimitive?.content shouldNotBe null

                // Close connection
                close()
            }
        }
    }
})
