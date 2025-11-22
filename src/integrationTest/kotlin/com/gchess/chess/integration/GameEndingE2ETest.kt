package com.gchess.chess.integration

import com.gchess.infrastructure.DatabaseE2ETest
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*

/**
 * End-to-end integration tests for game-ending conditions.
 *
 * Tests that the game status is automatically updated when moves lead to:
 * - Checkmate
 * - Stalemate
 * - Draw (insufficient material, fifty-move rule)
 *
 * Also verifies that check (without mate) keeps status as IN_PROGRESS.
 *
 * NOTE: Comprehensive checkmate/stalemate E2E tests from starting position
 * require long move sequences. Future enhancement: implement FEN import
 * to set up specific positions directly for easier testing.
 *
 * Uses Testcontainers PostgreSQL for database integration testing.
 */
class GameEndingE2ETest : DatabaseE2ETest({

    /**
     * Helper function to register two users and create a game with optional FEN position.
     * Returns Triple(gameId, whiteToken, blackToken)
     */
    suspend fun ApplicationTestBuilder.setupGame(initialPosition: String? = null): Triple<String, String, String> {
        val client = createClient {}

        // Use timestamps to ensure unique usernames/emails per test run
        val timestamp = System.currentTimeMillis()
        val whiteUsername = "white_$timestamp"
        val blackUsername = "black_$timestamp"

        // Register white player
        val registerWhiteResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$whiteUsername", "email": "$whiteUsername@test.com", "password": "password123"}""")
        }
        val whiteUserJson = Json.parseToJsonElement(registerWhiteResponse.bodyAsText()).jsonObject
        val whitePlayerId = whiteUserJson["id"]?.jsonPrimitive?.content!!

        // Register black player
        val registerBlackResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$blackUsername", "email": "$blackUsername@test.com", "password": "password123"}""")
        }
        val blackUserJson = Json.parseToJsonElement(registerBlackResponse.bodyAsText()).jsonObject
        val blackPlayerId = blackUserJson["id"]?.jsonPrimitive?.content!!

        // Login white player
        val loginWhiteResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$whiteUsername", "password": "password123"}""")
        }
        val whiteToken = Json.parseToJsonElement(loginWhiteResponse.bodyAsText())
            .jsonObject["token"]?.jsonPrimitive?.content!!

        // Login black player
        val loginBlackResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$blackUsername", "password": "password123"}""")
        }
        val blackToken = Json.parseToJsonElement(loginBlackResponse.bodyAsText())
            .jsonObject["token"]?.jsonPrimitive?.content!!

        // Create game with optional custom position
        val gameRequestBody = if (initialPosition != null) {
            """{"whitePlayerId": "$whitePlayerId", "blackPlayerId": "$blackPlayerId", "initialPosition": "$initialPosition"}"""
        } else {
            """{"whitePlayerId": "$whitePlayerId", "blackPlayerId": "$blackPlayerId"}"""
        }

        val createGameResponse = client.post("/api/games") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $whiteToken")
            setBody(gameRequestBody)
        }
        val gameId = Json.parseToJsonElement(createGameResponse.bodyAsText())
            .jsonObject["id"]?.jsonPrimitive?.content!!

        return Triple(gameId, whiteToken, blackToken)
    }

    /**
     * Helper function to make a move.
     * Returns the updated game JSON.
     */
    suspend fun ApplicationTestBuilder.makeMove(
        gameId: String,
        token: String,
        from: String,
        to: String,
        promotion: String? = null
    ): JsonObject {
        val client = createClient {}

        val moveBody = if (promotion != null) {
            """{"from": "$from", "to": "$to", "promotion": "$promotion"}"""
        } else {
            """{"from": "$from", "to": "$to"}"""
        }

        println("moveBody : $moveBody")

        val response = client.post("/api/games/$gameId/moves") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(moveBody)
        }

        response.status shouldBe HttpStatusCode.OK
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    "Fool's mate results in CHECKMATE status" {
        testApplication {
            // module() is automatically called from application.conf
            // (see ktor.application.modules in application.conf)

            val (gameId, whiteToken, blackToken) = setupGame()

            // Fool's mate - fastest possible checkmate (2 moves)
            makeMove(gameId, whiteToken, "f2", "f3") // 1. f3 (weak move)
            makeMove(gameId, blackToken, "e7", "e5") // 1... e5
            makeMove(gameId, whiteToken, "g2", "g4") // 2. g4 (very weak)
            val finalState = makeMove(gameId, blackToken, "d8", "h4") // 2... Qh4# checkmate!

            // Verify game ended in checkmate
            finalState["status"]?.jsonPrimitive?.content shouldBe "CHECKMATE"
        }
    }

    "King in check but not checkmate keeps status IN_PROGRESS" {
        testApplication {
            // module() is automatically called from application.conf
            // (see ktor.application.modules in application.conf)

            val (gameId, whiteToken, blackToken) = setupGame()

            // Create a check position
            makeMove(gameId, whiteToken, "e2", "e4") // 1. e4
            makeMove(gameId, blackToken, "e7", "e5") // 1... e5
            makeMove(gameId, whiteToken, "f1", "c4") // 2. Bc4
            makeMove(gameId, blackToken, "b8", "c6") // 2... Nc6
            makeMove(gameId, whiteToken, "d1", "h5") // 3. Qh5 (threatens f7)
            makeMove(gameId, blackToken, "g7", "g6") // 3... g6 (blocks)
            makeMove(gameId, whiteToken, "h5", "f3") // 4. Qf3
            makeMove(gameId, blackToken, "f8", "g7") // 4... Bg7
            val gameState = makeMove(gameId, whiteToken, "c4", "f7") // 5. Bxf7+ (check!)

            // Verify game is IN_PROGRESS (check is not a game-ending status)
            gameState["status"]?.jsonPrimitive?.content shouldBe "IN_PROGRESS"
        }
    }

    /**
     * Test stalemate detection using FEN import.
     * Sets up a position where white can deliver stalemate with one move.
     */
    "stalemate detection with FEN position" {
        testApplication {
            // Position: Black king on h8, White queen on f5, White king on f6
            // White to move - Qf7 will create stalemate (king on h8 has no moves and is not in check)
            val stalemateSetupFen = "7k/8/5K2/5Q2/8/8/8/8 w - - 0 1"

            val (gameId, whiteToken, blackToken) = setupGame(stalemateSetupFen)

            // White plays Qf7 - this should result in stalemate
            val finalState = makeMove(gameId, whiteToken, "f5", "g6")

            // Verify game ended in stalemate
            finalState["status"]?.jsonPrimitive?.content shouldBe "STALEMATE"
        }
    }

    /**
     * Test insufficient material draw using FEN import.
     * Sets up a position where capturing the last piece results in K vs K.
     */
    "insufficient material draw with FEN position" {
        testApplication {
            // Position: White king on e2, Black king on e8, Black knight on d3
            // White to move - Kxd3 will result in K vs K (insufficient material = DRAW)
            val insufficientMaterialSetupFen = "4k3/8/8/8/8/3n4/4K3/8 w - - 0 1"

            val (gameId, whiteToken, blackToken) = setupGame(insufficientMaterialSetupFen)

            // White captures the knight: Kxd3 - this should result in DRAW (insufficient material)
            val finalState = makeMove(gameId, whiteToken, "e2", "d3")

            // Verify game ended in draw due to insufficient material
            finalState["status"]?.jsonPrimitive?.content shouldBe "DRAW"
        }
    }
})
