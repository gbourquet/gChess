package com.gchess.chess.integration

import com.gchess.infrastructure.DatabaseITest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*

/**
 * End-to-end integration test for the game API.
 * Tests the complete flow from user registration to game creation and moves via HTTP endpoints.
 *
 * This test validates the Anti-Corruption Layer (ACL) by ensuring that:
 * - Users must exist before creating a game
 * - Only the correct player can make moves on their turn
 *
 * Uses Testcontainers PostgreSQL for database integration testing.
 */
class GameITest : DatabaseITest({

    "complete game flow: register users, create game, make valid moves, and reject invalid turn" {
        testApplication {
            // module() is automatically called from application.conf
            // (see ktor.application.modules in application.conf)

            val client = createClient {
                // No additional configuration needed, JSON is handled by ContentNegotiation
            }

            // Step 1: Register white player
            val registerWhiteResponse = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "alice", "email": "alice@example.com", "password": "password123"}""")
            }
            registerWhiteResponse.status shouldBe HttpStatusCode.Created

            val whiteUserJson = Json.parseToJsonElement(registerWhiteResponse.bodyAsText()).jsonObject
            val whitePlayerId = whiteUserJson["id"]?.jsonPrimitive?.content
                ?: error("White player ID not found in response")

            // Step 2: Register black player
            val registerBlackResponse = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "bob", "email": "bob@example.com", "password": "password456"}""")
            }
            registerBlackResponse.status shouldBe HttpStatusCode.Created

            val blackUserJson = Json.parseToJsonElement(registerBlackResponse.bodyAsText()).jsonObject
            val blackPlayerId = blackUserJson["id"]?.jsonPrimitive?.content
                ?: error("Black player ID not found in response")

            // Step 2.5: Login white player to get JWT token
            val loginWhiteResponse = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "alice", "password": "password123"}""")
            }
            loginWhiteResponse.status shouldBe HttpStatusCode.OK

            val whiteLoginJson = Json.parseToJsonElement(loginWhiteResponse.bodyAsText()).jsonObject
            val whiteToken = whiteLoginJson["token"]?.jsonPrimitive?.content
                ?: error("White player token not found in login response")

            // Step 2.6: Login black player to get JWT token
            val loginBlackResponse = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "bob", "password": "password456"}""")
            }
            loginBlackResponse.status shouldBe HttpStatusCode.OK

            val blackLoginJson = Json.parseToJsonElement(loginBlackResponse.bodyAsText()).jsonObject
            val blackToken = blackLoginJson["token"]?.jsonPrimitive?.content
                ?: error("Black player token not found in login response")

            // Step 3: Create a game via matchmaking
            // Player 1 joins matchmaking queue
            val join1Response = client.post("/api/matchmaking/queue") {
                header("Authorization", "Bearer $whiteToken")
            }
            join1Response.status shouldBe HttpStatusCode.OK
            val join1Json = Json.parseToJsonElement(join1Response.bodyAsText()).jsonObject
            join1Json["status"]?.jsonPrimitive?.content shouldBe "WAITING"

            // Player 2 joins matchmaking queue - match should be created automatically
            val join2Response = client.post("/api/matchmaking/queue") {
                header("Authorization", "Bearer $blackToken")
            }
            join2Response.status shouldBe HttpStatusCode.OK
            val join2Json = Json.parseToJsonElement(join2Response.bodyAsText()).jsonObject
            join2Json["status"]?.jsonPrimitive?.content shouldBe "MATCHED"

            val gameId = join2Json["gameId"]?.jsonPrimitive?.content
                ?: error("Game ID not found in matchmaking response")

            // Determine which token corresponds to which color
            val player2Color = join2Json["yourColor"]?.jsonPrimitive?.content!!
            val (actualWhiteToken, actualBlackToken) = if (player2Color == "WHITE") {
                Pair(blackToken, whiteToken)
            } else {
                Pair(whiteToken, blackToken)
            }

            // Verify game was created
            val gameResponse = client.get("/api/games/$gameId")
            gameResponse.status shouldBe HttpStatusCode.OK

            // Step 4: Make first move e2→e4 (white's turn, authenticated with white token)
            val move1Response = client.post("/api/games/$gameId/moves") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $actualWhiteToken")
                setBody("""{"from": "e2", "to": "e4"}""")
            }
            move1Response.status shouldBe HttpStatusCode.OK

            val game1Json = Json.parseToJsonElement(move1Response.bodyAsText()).jsonObject
            game1Json["currentSide"]?.jsonPrimitive?.content shouldBe "BLACK"

            // Step 5: Make second move e7→e5 (black's turn, authenticated with black token)
            val move2Response = client.post("/api/games/$gameId/moves") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $actualBlackToken")
                setBody("""{"from": "e7", "to": "e5"}""")
            }
            move2Response.status shouldBe HttpStatusCode.OK

            val game2Json = Json.parseToJsonElement(move2Response.bodyAsText()).jsonObject
            game2Json["currentSide"]?.jsonPrimitive?.content shouldBe "WHITE"

            // Step 6: Try to make move with wrong player (black player's token on white's turn)
            val move3Response = client.post("/api/games/$gameId/moves") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $actualBlackToken")
                setBody("""{"from": "g8", "to": "f6"}""")
            }
            move3Response.status shouldBe HttpStatusCode.BadRequest

            val errorMessage = move3Response.bodyAsText()
            errorMessage shouldContain "not"
            errorMessage shouldContain "turn"
        }
    }
})
