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
package com.gchess.chess

import com.gchess.infrastructure.DatabaseITest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/**
 * End-to-end integration test for a complete chess game with a pawn promotion.
 *
 * This test validates the complete flow:
 * 1. User registration for 2 players
 * 2. User login for both players
 * 3. Matchmaking via WebSocket
 * 4. Game creation and player connection via WebSocket
 * 5. Game play with pawn promotion
 *
 * Uses Testcontainers PostgreSQL for database integration testing.
 */
class PawnPromotionITest : DatabaseITest({

    "game from registration to pawn promotion" {
        testApplication {
            val httpClient = createClient { }

            // ========== 1. Register both players ==========
            httpClient.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "player3", "email": "player3@example.com", "password": "password123"}""")
            }
            httpClient.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "player4", "email": "player4@example.com", "password": "password123"}""")
            }

            // ========== 2. Login both players ==========
            val loginResponse1 = httpClient.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "player3", "password": "password123"}""")
            }
            val loginJson1 = Json.parseToJsonElement(loginResponse1.bodyAsText()).jsonObject
            val token1 = loginJson1["token"]?.jsonPrimitive?.content!!
            val userId1 = loginJson1["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content!!

            val loginResponse2 = httpClient.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "player4", "password": "password123"}""")
            }
            val loginJson2 = Json.parseToJsonElement(loginResponse2.bodyAsText()).jsonObject
            val token2 = loginJson2["token"]?.jsonPrimitive?.content!!
            val userId2 = loginJson2["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content!!

            // ========== 3. Join matchmaking and get game ID ==========
            val wsClient1 = createClient { install(WebSockets) }
            val wsClient2 = createClient { install(WebSockets) }

            var player1GameId: String? = null
            var player2GameId: String? = null
            var player1Color: String? = null
            var player2Color: String? = null
            var player1Id: String? = null
            var player2Id: String? = null

            // Use coroutineScope to create a proper scope for concurrent operations
            coroutineScope {
                // Player 1 joins matchmaking
                val matchmaking1 = async {
                    wsClient1.webSocket("/ws/matchmaking?token=$token1") {
                        // Skip AuthSuccess
                        incoming.receive()

                        // Send JoinQueue
                        send(Frame.Text("""{"type": "JoinQueue"}"""))

                        // Expect QueuePositionUpdate (waiting)
                        val queueFrame = incoming.receive() as Frame.Text
                        val queueMsg = Json.parseToJsonElement(queueFrame.readText()).jsonObject
                        queueMsg["type"]?.jsonPrimitive?.content shouldBe "QueuePositionUpdate"

                        // Wait for MatchFound
                        val matchFrame = incoming.receive() as Frame.Text

                        val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                        matchMsg["type"]?.jsonPrimitive?.content shouldBe "MatchFound"
                        player1GameId = matchMsg["gameId"]?.jsonPrimitive?.content!!
                        player1Id = matchMsg["playerId"]?.jsonPrimitive?.content!!
                        player1Color = matchMsg["yourColor"]?.jsonPrimitive?.content!!

                        close()
                    }
                }

                // Give player 1 a moment to join the queue
                delay(500)

                // Player 2 joins matchmaking
                val matchmaking2 = async {
                    wsClient2.webSocket("/ws/matchmaking?token=$token2") {
                        // Skip AuthSuccess
                        incoming.receive()

                        // Send JoinQueue
                        send(Frame.Text("""{"type": "JoinQueue"}"""))

                        // Wait for MatchFound (should be immediate)
                        val matchFrame = incoming.receive() as Frame.Text
                        val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                        matchMsg["type"]?.jsonPrimitive?.content shouldBe "MatchFound"
                        player2GameId = matchMsg["gameId"]?.jsonPrimitive?.content!!
                        player2Color = matchMsg["yourColor"]?.jsonPrimitive?.content!!
                        player2Id = matchMsg["playerId"]?.jsonPrimitive?.content!!

                        close()
                    }
                }

                // Wait for both matchmaking sessions to complete
                matchmaking1.await()
                matchmaking2.await()
            }

            player1GameId shouldBe player2GameId // Ensure both got the same game ID
            player1Id shouldNotBe ""
            player2Id shouldNotBe ""
            val gameId = player1GameId

            // Determine which player is white and which is black
            val (whiteToken, blackToken) = if (player1Color == "WHITE") {
                Pair(token1, token2)
            } else {
                Pair(token2, token1)
            }

            // ========== 4. Connect to game and play Scholar's Mate ==========
            val gameClient1 = createClient { install(WebSockets) }
            val gameClient2 = createClient { install(WebSockets) }

            coroutineScope {
                val whiteSession = async {
                    gameClient1.webSocket("/ws/game/$gameId?token=$whiteToken") {
                        // Helper function to receive and parse next message
                        suspend fun receiveMessage(): JsonObject {
                            val frame = incoming.receive() as Frame.Text
                            return Json.parseToJsonElement(frame.readText()).jsonObject
                        }

                        // Skip AuthSuccess
                        receiveMessage()

                        // Receive GameStateSync
                        val syncMsg = receiveMessage()
                        syncMsg["type"]?.jsonPrimitive?.content shouldBe "GameStateSync"

                        // Move 1: d2-d4
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "d2", "to": "d4"}"""))
                        val move1 = receiveMessage()
                        move1["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                        move1["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                        // Wait for black's move
                        val blackMove1 = receiveMessage()
                        blackMove1["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Move 2: c2-c4
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "c2", "to": "c4"}"""))
                        val move2 = receiveMessage()
                        move2["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Wait for black's move
                        val blackMove2 = receiveMessage()
                        blackMove2["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Move 3: c4-d5
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "c4", "to": "d5"}"""))
                        val move3 = receiveMessage()
                        move3["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                        move3["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                        // Wait for black's move
                        val blackMove3 = receiveMessage()
                        blackMove3["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Move 4: d5-d6
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "d5", "to": "d6"}"""))
                        val move4 = receiveMessage()
                        move4["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                        move4["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                        // Wait for black's move
                        val blackMove4 = receiveMessage()
                        blackMove4["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Move 5: d6-e7
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "d6", "to": "e7"}"""))
                        val move5 = receiveMessage()
                        move5["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                        move5["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                        // Wait for black's move
                        val blackMove5 = receiveMessage()
                        blackMove5["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Move 6: d6-e7
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e7", "to": "d8", "promotion": "QUEEN"}"""))
                        val move6 = receiveMessage()
                        move6["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                        move6["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                        close()
                    }
                }

                val blackSession = async {
                    gameClient2.webSocket("/ws/game/$gameId?token=$blackToken") {
                        // Helper function to receive and parse next message
                        suspend fun receiveMessage(): JsonObject {
                            val frame = incoming.receive() as Frame.Text
                            return Json.parseToJsonElement(frame.readText()).jsonObject
                        }

                        // Skip AuthSuccess
                        receiveMessage()

                        // Receive GameStateSync
                        receiveMessage()

                        // Wait for white's move
                        val whiteMove1 = receiveMessage()
                        whiteMove1["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Move 1: d7-d5
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "d7", "to": "d5"}"""))
                        val move1 = receiveMessage()
                        move1["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Wait for white's move
                        val whiteMove2 = receiveMessage()
                        whiteMove2["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Move 2: c7-c6
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "c7", "to": "c6"}"""))
                        val move2 = receiveMessage()
                        move2["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Wait for white's move
                        val whiteMove3 = receiveMessage()
                        whiteMove3["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                        whiteMove3["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                        // Move 3: c6-c5
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "c6", "to": "c5"}"""))
                        val move3 = receiveMessage()
                        move3["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Wait for white's move
                        val whiteMove4 = receiveMessage()
                        whiteMove4["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                        whiteMove4["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                        // Move 4: c5-d4
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "c5", "to": "d4"}"""))
                        val move4 = receiveMessage()
                        move4["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Wait for white's move
                        val whiteMove5 = receiveMessage()
                        whiteMove5["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                        whiteMove5["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                        // Move 5: d4-d3
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "d4", "to": "d3"}"""))
                        val move5 = receiveMessage()
                        move5["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                        // Wait for last white's move
                        val whiteMove6 = receiveMessage()
                        whiteMove6["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                        whiteMove6["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                        close()
                    }
                }

                // Wait for both game sessions to complete
                withTimeout(10000) {
                    whiteSession.await()
                    blackSession.await()
                }
            }
        }
    }
})
