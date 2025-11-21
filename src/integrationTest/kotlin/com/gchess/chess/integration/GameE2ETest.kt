package com.gchess.chess.integration

import com.gchess.infrastructure.DatabaseE2ETest
import com.gchess.infrastructure.testModule
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
class GameE2ETest : DatabaseE2ETest({

    "complete game flow: register users, create game, make valid moves, and reject invalid turn" {
        testApplication {
            application {
                testModule()
            }

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

            // Step 3: Create a new game with the two players (authenticated as white player)
            val createGameResponse = client.post("/api/games") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $whiteToken")
                setBody("""{"whitePlayerId": "$whitePlayerId", "blackPlayerId": "$blackPlayerId"}""")
            }
            createGameResponse.status shouldBe HttpStatusCode.Created

            val gameJson = Json.parseToJsonElement(createGameResponse.bodyAsText()).jsonObject
            val gameId = gameJson["id"]?.jsonPrimitive?.content
                ?: error("Game ID not found in response")

            // Verify that the game has the correct players
            gameJson["whitePlayer"]?.jsonPrimitive?.content shouldBe whitePlayerId
            gameJson["blackPlayer"]?.jsonPrimitive?.content shouldBe blackPlayerId

            // Step 4: Make first move e2→e4 (white's turn, authenticated with white token)
            val move1Response = client.post("/api/games/$gameId/moves") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $whiteToken")
                setBody("""{"from": "e2", "to": "e4"}""")
            }
            move1Response.status shouldBe HttpStatusCode.OK

            val game1Json = Json.parseToJsonElement(move1Response.bodyAsText()).jsonObject
            game1Json["currentSide"]?.jsonPrimitive?.content shouldBe "BLACK"

            // Step 5: Make second move e7→e5 (black's turn, authenticated with black token)
            val move2Response = client.post("/api/games/$gameId/moves") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $blackToken")
                setBody("""{"from": "e7", "to": "e5"}""")
            }
            move2Response.status shouldBe HttpStatusCode.OK

            val game2Json = Json.parseToJsonElement(move2Response.bodyAsText()).jsonObject
            game2Json["currentSide"]?.jsonPrimitive?.content shouldBe "WHITE"

            // Step 6: Try to make move with wrong player (black player's token on white's turn)
            val move3Response = client.post("/api/games/$gameId/moves") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $blackToken")
                setBody("""{"from": "g8", "to": "f6"}""")
            }
            move3Response.status shouldBe HttpStatusCode.BadRequest

            val errorMessage = move3Response.bodyAsText()
            errorMessage shouldContain "not"
            errorMessage shouldContain "turn"
        }
    }
})
