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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.time.Instant

/**
 * Tests pour le champ `lastMoveAt` dans les messages `GameStateSync`.
 *
 * Ce champ est indispensable pour que le client puisse recalculer le temps
 * écoulé depuis le dernier coup lors d'une reconnexion ou d'un refresh de page.
 */
class GameStateSyncITest : DatabaseITest({

    /**
     * Helper : inscrit deux joueurs, les connecte via matchmaking et retourne leurs tokens
     * ainsi que l'identifiant de partie et les playerId.
     */
    data class GameSetup(
        val token1: String,
        val token2: String,
        val gameId: String,
        val player1Id: String,
        val player2Id: String,
        val player1Color: String,
        val player2Color: String,
    ) {
        val whiteToken get() = if (player1Color == "WHITE") token1 else token2
        val blackToken get() = if (player1Color == "WHITE") token2 else token1
        val whitePlayerId get() = if (player1Color == "WHITE") player1Id else player2Id
    }

    suspend fun ApplicationTestBuilder.setupGame(joinQueueMsg: String): GameSetup {
        val httpClient = createClient {}

        httpClient.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "synctest1", "email": "synctest1@example.com", "password": "password123"}""")
        }
        httpClient.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "synctest2", "email": "synctest2@example.com", "password": "password123"}""")
        }

        val loginResponse1 = httpClient.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "synctest1", "password": "password123"}""")
        }
        val token1 = Json.parseToJsonElement(loginResponse1.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content

        val loginResponse2 = httpClient.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "synctest2", "password": "password123"}""")
        }
        val token2 = Json.parseToJsonElement(loginResponse2.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content

        var gameId = ""
        var player1Id = ""
        var player2Id = ""
        var player1Color = ""
        var player2Color = ""

        val wsClient1 = createClient { install(WebSockets) }
        val wsClient2 = createClient { install(WebSockets) }

        coroutineScope {
            val job1 = async {
                wsClient1.webSocket("/ws/matchmaking?token=$token1") {
                    incoming.receive() // AuthSuccess
                    send(Frame.Text(joinQueueMsg))
                    incoming.receive() // QueuePositionUpdate
                    val matchFrame = incoming.receive() as Frame.Text
                    val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                    gameId = matchMsg["gameId"]!!.jsonPrimitive.content
                    player1Id = matchMsg["playerId"]!!.jsonPrimitive.content
                    player1Color = matchMsg["yourColor"]!!.jsonPrimitive.content
                    close()
                }
            }
            delay(300)
            val job2 = async {
                wsClient2.webSocket("/ws/matchmaking?token=$token2") {
                    incoming.receive() // AuthSuccess
                    send(Frame.Text(joinQueueMsg))
                    val matchFrame = incoming.receive() as Frame.Text
                    val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                    player2Id = matchMsg["playerId"]!!.jsonPrimitive.content
                    player2Color = matchMsg["yourColor"]!!.jsonPrimitive.content
                    close()
                }
            }
            job1.await()
            job2.await()
        }

        return GameSetup(token1, token2, gameId, player1Id, player2Id, player1Color, player2Color)
    }

    "GameStateSync d'une partie sans contrôle du temps ne contient pas lastMoveAt" {
        testApplication {
            application { module() }

            val setup = setupGame("""{"type": "JoinQueue"}""")

            val gameClient = createClient { install(WebSockets) }
            gameClient.webSocket("/ws/game/${setup.gameId}?token=${setup.whiteToken}") {
                suspend fun receiveMessage() =
                    Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject

                receiveMessage() // AuthSuccess
                val syncMsg = receiveMessage()

                syncMsg["type"]!!.jsonPrimitive.content shouldBe "GameStateSync"
                // Partie sans contrôle de temps : lastMoveAt absent ou null
                val lastMoveAt = syncMsg["lastMoveAt"]
                if (lastMoveAt != null) lastMoveAt shouldBe JsonNull

                close()
            }
        }
    }

    "GameStateSync d'une partie Fischer contient un lastMoveAt non-null dès la création" {
        testApplication {
            application { module() }

            val setup = setupGame("""{"type": "JoinQueue", "totalTimeMinutes": 10, "incrementSeconds": 5}""")

            val gameClient = createClient { install(WebSockets) }
            gameClient.webSocket("/ws/game/${setup.gameId}?token=${setup.whiteToken}") {
                suspend fun receiveMessage() =
                    Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject

                receiveMessage() // AuthSuccess
                val syncMsg = receiveMessage()

                syncMsg["type"]!!.jsonPrimitive.content shouldBe "GameStateSync"
                val lastMoveAtStr = syncMsg["lastMoveAt"]?.jsonPrimitive?.contentOrNull
                lastMoveAtStr.shouldNotBeNull()
                // Doit être parsable en Instant ISO-8601
                Instant.parse(lastMoveAtStr)

                close()
            }
        }
    }

    "GameStateSync sur reconnexion Fischer après des coups contient un lastMoveAt mis à jour" {
        testApplication {
            application { module() }

            val setup = setupGame("""{"type": "JoinQueue", "totalTimeMinutes": 10, "incrementSeconds": 5}""")

            val gameClientWhite = createClient { install(WebSockets) }
            val gameClientBlack = createClient { install(WebSockets) }

            // Jouer 2 coups (1.e4 e5) pour démarrer l'horloge
            coroutineScope {
                val whiteSession = async {
                    gameClientWhite.webSocket("/ws/game/${setup.gameId}?token=${setup.whiteToken}") {
                        suspend fun receiveMessage() =
                            Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject

                        receiveMessage() // AuthSuccess
                        receiveMessage() // GameStateSync

                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e2", "to": "e4"}"""))
                        receiveMessage() // MoveExecuted (e4)
                        receiveMessage() // MoveExecuted (e5 de noir)

                        close()
                    }
                }
                val blackSession = async {
                    gameClientBlack.webSocket("/ws/game/${setup.gameId}?token=${setup.blackToken}") {
                        suspend fun receiveMessage() =
                            Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject

                        receiveMessage() // AuthSuccess
                        receiveMessage() // GameStateSync
                        receiveMessage() // MoveExecuted (e4 de blanc)

                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e7", "to": "e5"}"""))
                        receiveMessage() // MoveExecuted (e5)

                        close()
                    }
                }
                whiteSession.await()
                blackSession.await()
            }

            // Reconnexion de blanc : nouveau GameStateSync doit contenir lastMoveAt valide
            val reconnectClient = createClient { install(WebSockets) }
            reconnectClient.webSocket("/ws/game/${setup.gameId}?token=${setup.whiteToken}") {
                suspend fun receiveMessage() =
                    Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject

                receiveMessage() // AuthSuccess
                val syncMsg = receiveMessage()

                syncMsg["type"]!!.jsonPrimitive.content shouldBe "GameStateSync"

                // lastMoveAt doit être présent et parsable
                val lastMoveAtStr = syncMsg["lastMoveAt"]?.jsonPrimitive?.contentOrNull
                lastMoveAtStr.shouldNotBeNull()
                val lastMoveAt = Instant.parse(lastMoveAtStr)

                // lastMoveAt doit être récent (dans les 10 dernières secondes)
                val elapsedMs = Instant.now().toEpochMilli() - lastMoveAt.toEpochMilli()
                (elapsedMs < 10_000L) shouldBe true

                // Les deux horloges doivent être présentes pour une partie Fischer
                syncMsg["whiteTimeRemainingMs"]?.jsonPrimitive?.longOrNull.shouldNotBeNull()
                syncMsg["blackTimeRemainingMs"]?.jsonPrimitive?.longOrNull.shouldNotBeNull()

                close()
            }
        }
    }
})
