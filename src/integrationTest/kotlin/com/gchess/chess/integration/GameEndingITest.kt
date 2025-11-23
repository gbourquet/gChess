package com.gchess.chess.integration

import com.gchess.infrastructure.DatabaseITest
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
 * - Checkmate (Scholar's mate - 4 moves)
 * - Stalemate (10-move sequence)
 * - Check without checkmate (remains IN_PROGRESS)
 *
 * All tests use complete move sequences from the starting position,
 * created via matchmaking to ensure realistic game flow.
 *
 * Uses Testcontainers PostgreSQL for database integration testing.
 */
class GameEndingITest : DatabaseITest({

    /**
     * Helper function to register two users and create a game via matchmaking.
     * Returns Triple(gameId, whiteToken, blackToken) where tokens are correctly assigned to colors.
     */
    suspend fun ApplicationTestBuilder.setupGame(): Triple<String, String, String> {
        val client = createClient {}

        // Use timestamps to ensure unique usernames/emails per test run
        val timestamp = System.currentTimeMillis()
        val player1Username = "player1_$timestamp"
        val player2Username = "player2_$timestamp"

        // Register player 1
        val registerPlayer1Response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$player1Username", "email": "$player1Username@test.com", "password": "password123"}""")
        }
        registerPlayer1Response.status shouldBe HttpStatusCode.Created

        // Register player 2
        val registerPlayer2Response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$player2Username", "email": "$player2Username@test.com", "password": "password123"}""")
        }
        registerPlayer2Response.status shouldBe HttpStatusCode.Created

        // Login player 1
        val loginPlayer1Response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$player1Username", "password": "password123"}""")
        }
        val player1Token = Json.parseToJsonElement(loginPlayer1Response.bodyAsText())
            .jsonObject["token"]?.jsonPrimitive?.content!!

        // Login player 2
        val loginPlayer2Response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$player2Username", "password": "password123"}""")
        }
        val player2Token = Json.parseToJsonElement(loginPlayer2Response.bodyAsText())
            .jsonObject["token"]?.jsonPrimitive?.content!!

        // Create game via matchmaking
        // Player 1 joins queue
        val join1Response = client.post("/api/matchmaking/queue") {
            header("Authorization", "Bearer $player1Token")
        }
        join1Response.status shouldBe HttpStatusCode.OK

        // Player 2 joins queue - match is created
        val join2Response = client.post("/api/matchmaking/queue") {
            header("Authorization", "Bearer $player2Token")
        }
        join2Response.status shouldBe HttpStatusCode.OK

        val join2Json = Json.parseToJsonElement(join2Response.bodyAsText()).jsonObject
        join2Json["status"]?.jsonPrimitive?.content shouldBe "MATCHED"

        val gameId = join2Json["gameId"]?.jsonPrimitive?.content!!
        val player2Color = join2Json["yourColor"]?.jsonPrimitive?.content!!

        // Determine which token is white and which is black
        val (whiteToken, blackToken) = if (player2Color == "WHITE") {
            Pair(player2Token, player1Token)
        } else {
            Pair(player1Token, player2Token)
        }

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

    "Scholar's mate (mat du berger) results in CHECKMATE status" {
        testApplication {
            // module() is automatically called from application.conf
            // (see ktor.application.modules in application.conf)

            val (gameId, whiteToken, blackToken) = setupGame()

            // Scholar's mate (mat du berger) - classic beginner's checkmate (4 moves)
            makeMove(gameId, whiteToken, "e2", "e4") // 1. e4
            makeMove(gameId, blackToken, "e7", "e5") // 1... e5
            makeMove(gameId, whiteToken, "f1", "c4") // 2. Bc4 (attacking f7)
            makeMove(gameId, blackToken, "b8", "c6") // 2... Nc6
            makeMove(gameId, whiteToken, "d1", "h5") // 3. Qh5 (threatening Qxf7#)
            makeMove(gameId, blackToken, "g8", "f6") // 3... Nf6?? (blunder, blocks escape square)
            val finalState = makeMove(gameId, whiteToken, "h5", "f7") // 4. Qxf7# checkmate!

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
     * Test stalemate detection with a complete game sequence.
     * This is one of the shortest known stalemate sequences (~10 moves).
     */
    "stalemate detection with complete move sequence" {
        testApplication {
            val (gameId, whiteToken, blackToken) = setupGame()

            // Stalemate sequence - one of the shortest known
            makeMove(gameId, whiteToken, "e2", "e3") // 1. e3
            makeMove(gameId, blackToken, "a7", "a5") // 1... a5
            makeMove(gameId, whiteToken, "d1", "h5") // 2. Qh5
            makeMove(gameId, blackToken, "a8", "a6") // 2... Ra6
            makeMove(gameId, whiteToken, "h5", "a5") // 3. Qxa5
            makeMove(gameId, blackToken, "h7", "h5") // 3... h5
            makeMove(gameId, whiteToken, "h2", "h4") // 4. h4
            makeMove(gameId, blackToken, "a6", "h6") // 4... Rah6
            makeMove(gameId, whiteToken, "a5", "c7") // 5. Qxc7
            makeMove(gameId, blackToken, "f7", "f6") // 5... f6
            makeMove(gameId, whiteToken, "c7", "d7") // 6. Qxd7+
            makeMove(gameId, blackToken, "e8", "f7") // 6... Kf7
            makeMove(gameId, whiteToken, "d7", "b7") // 7. Qxb7
            makeMove(gameId, blackToken, "d8", "d3") // 7... Qd3
            makeMove(gameId, whiteToken, "b7", "b8") // 8. Qxb8
            makeMove(gameId, blackToken, "d3", "h7") // 8... Qh7
            makeMove(gameId, whiteToken, "b8", "c8") // 9. Qxc8
            makeMove(gameId, blackToken, "f7", "g6") // 9... Kg6
            val finalState = makeMove(gameId, whiteToken, "c8", "e6") // 10. Qe6 - stalemate!

            // Verify game ended in stalemate
            // Black king on g6 has no legal moves but is not in check
            finalState["status"]?.jsonPrimitive?.content shouldBe "STALEMATE"
        }
    }

})
