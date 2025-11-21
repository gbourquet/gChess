package com.gchess.matchmaking.integration

import com.gchess.infrastructure.DatabaseE2ETest
import com.gchess.infrastructure.testModule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*

/**
 * End-to-end integration test for the matchmaking API.
 * Tests the complete flow from user registration to matchmaking via HTTP endpoints.
 *
 * This test validates:
 * - Join matchmaking queue (single player - WAITING status)
 * - Join matchmaking queue (two players - automatic match creation, MATCHED status)
 * - Get matchmaking status (WAITING, MATCHED, NOT_FOUND)
 * - Leave matchmaking queue
 * - Error cases (already in queue, player doesn't exist, unauthorized)
 * - ACL integration with User and Chess contexts
 *
 * Uses Testcontainers PostgreSQL for database integration testing.
 */
class MatchmakingE2ETest : DatabaseE2ETest({

    "complete matchmaking flow: join queue, match creation, status checks, and error handling" {
        testApplication {
            application { testModule() }
            val client = createClient { }

            // === Test 1: Single player joins queue - gets WAITING status ===

            // Register and login player 1
            client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "alice", "email": "alice@example.com", "password": "password123"}""")
            }
            val login1Response = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "alice", "password": "password123"}""")
            }
            val login1Json = Json.parseToJsonElement(login1Response.bodyAsText()).jsonObject
            val token1 = login1Json["token"]?.jsonPrimitive?.content!!
            val playerId1 = login1Json["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content!!

            // Player 1 joins queue - should get WAITING at position 1
            val join1Response = client.post("/api/matchmaking/queue") {
                header("Authorization", "Bearer $token1")
            }
            join1Response.status shouldBe HttpStatusCode.OK
            val join1Json = Json.parseToJsonElement(join1Response.bodyAsText()).jsonObject
            join1Json["status"]?.jsonPrimitive?.content shouldBe "WAITING"
            join1Json["queuePosition"]?.jsonPrimitive?.int shouldBe 1
            join1Json["gameId"] shouldBe null
            join1Json["yourColor"] shouldBe null

            // Verify status endpoint returns WAITING
            val status1aResponse = client.get("/api/matchmaking/status") {
                header("Authorization", "Bearer $token1")
            }
            status1aResponse.status shouldBe HttpStatusCode.OK
            val status1aJson = Json.parseToJsonElement(status1aResponse.bodyAsText()).jsonObject
            status1aJson["status"]?.jsonPrimitive?.content shouldBe "WAITING"

            // === Test 2: Try to join queue again - should get Conflict ===

            val joinAgainResponse = client.post("/api/matchmaking/queue") {
                header("Authorization", "Bearer $token1")
            }
            joinAgainResponse.status shouldBe HttpStatusCode.Conflict
            joinAgainResponse.bodyAsText() shouldContain "already"

            // === Test 3: Second player joins - both get MATCHED with game created ===

            // Register and login player 2
            client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "bob", "email": "bob@example.com", "password": "password456"}""")
            }
            val login2Response = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "bob", "password": "password456"}""")
            }
            val login2Json = Json.parseToJsonElement(login2Response.bodyAsText()).jsonObject
            val token2 = login2Json["token"]?.jsonPrimitive?.content!!
            val playerId2 = login2Json["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content!!

            // Player 2 joins queue - both should be MATCHED
            val join2Response = client.post("/api/matchmaking/queue") {
                header("Authorization", "Bearer $token2")
            }
            join2Response.status shouldBe HttpStatusCode.OK
            val join2Json = Json.parseToJsonElement(join2Response.bodyAsText()).jsonObject
            join2Json["status"]?.jsonPrimitive?.content shouldBe "MATCHED"
            val gameId = join2Json["gameId"]?.jsonPrimitive?.content
            gameId shouldNotBe null
            val player2Color = join2Json["yourColor"]?.jsonPrimitive?.content
            (player2Color == "WHITE" || player2Color == "BLACK") shouldBe true

            // Check player 1 status - should now be MATCHED with same gameId
            val status1bResponse = client.get("/api/matchmaking/status") {
                header("Authorization", "Bearer $token1")
            }
            status1bResponse.status shouldBe HttpStatusCode.OK
            val status1bJson = Json.parseToJsonElement(status1bResponse.bodyAsText()).jsonObject
            status1bJson["status"]?.jsonPrimitive?.content shouldBe "MATCHED"
            status1bJson["gameId"]?.jsonPrimitive?.content shouldBe gameId
            val player1Color = status1bJson["yourColor"]?.jsonPrimitive?.content
            // Players should have opposite colors
            player1Color shouldNotBe player2Color

            // Verify game was actually created in Chess context via ACL
            val gameResponse = client.get("/api/games/$gameId")
            gameResponse.status shouldBe HttpStatusCode.OK
            val gameJson = Json.parseToJsonElement(gameResponse.bodyAsText()).jsonObject
            gameJson["id"]?.jsonPrimitive?.content shouldBe gameId
            // Verify players are in the game (one as white, one as black)
            val whitePlayer = gameJson["whitePlayer"]?.jsonPrimitive?.content
            val blackPlayer = gameJson["blackPlayer"]?.jsonPrimitive?.content
            setOf(whitePlayer, blackPlayer) shouldBe setOf(playerId1, playerId2)

            // === Test 4: Third player - queue, check status, then leave ===

            // Register and login player 3
            client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "charlie", "email": "charlie@example.com", "password": "password789"}""")
            }
            val login3Response = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "charlie", "password": "password789"}""")
            }
            val token3 = Json.parseToJsonElement(login3Response.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content!!

            // Player 3 joins - should be WAITING at position 1 (new queue)
            val join3Response = client.post("/api/matchmaking/queue") {
                header("Authorization", "Bearer $token3")
            }
            join3Response.status shouldBe HttpStatusCode.OK
            val join3Json = Json.parseToJsonElement(join3Response.bodyAsText()).jsonObject
            join3Json["status"]?.jsonPrimitive?.content shouldBe "WAITING"
            join3Json["queuePosition"]?.jsonPrimitive?.int shouldBe 1

            // Player 3 leaves queue
            val leave3Response = client.delete("/api/matchmaking/queue") {
                header("Authorization", "Bearer $token3")
            }
            leave3Response.status shouldBe HttpStatusCode.OK
            val leave3Json = Json.parseToJsonElement(leave3Response.bodyAsText()).jsonObject
            leave3Json["removed"]?.jsonPrimitive?.boolean shouldBe true

            // Verify player 3 status is now NOT_FOUND
            val status3Response = client.get("/api/matchmaking/status") {
                header("Authorization", "Bearer $token3")
            }
            status3Response.status shouldBe HttpStatusCode.OK
            val status3Json = Json.parseToJsonElement(status3Response.bodyAsText()).jsonObject
            status3Json["status"]?.jsonPrimitive?.content shouldBe "NOT_FOUND"

            // Try to leave again - should return false (not in queue)
            val leave3AgainResponse = client.delete("/api/matchmaking/queue") {
                header("Authorization", "Bearer $token3")
            }
            leave3AgainResponse.status shouldBe HttpStatusCode.OK
            val leave3AgainJson = Json.parseToJsonElement(leave3AgainResponse.bodyAsText()).jsonObject
            leave3AgainJson["removed"]?.jsonPrimitive?.boolean shouldBe false

            // === Test 5: Verify matched players still have MATCHED status ===

            val finalStatus1Response = client.get("/api/matchmaking/status") {
                header("Authorization", "Bearer $token1")
            }
            val finalStatus1Json = Json.parseToJsonElement(finalStatus1Response.bodyAsText()).jsonObject
            finalStatus1Json["status"]?.jsonPrimitive?.content shouldBe "MATCHED"
            finalStatus1Json["gameId"]?.jsonPrimitive?.content shouldBe gameId

            val finalStatus2Response = client.get("/api/matchmaking/status") {
                header("Authorization", "Bearer $token2")
            }
            val finalStatus2Json = Json.parseToJsonElement(finalStatus2Response.bodyAsText()).jsonObject
            finalStatus2Json["status"]?.jsonPrimitive?.content shouldBe "MATCHED"
            finalStatus2Json["gameId"]?.jsonPrimitive?.content shouldBe gameId

            // === Test 6: Authentication and authorization - requires valid JWT token ===

            // Join queue without token - Unauthorized
            val joinUnauth = client.post("/api/matchmaking/queue")
            joinUnauth.status shouldBe HttpStatusCode.Unauthorized

            // Get status without token - Unauthorized
            val statusUnauth = client.get("/api/matchmaking/status")
            statusUnauth.status shouldBe HttpStatusCode.Unauthorized

            // Leave queue without token - Unauthorized
            val leaveUnauth = client.delete("/api/matchmaking/queue")
            leaveUnauth.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})
