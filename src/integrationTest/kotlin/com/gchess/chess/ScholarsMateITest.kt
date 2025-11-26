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
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.*

/**
 * End-to-end integration test for a complete chess game with Scholar's Mate.
 *
 * This test validates the complete flow:
 * 1. User registration for 2 players
 * 2. User login for both players
 * 3. Matchmaking via WebSocket
 * 4. Game creation and player connection via WebSocket
 * 5. Complete game play with Scholar's Mate (checkmate in 4 moves)
 *
 * Scholar's Mate sequence:
 * 1. e4 e5
 * 2. Bc4 Nc6
 * 3. Qh5 Nf6
 * 4. Qxf7# (checkmate)
 *
 * Uses Testcontainers PostgreSQL for database integration testing.
 */
class ScholarsMateITest : DatabaseITest({

    "Complete Scholar's Mate game from registration to checkmate" {
        testApplication {
            val httpClient = createClient { }

            // ========== 1. Register both players ==========
            httpClient.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "player1", "email": "player1@example.com", "password": "password123"}""")
            }
            httpClient.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "player2", "email": "player2@example.com", "password": "password123"}""")
            }

            // ========== 2. Login both players ==========
            val loginResponse1 = httpClient.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "player1", "password": "password123"}""")
            }
            val loginJson1 = Json.parseToJsonElement(loginResponse1.bodyAsText()).jsonObject
            val token1 = loginJson1["token"]?.jsonPrimitive?.content!!
            val userId1 = loginJson1["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content!!

            val loginResponse2 = httpClient.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "player2", "password": "password123"}""")
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

            println("Starting matchmaking coroutines...")

            // Use coroutineScope to create a proper scope for concurrent operations
            coroutineScope {
                // Player 1 joins matchmaking
                val matchmaking1 = async {
                    println("matchmaking 1 started")
                    wsClient1.webSocket("/ws/matchmaking?token=$token1") {
                        println("Player1 connected to matchmaking")
                        // Skip AuthSuccess
                        incoming.receive()
                        println("Matchmaking auth player 1 received")

                        // Send JoinQueue
                        send(Frame.Text("""{"type": "JoinQueue"}"""))
                        println("Matchmaking call player 1 sent")

                        // Expect QueuePositionUpdate (waiting)
                        val queueFrame = incoming.receive() as Frame.Text
                        val queueMsg = Json.parseToJsonElement(queueFrame.readText()).jsonObject
                        queueMsg["type"]?.jsonPrimitive?.content shouldBe "QueuePositionUpdate"
                        println("Matchmaking acknowledge player 1 received")

                        // Wait for MatchFound
                        println("Player1 waiting for match")
                        val matchFrame = incoming.receive() as Frame.Text
                        println("Player1 match received")

                        val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                        matchMsg["type"]?.jsonPrimitive?.content shouldBe "MatchFound"
                        player1GameId = matchMsg["gameId"]?.jsonPrimitive?.content!!
                        player1Color = matchMsg["yourColor"]?.jsonPrimitive?.content!!

                        close()
                    }
                }

                // Give player 1 a moment to join the queue
                delay(500)

                // Player 2 joins matchmaking
                val matchmaking2 = async {
                    println("matchmaking 2 started")
                    wsClient2.webSocket("/ws/matchmaking?token=$token2") {
                        println("Player2 connected to matchmaking")
                        // Skip AuthSuccess
                        incoming.receive()

                        // Send JoinQueue
                        send(Frame.Text("""{"type": "JoinQueue"}"""))
                        println("Player2 sent JoinQueue")

                        // Wait for MatchFound (should be immediate)
                        val matchFrame = incoming.receive() as Frame.Text
                        val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                        matchMsg["type"]?.jsonPrimitive?.content shouldBe "MatchFound"
                        player2GameId = matchMsg["gameId"]?.jsonPrimitive?.content!!
                        player2Color = matchMsg["yourColor"]?.jsonPrimitive?.content!!
                        println("Player2 match received")

                        close()
                    }
                }

                // Wait for both matchmaking sessions to complete
                println("Waiting for matchmaking completion...")
                matchmaking1.await()
                matchmaking2.await()
            }

            player1GameId shouldBe player2GameId // Ensure both got the same game ID
            val gameId = player1GameId
            println("Game created: $gameId")
            println("Player1 ($userId1) is $player1Color")
            println("Player2 ($userId2) is $player2Color")

            // Determine which player is white and which is black
            val (whiteToken, blackToken) = if (player1Color == "WHITE") {
                Pair(token1, token2)
            } else {
                Pair(token2, token1)
            }

            // ========== 4. Connect to game and play Scholar's Mate ==========
            val gameClient1 = createClient { install(WebSockets) }
            val gameClient2 = createClient { install(WebSockets) }

            println("Starting game sessions...")

            coroutineScope {
                val whiteSession = async {
                    println("White session starting...")
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

                    // Move 1: e2-e4
                    println("White plays e2-e4")
                    send(Frame.Text("""{"type": "MoveAttempt", "from": "e2", "to": "e4"}"""))
                    val move1 = receiveMessage()
                    move1["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                    move1["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                    // Wait for black's move (e7-e5)
                    val blackMove1 = receiveMessage()
                    blackMove1["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                    // Move 2: Bf1-c4
                    println("White plays Bc4")
                    send(Frame.Text("""{"type": "MoveAttempt", "from": "f1", "to": "c4"}"""))
                    val move2 = receiveMessage()
                    move2["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                    // Wait for black's move (Nb8-c6)
                    val blackMove2 = receiveMessage()
                    blackMove2["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                    // Move 3: Qd1-h5
                    println("White plays Qh5")
                    send(Frame.Text("""{"type": "MoveAttempt", "from": "d1", "to": "h5"}"""))
                    val move3 = receiveMessage()
                    move3["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                    move3["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                    // Wait for black's move (Ng8-f6)
                    val blackMove3 = receiveMessage()
                    blackMove3["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                    // Move 4: Qh5xf7# (checkmate!)
                    println("White plays Qxf7# - CHECKMATE!")
                    send(Frame.Text("""{"type": "MoveAttempt", "from": "h5", "to": "f7"}"""))
                    val move4 = receiveMessage()
                    move4["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                    move4["gameStatus"]?.jsonPrimitive?.content shouldBe "CHECKMATE"

                        close()
                    }
                }

                val blackSession = async {
                    println("Black session starting...")
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

                    // Wait for white's move (e2-e4)
                    val whiteMove1 = receiveMessage()
                    whiteMove1["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                    // Move 1: e7-e5
                    println("Black plays e7-e5")
                    send(Frame.Text("""{"type": "MoveAttempt", "from": "e7", "to": "e5"}"""))
                    val move1 = receiveMessage()
                    move1["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                    // Wait for white's move (Bf1-c4)
                    val whiteMove2 = receiveMessage()
                    whiteMove2["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                    // Move 2: Nb8-c6
                    println("Black plays Nc6")
                    send(Frame.Text("""{"type": "MoveAttempt", "from": "b8", "to": "c6"}"""))
                    val move2 = receiveMessage()
                    move2["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                    // Wait for white's move (Qd1-h5)
                    val whiteMove3 = receiveMessage()
                    whiteMove3["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                    whiteMove3["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                    // Move 3: Ng8-f6 (trying to defend)
                    println("Black plays Nf6")
                    send(Frame.Text("""{"type": "MoveAttempt", "from": "g8", "to": "f6"}"""))
                    val move3 = receiveMessage()
                    move3["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"

                    // Wait for white's checkmate move (Qh5xf7#)
                    val whiteMove4 = receiveMessage()
                    whiteMove4["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                    whiteMove4["gameStatus"]?.jsonPrimitive?.content shouldBe "CHECKMATE"

                        close()
                    }
                }

                // Wait for both game sessions to complete
                println("Waiting for game sessions to complete...")
                withTimeout(10000) {
                    whiteSession.await()
                    blackSession.await()
                }
            }

            println("Scholar's Mate completed successfully! White wins by checkmate.")
        }
    }
})
