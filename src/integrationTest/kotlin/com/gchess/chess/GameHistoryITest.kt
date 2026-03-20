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
import io.kotest.matchers.collections.shouldHaveSize
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
 * Tests d'intégration pour l'historique des parties.
 *
 * Couvre :
 * 1. Récupération de la liste des parties d'un joueur (GET /api/history/games)
 * 2. Liste vide si le joueur n'a pas encore de parties
 * 3. Authentification requise (401 sans token)
 * 4. Récupération des coups d'une partie (GET /api/history/games/{id}/moves)
 * 5. Accès interdit aux non-participants (403)
 * 6. Partie inconnue (404)
 */
class GameHistoryITest : DatabaseITest({

    /**
     * Helpers réutilisables pour setup de partie.
     */
    suspend fun ApplicationTestBuilder.registerAndLogin(
        httpClient: io.ktor.client.HttpClient,
        username: String,
        email: String,
        password: String = "password123"
    ): String {
        httpClient.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$username", "email": "$email", "password": "$password"}""")
        }
        val loginResponse = httpClient.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$username", "password": "$password"}""")
        }
        return Json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content!!
    }

    suspend fun ApplicationTestBuilder.createGameViaMatchmaking(
        token1: String,
        token2: String
    ): Triple<String, String, String> {
        val wsClient1 = createClient { install(WebSockets) }
        val wsClient2 = createClient { install(WebSockets) }

        var player1GameId: String? = null
        var player1Color: String? = null
        var player2Color: String? = null

        coroutineScope {
            val job1 = async {
                wsClient1.webSocket("/ws/matchmaking?token=$token1") {
                    incoming.receive() // AuthSuccess
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

            val job2 = async {
                wsClient2.webSocket("/ws/matchmaking?token=$token2") {
                    incoming.receive() // AuthSuccess
                    send(Frame.Text("""{"type": "JoinQueue"}"""))

                    val matchFrame = incoming.receive() as Frame.Text
                    val matchMsg = Json.parseToJsonElement(matchFrame.readText()).jsonObject
                    player2Color = matchMsg["yourColor"]?.jsonPrimitive?.content!!
                    close()
                }
            }

            job1.await()
            job2.await()
        }

        val gameId = player1GameId!!
        val whiteToken = if (player1Color == "WHITE") token1 else token2
        val blackToken = if (player1Color == "WHITE") token2 else token1
        return Triple(gameId, whiteToken, blackToken)
    }

    "GET /api/history/games retourne 401 sans token" {
        testApplication {
            application { module() }
            val httpClient = createClient { }

            val response = httpClient.get("/api/history/games")

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "GET /api/history/games retourne une liste vide si le joueur n'a pas de parties" {
        testApplication {
            application { module() }
            val httpClient = createClient { }

            val token = registerAndLogin(httpClient, "joueur_solo", "solo@example.com")

            val response = httpClient.get("/api/history/games") {
                bearerAuth(token)
            }

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body shouldHaveSize 0
        }
    }

    "GET /api/history/games retourne les parties du joueur authentifié" {
        testApplication {
            application { module() }
            val httpClient = createClient { }

            val token1 = registerAndLogin(httpClient, "history_white", "hwhite@example.com")
            val token2 = registerAndLogin(httpClient, "history_black", "hblack@example.com")

            // Obtenir l'userId de chaque joueur via login response
            val login1 = Json.parseToJsonElement(
                httpClient.post("/api/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username": "history_white", "password": "password123"}""")
                }.bodyAsText()
            ).jsonObject
            val user1Id = login1["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content!!

            val (gameId, _, _) = createGameViaMatchmaking(token1, token2)

            // Joueur 1 consulte son historique
            val response = httpClient.get("/api/history/games") {
                bearerAuth(token1)
            }

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body shouldHaveSize 1

            val gameSummary = body[0].jsonObject
            gameSummary["gameId"]?.jsonPrimitive?.content shouldBe gameId
            gameSummary["status"]?.jsonPrimitive?.content shouldNotBe null
            gameSummary["moveCount"]?.jsonPrimitive?.int shouldBe 0

            // Vérifier que les deux userId sont bien présents
            val whiteUserId = gameSummary["whiteUserId"]?.jsonPrimitive?.content
            val blackUserId = gameSummary["blackUserId"]?.jsonPrimitive?.content
            // L'un des deux doit être user1Id
            (whiteUserId == user1Id || blackUserId == user1Id) shouldBe true
        }
    }

    "GET /api/history/games ne retourne que les parties du joueur authentifié" {
        testApplication {
            application { module() }
            val httpClient = createClient { }

            val token1 = registerAndLogin(httpClient, "isolation_p1", "iso1@example.com")
            val token2 = registerAndLogin(httpClient, "isolation_p2", "iso2@example.com")
            val token3 = registerAndLogin(httpClient, "isolation_p3", "iso3@example.com")

            // Créer une partie entre joueur 1 et 2
            createGameViaMatchmaking(token1, token2)

            // Joueur 3 n'a pas de parties
            val response = httpClient.get("/api/history/games") {
                bearerAuth(token3)
            }

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body shouldHaveSize 0
        }
    }

    "GET /api/history/games/{gameId}/moves retourne 401 sans token" {
        testApplication {
            application { module() }
            val httpClient = createClient { }

            val response = httpClient.get("/api/history/games/someid/moves")

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "GET /api/history/games/{gameId}/moves retourne 404 pour une partie inconnue" {
        testApplication {
            application { module() }
            val httpClient = createClient { }

            val token = registerAndLogin(httpClient, "unknown_game_user", "unk@example.com")

            val response = httpClient.get("/api/history/games/01JXXXXXXXXXXXXXXXXXXXXXXXXX/moves") {
                bearerAuth(token)
            }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    "GET /api/history/games/{gameId}/moves retourne 403 pour un non-participant" {
        testApplication {
            application { module() }
            val httpClient = createClient { }

            val token1 = registerAndLogin(httpClient, "moves_p1", "moves1@example.com")
            val token2 = registerAndLogin(httpClient, "moves_p2", "moves2@example.com")
            val token3 = registerAndLogin(httpClient, "moves_p3", "moves3@example.com")

            val (gameId, _, _) = createGameViaMatchmaking(token1, token2)

            // Joueur 3 tente d'accéder aux coups → 403
            val response = httpClient.get("/api/history/games/$gameId/moves") {
                bearerAuth(token3)
            }

            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    "GET /api/history/games/{gameId}/moves retourne les coups de la partie pour un participant" {
        testApplication {
            application { module() }
            val httpClient = createClient { }

            val token1 = registerAndLogin(httpClient, "moves_white", "mwhite@example.com")
            val token2 = registerAndLogin(httpClient, "moves_black", "mblack@example.com")

            val (gameId, whiteToken, blackToken) = createGameViaMatchmaking(token1, token2)

            // Jouer 2 coups via WebSocket
            val gameClient1 = createClient { install(WebSockets) }
            val gameClient2 = createClient { install(WebSockets) }

            coroutineScope {
                val whiteSession = async {
                    gameClient1.webSocket("/ws/game/$gameId?token=$whiteToken") {
                        suspend fun receiveMessage(): JsonObject {
                            val frame = incoming.receive() as Frame.Text
                            return Json.parseToJsonElement(frame.readText()).jsonObject
                        }
                        receiveMessage() // AuthSuccess
                        receiveMessage() // GameStateSync
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e2", "to": "e4"}"""))
                        receiveMessage() // MoveExecuted
                        receiveMessage() // Wait for black's move
                        close()
                    }
                }
                val blackSession = async {
                    gameClient2.webSocket("/ws/game/$gameId?token=$blackToken") {
                        suspend fun receiveMessage(): JsonObject {
                            val frame = incoming.receive() as Frame.Text
                            return Json.parseToJsonElement(frame.readText()).jsonObject
                        }
                        receiveMessage() // AuthSuccess
                        receiveMessage() // GameStateSync
                        receiveMessage() // Wait for white's e4

                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e7", "to": "e5"}"""))
                        receiveMessage() // MoveExecuted
                        close()
                    }
                }
                withTimeout(10000) {
                    whiteSession.await()
                    blackSession.await()
                }
            }

            // Joueur blanc consulte les coups
            val response = httpClient.get("/api/history/games/$gameId/moves") {
                bearerAuth(whiteToken)
            }

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body shouldHaveSize 2

            val move1 = body[0].jsonObject
            move1["moveNumber"]?.jsonPrimitive?.int shouldBe 0
            move1["from"]?.jsonPrimitive?.content shouldBe "e2"
            move1["to"]?.jsonPrimitive?.content shouldBe "e4"
            move1["promotion"]?.jsonPrimitive?.contentOrNull shouldBe null

            val move2 = body[1].jsonObject
            move2["moveNumber"]?.jsonPrimitive?.int shouldBe 1
            move2["from"]?.jsonPrimitive?.content shouldBe "e7"
            move2["to"]?.jsonPrimitive?.content shouldBe "e5"

            // Joueur noir peut aussi consulter les coups
            val responseBlack = httpClient.get("/api/history/games/$gameId/moves") {
                bearerAuth(blackToken)
            }
            responseBlack.status shouldBe HttpStatusCode.OK
            val bodyBlack = Json.parseToJsonElement(responseBlack.bodyAsText()).jsonArray
            bodyBlack shouldHaveSize 2
        }
    }

    "GET /api/history/games retourne le nombre de coups correct après une partie jouée" {
        testApplication {
            application { module() }
            val httpClient = createClient { }

            val token1 = registerAndLogin(httpClient, "count_white", "cwhite@example.com")
            val token2 = registerAndLogin(httpClient, "count_black", "cblack@example.com")

            val (gameId, whiteToken, blackToken) = createGameViaMatchmaking(token1, token2)

            // Jouer 2 coups
            val gameClient1 = createClient { install(WebSockets) }
            val gameClient2 = createClient { install(WebSockets) }

            coroutineScope {
                val whiteSession = async {
                    gameClient1.webSocket("/ws/game/$gameId?token=$whiteToken") {
                        suspend fun receiveMessage(): JsonObject {
                            val frame = incoming.receive() as Frame.Text
                            return Json.parseToJsonElement(frame.readText()).jsonObject
                        }
                        receiveMessage() // AuthSuccess
                        receiveMessage() // GameStateSync
                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e2", "to": "e4"}"""))
                        receiveMessage() // MoveExecuted
                        receiveMessage() // black's move
                        close()
                    }
                }
                val blackSession = async {
                    gameClient2.webSocket("/ws/game/$gameId?token=$blackToken") {
                        suspend fun receiveMessage(): JsonObject {
                            val frame = incoming.receive() as Frame.Text
                            return Json.parseToJsonElement(frame.readText()).jsonObject
                        }
                        receiveMessage() // AuthSuccess
                        receiveMessage() // GameStateSync
                        receiveMessage() // white's move

                        send(Frame.Text("""{"type": "MoveAttempt", "from": "e7", "to": "e5"}"""))
                        receiveMessage() // MoveExecuted
                        close()
                    }
                }
                withTimeout(10000) {
                    whiteSession.await()
                    blackSession.await()
                }
            }

            // Vérifier le moveCount dans le résumé
            val response = httpClient.get("/api/history/games") {
                bearerAuth(token1)
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body shouldHaveSize 1
            body[0].jsonObject["moveCount"]?.jsonPrimitive?.int shouldBe 2
        }
    }
})
