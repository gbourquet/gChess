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
import com.gchess.module
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/**
 * End-to-end integration tests for game resignation and draw offers.
 *
 * These tests validate the complete flows for:
 * 1. Draw offer acceptance: White offers draw -> Black accepts -> Game ends in DRAW
 * 2. Draw offer rejection: Black offers draw -> White rejects -> Game continues
 * 3. Resignation: White resigns -> Game ends in RESIGNED status
 *
 * Uses Testcontainers PostgreSQL for database integration testing.
 */
class DrawAndResignITest : DatabaseITest({

    "Scenario 1: White offers draw, Black accepts, game ends in DRAW" {
        testApplication {
            application {
                module()
            }
            val httpClient = createClient { }

            // ========== 1. Register both players ==========
            httpClient.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "white_player", "email": "white@example.com", "password": "password123"}""")
            }
            httpClient.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "black_player", "email": "black@example.com", "password": "password123"}""")
            }

            // ========== 2. Login both players ==========
            val loginResponse1 = httpClient.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "white_player", "password": "password123"}""")
            }
            val loginJson1 = Json.parseToJsonElement(loginResponse1.bodyAsText()).jsonObject
            val token1 = loginJson1["token"]?.jsonPrimitive?.content!!

            val loginResponse2 = httpClient.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "black_player", "password": "password123"}""")
            }
            val loginJson2 = Json.parseToJsonElement(loginResponse2.bodyAsText()).jsonObject
            val token2 = loginJson2["token"]?.jsonPrimitive?.content!!

            // ========== 3. Join matchmaking and get game ID ==========
            val wsClient1 = createClient { install(WebSockets) }
            val wsClient2 = createClient { install(WebSockets) }

            var player1GameId: String? = null
            var player2GameId: String? = null
            var player1Color: String? = null
            var player2Color: String? = null

            coroutineScope {
                val matchmaking1 = async {
                    wsClient1.webSocket("/ws/matchmaking?token=$token1") {
                        incoming.receive() // Skip AuthSuccess
                        send(Frame.Text("""{"type": "JoinQueue"}"""))
                        incoming.receive() // QueuePositionUpdate

                        val matchFrame = incoming.receive() as Frame.Text
                        val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                        player1GameId = matchMsg["gameId"]?.jsonPrimitive?.content!!
                        player1Color = matchMsg["yourColor"]?.jsonPrimitive?.content!!
                        close()
                    }
                }

                delay(500)

                val matchmaking2 = async {
                    wsClient2.webSocket("/ws/matchmaking?token=$token2") {
                        incoming.receive() // Skip AuthSuccess
                        send(Frame.Text("""{"type": "JoinQueue"}"""))

                        val matchFrame = incoming.receive() as Frame.Text
                        val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                        player2GameId = matchMsg["gameId"]?.jsonPrimitive?.content!!
                        player2Color = matchMsg["yourColor"]?.jsonPrimitive?.content!!
                        close()
                    }
                }

                matchmaking1.await()
                matchmaking2.await()
            }

            player1GameId shouldBe player2GameId
            val gameId = player1GameId!!
            println("Game created: $gameId")

            // Determine tokens by color
            val (whiteToken, blackToken) = if (player1Color == "WHITE") {
                Pair(token1, token2)
            } else {
                Pair(token2, token1)
            }

            // ========== 4. Play a few moves then white offers draw ==========
            val gameClient1 = createClient { install(WebSockets) }
            val gameClient2 = createClient { install(WebSockets) }

            coroutineScope {
                val whiteSession = async {
                    gameClient1.webSocket("/ws/game/$gameId?token=$whiteToken") {
                        suspend fun receiveMessage(): JsonObject {
                            val frame = incoming.receive() as Frame.Text
                            return Json.parseToJsonElement(frame.readText()).jsonObject
                        }

                        receiveMessage() // Skip AuthSuccess
                        receiveMessage() // Skip GameStateSync

                        // Move 1: e2-e4
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e2", "to": "e4"}"""))
                        receiveMessage() // MoveExecuted
                        receiveMessage() // Wait for black's move

                        // White offers draw after first move
                        println("White offers draw")
                        send(Frame.Text("""{"type": "OfferDraw"}"""))

                        // Wait for DrawAccepted message
                        val acceptMsg = receiveMessage()
                        println("White received: ${acceptMsg["type"]?.jsonPrimitive?.content}")
                        acceptMsg["type"]?.jsonPrimitive?.content shouldBe "DrawAccepted"
                        acceptMsg["gameStatus"]?.jsonPrimitive?.content shouldBe "DRAW"

                        close()
                    }
                }

                val blackSession = async {
                    gameClient2.webSocket("/ws/game/$gameId?token=$blackToken") {
                        suspend fun receiveMessage(): JsonObject {
                            val frame = incoming.receive() as Frame.Text
                            return Json.parseToJsonElement(frame.readText()).jsonObject
                        }

                        receiveMessage() // Skip AuthSuccess
                        receiveMessage() // Skip GameStateSync
                        receiveMessage() // Wait for white's e4

                        // Move 1: e7-e5
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e7", "to": "e5"}"""))
                        receiveMessage() // MoveExecuted

                        // Receive draw offer from white
                        val offerMsg = receiveMessage()
                        println("Black received: ${offerMsg["type"]?.jsonPrimitive?.content}")
                        offerMsg["type"]?.jsonPrimitive?.content shouldBe "DrawOffered"

                        // Black accepts the draw
                        println("Black accepts draw")
                        send(Frame.Text("""{"type": "AcceptDraw"}"""))

                        val acceptMsg = receiveMessage()
                        acceptMsg["type"]?.jsonPrimitive?.content shouldBe "DrawAccepted"
                        acceptMsg["gameStatus"]?.jsonPrimitive?.content shouldBe "DRAW"

                        close()
                    }
                }

                withTimeout(10000) {
                    whiteSession.await()
                    blackSession.await()
                }
            }

            println("Draw offer accepted successfully! Game ended in DRAW.")
        }
    }

    "Scenario 2: Black offers draw, White rejects, game continues" {
        testApplication {
            application {
                module()
            }
            val httpClient = createClient { }

            // ========== 1. Register both players ==========
            httpClient.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "white_player2", "email": "white2@example.com", "password": "password123"}""")
            }
            httpClient.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "black_player2", "email": "black2@example.com", "password": "password123"}""")
            }

            // ========== 2. Login both players ==========
            val loginResponse1 = httpClient.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "white_player2", "password": "password123"}""")
            }
            val loginJson1 = Json.parseToJsonElement(loginResponse1.bodyAsText()).jsonObject
            val token1 = loginJson1["token"]?.jsonPrimitive?.content!!

            val loginResponse2 = httpClient.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "black_player2", "password": "password123"}""")
            }
            val loginJson2 = Json.parseToJsonElement(loginResponse2.bodyAsText()).jsonObject
            val token2 = loginJson2["token"]?.jsonPrimitive?.content!!

            // ========== 3. Join matchmaking and get game ID ==========
            val wsClient1 = createClient { install(WebSockets) }
            val wsClient2 = createClient { install(WebSockets) }

            var player1GameId: String? = null
            var player2GameId: String? = null
            var player1Color: String? = null
            var player2Color: String? = null

            coroutineScope {
                val matchmaking1 = async {
                    wsClient1.webSocket("/ws/matchmaking?token=$token1") {
                        incoming.receive() // Skip AuthSuccess
                        send(Frame.Text("""{"type": "JoinQueue"}"""))
                        incoming.receive() // QueuePositionUpdate

                        val matchFrame = incoming.receive() as Frame.Text
                        val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                        player1GameId = matchMsg["gameId"]?.jsonPrimitive?.content!!
                        player1Color = matchMsg["yourColor"]?.jsonPrimitive?.content!!
                        close()
                    }
                }

                delay(500)

                val matchmaking2 = async {
                    wsClient2.webSocket("/ws/matchmaking?token=$token2") {
                        incoming.receive() // Skip AuthSuccess
                        send(Frame.Text("""{"type": "JoinQueue"}"""))

                        val matchFrame = incoming.receive() as Frame.Text
                        val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                        player2GameId = matchMsg["gameId"]?.jsonPrimitive?.content!!
                        player2Color = matchMsg["yourColor"]?.jsonPrimitive?.content!!
                        close()
                    }
                }

                matchmaking1.await()
                matchmaking2.await()
            }

            player1GameId shouldBe player2GameId
            val gameId = player1GameId!!
            println("Game created: $gameId")

            // Determine tokens by color
            val (whiteToken, blackToken) = if (player1Color == "WHITE") {
                Pair(token1, token2)
            } else {
                Pair(token2, token1)
            }

            // ========== 4. Play moves, black offers draw, white rejects ==========
            val gameClient1 = createClient { install(WebSockets) }
            val gameClient2 = createClient { install(WebSockets) }

            coroutineScope {
                val whiteSession = async {
                    gameClient1.webSocket("/ws/game/$gameId?token=$whiteToken") {
                        suspend fun receiveMessage(): JsonObject {
                            val frame = incoming.receive() as Frame.Text
                            return Json.parseToJsonElement(frame.readText()).jsonObject
                        }

                        receiveMessage() // Skip AuthSuccess
                        receiveMessage() // Skip GameStateSync

                        // Move 1: e2-e4
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e2", "to": "e4"}"""))
                        receiveMessage() // MoveExecuted
                        receiveMessage() // Wait for black's move

                        // Receive draw offer from black
                        val offerMsg = receiveMessage()
                        println("White received: ${offerMsg["type"]?.jsonPrimitive?.content}")
                        offerMsg["type"]?.jsonPrimitive?.content shouldBe "DrawOffered"

                        // White rejects the draw
                        println("White rejects draw")
                        send(Frame.Text("""{"type": "RejectDraw"}"""))

                        // Make another move to prove game continues
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "d2", "to": "d4"}"""))
                        val move2Msg = receiveMessage()
                        move2Msg["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                        move2Msg["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                        close()
                    }
                }

                val blackSession = async {
                    gameClient2.webSocket("/ws/game/$gameId?token=$blackToken") {
                        suspend fun receiveMessage(): JsonObject {
                            val frame = incoming.receive() as Frame.Text
                            return Json.parseToJsonElement(frame.readText()).jsonObject
                        }

                        receiveMessage() // Skip AuthSuccess
                        receiveMessage() // Skip GameStateSync
                        receiveMessage() // Wait for white's e4

                        // Move 1: e7-e5
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e7", "to": "e5"}"""))
                        receiveMessage() // MoveExecuted

                        // Black offers draw
                        println("Black offers draw")
                        send(Frame.Text("""{"type": "OfferDraw"}"""))

                        // Receive DrawRejected message
                        val rejectMsg = receiveMessage()
                        println("Black received: ${rejectMsg["type"]?.jsonPrimitive?.content}")
                        rejectMsg["type"]?.jsonPrimitive?.content shouldBe "DrawRejected"

                        // Receive white's next move (proving game continues)
                        val continueMsg = receiveMessage()
                        continueMsg["type"]?.jsonPrimitive?.content shouldBe "MoveExecuted"
                        continueMsg["gameStatus"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"

                        close()
                    }
                }

                withTimeout(10000) {
                    whiteSession.await()
                    blackSession.await()
                }
            }

            println("Draw offer rejected successfully! Game continues.")
        }
    }

    "Scenario 3: White resigns, game ends in RESIGNED status" {
        testApplication {
            application {
                module()
            }
            val httpClient = createClient { }

            // ========== 1. Register both players ==========
            httpClient.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "white_player3", "email": "white3@example.com", "password": "password123"}""")
            }
            httpClient.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "black_player3", "email": "black3@example.com", "password": "password123"}""")
            }

            // ========== 2. Login both players ==========
            val loginResponse1 = httpClient.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "white_player3", "password": "password123"}""")
            }
            val loginJson1 = Json.parseToJsonElement(loginResponse1.bodyAsText()).jsonObject
            val token1 = loginJson1["token"]?.jsonPrimitive?.content!!

            val loginResponse2 = httpClient.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "black_player3", "password": "password123"}""")
            }
            val loginJson2 = Json.parseToJsonElement(loginResponse2.bodyAsText()).jsonObject
            val token2 = loginJson2["token"]?.jsonPrimitive?.content!!

            // ========== 3. Join matchmaking and get game ID ==========
            val wsClient1 = createClient { install(WebSockets) }
            val wsClient2 = createClient { install(WebSockets) }

            var player1GameId: String? = null
            var player2GameId: String? = null
            var player1Color: String? = null
            var player2Color: String? = null

            coroutineScope {
                val matchmaking1 = async {
                    wsClient1.webSocket("/ws/matchmaking?token=$token1") {
                        incoming.receive() // Skip AuthSuccess
                        send(Frame.Text("""{"type": "JoinQueue"}"""))
                        incoming.receive() // QueuePositionUpdate

                        val matchFrame = incoming.receive() as Frame.Text
                        val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                        player1GameId = matchMsg["gameId"]?.jsonPrimitive?.content!!
                        player1Color = matchMsg["yourColor"]?.jsonPrimitive?.content!!
                        close()
                    }
                }

                delay(500)

                val matchmaking2 = async {
                    wsClient2.webSocket("/ws/matchmaking?token=$token2") {
                        incoming.receive() // Skip AuthSuccess
                        send(Frame.Text("""{"type": "JoinQueue"}"""))

                        val matchFrame = incoming.receive() as Frame.Text
                        val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                        player2GameId = matchMsg["gameId"]?.jsonPrimitive?.content!!
                        player2Color = matchMsg["yourColor"]?.jsonPrimitive?.content!!
                        close()
                    }
                }

                matchmaking1.await()
                matchmaking2.await()
            }

            player1GameId shouldBe player2GameId
            val gameId = player1GameId!!
            println("Game created: $gameId")

            // Determine tokens by color
            val (whiteToken, blackToken) = if (player1Color == "WHITE") {
                Pair(token1, token2)
            } else {
                Pair(token2, token1)
            }

            // ========== 4. Play a move then white resigns ==========
            val gameClient1 = createClient { install(WebSockets) }
            val gameClient2 = createClient { install(WebSockets) }

            coroutineScope {
                val whiteSession = async {
                    gameClient1.webSocket("/ws/game/$gameId?token=$whiteToken") {
                        suspend fun receiveMessage(): JsonObject {
                            val frame = incoming.receive() as Frame.Text
                            return Json.parseToJsonElement(frame.readText()).jsonObject
                        }

                        receiveMessage() // Skip AuthSuccess
                        receiveMessage() // Skip GameStateSync

                        // Move 1: e2-e4
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e2", "to": "e4"}"""))
                        receiveMessage() // MoveExecuted
                        receiveMessage() // Wait for black's move

                        // White resigns
                        println("White resigns")
                        send(Frame.Text("""{"type": "Resign"}"""))

                        // Receive GameResigned message
                        val resignMsg = receiveMessage()
                        println("White received: ${resignMsg["type"]?.jsonPrimitive?.content}")
                        resignMsg["type"]?.jsonPrimitive?.content shouldBe "GameResigned"
                        resignMsg["gameStatus"]?.jsonPrimitive?.content shouldBe "RESIGNED"

                        close()
                    }
                }

                val blackSession = async {
                    gameClient2.webSocket("/ws/game/$gameId?token=$blackToken") {
                        suspend fun receiveMessage(): JsonObject {
                            val frame = incoming.receive() as Frame.Text
                            return Json.parseToJsonElement(frame.readText()).jsonObject
                        }

                        receiveMessage() // Skip AuthSuccess
                        receiveMessage() // Skip GameStateSync
                        receiveMessage() // Wait for white's e4

                        // Move 1: e7-e5
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e7", "to": "e5"}"""))
                        receiveMessage() // MoveExecuted

                        // Receive GameResigned message
                        val resignMsg = receiveMessage()
                        println("Black received: ${resignMsg["type"]?.jsonPrimitive?.content}")
                        resignMsg["type"]?.jsonPrimitive?.content shouldBe "GameResigned"
                        resignMsg["gameStatus"]?.jsonPrimitive?.content shouldBe "RESIGNED"

                        close()
                    }
                }

                withTimeout(10000) {
                    whiteSession.await()
                    blackSession.await()
                }
            }

            println("Resignation successful! Game ended in RESIGNED status.")
        }
    }
})