package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.shared.domain.model.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class InMemoryMatchmakingQueueTest : FunSpec({

    test("addPlayer should add player to queue successfully") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val playerId = UserId.generate()

        // When
        val entry = queue.addPlayer(playerId)

        // Then
        entry.userId shouldBe playerId
        queue.isPlayerInQueue(playerId) shouldBe true
        queue.getQueueSize() shouldBe 1
    }

    test("addPlayer should throw exception when player is already in queue") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val playerId = UserId.generate()
        queue.addPlayer(playerId)

        // When/Then
        shouldThrow<IllegalStateException> {
            queue.addPlayer(playerId)
        }
    }

    test("removePlayer should remove player from queue") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val playerId = UserId.generate()
        queue.addPlayer(playerId)

        // When
        val removed = queue.removePlayer(playerId)

        // Then
        removed shouldBe true
        queue.isPlayerInQueue(playerId) shouldBe false
        queue.getQueueSize() shouldBe 0
    }

    test("removePlayer should return false when player is not in queue") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val playerId = UserId.generate()

        // When
        val removed = queue.removePlayer(playerId)

        // Then
        removed shouldBe false
    }

    test("isPlayerInQueue should return false for player not in queue") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val playerId = UserId.generate()

        // When/Then
        queue.isPlayerInQueue(playerId) shouldBe false
    }

    test("getQueueSize should return correct size") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val player1 = UserId.generate()
        val player2 = UserId.generate()
        val player3 = UserId.generate()

        // When/Then
        queue.getQueueSize() shouldBe 0
        queue.addPlayer(player1)
        queue.getQueueSize() shouldBe 1
        queue.addPlayer(player2)
        queue.getQueueSize() shouldBe 2
        queue.addPlayer(player3)
        queue.getQueueSize() shouldBe 3
        queue.removePlayer(player2)
        queue.getQueueSize() shouldBe 2
    }

    test("findMatch should return null when queue has less than 2 players") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val player1 = UserId.generate()

        // When/Then - empty queue
        queue.findMatch() shouldBe null

        // When/Then - 1 player
        queue.addPlayer(player1)
        queue.findMatch() shouldBe null
    }

    test("findMatch should match two oldest players (FIFO)") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val player1 = UserId.generate()
        val player2 = UserId.generate()
        val player3 = UserId.generate()

        // Add players in order
        val entry1 = queue.addPlayer(player1)
        delay(10) // Ensure different timestamps
        val entry2 = queue.addPlayer(player2)
        delay(10)
        queue.addPlayer(player3)

        // When
        val match = queue.findMatch()

        // Then
        match shouldNotBe null
        match!!.first.userId shouldBe player1
        match.second.userId shouldBe player2
        match.first.joinedAt shouldBe entry1.joinedAt
        match.second.joinedAt shouldBe entry2.joinedAt

        // Players should be removed from queue
        queue.isPlayerInQueue(player1) shouldBe false
        queue.isPlayerInQueue(player2) shouldBe false
        queue.isPlayerInQueue(player3) shouldBe true
        queue.getQueueSize() shouldBe 1
    }

    test("findMatch should remove both players atomically from queue") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val player1 = UserId.generate()
        val player2 = UserId.generate()

        queue.addPlayer(player1)
        queue.addPlayer(player2)

        // When
        val match = queue.findMatch()

        // Then
        match shouldNotBe null
        queue.getQueueSize() shouldBe 0
    }

    test("multiple findMatch calls should pair players correctly") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val player1 = UserId.generate()
        val player2 = UserId.generate()
        val player3 = UserId.generate()
        val player4 = UserId.generate()

        queue.addPlayer(player1)
        delay(10)
        queue.addPlayer(player2)
        delay(10)
        queue.addPlayer(player3)
        delay(10)
        queue.addPlayer(player4)

        // When - first match
        val match1 = queue.findMatch()

        // Then - should match player1 and player2
        match1 shouldNotBe null
        match1!!.first.userId shouldBe player1
        match1.second.userId shouldBe player2

        // When - second match
        val match2 = queue.findMatch()

        // Then - should match player3 and player4
        match2 shouldNotBe null
        match2!!.first.userId shouldBe player3
        match2.second.userId shouldBe player4

        // Queue should be empty
        queue.getQueueSize() shouldBe 0
    }

    test("concurrent addPlayer calls should be thread-safe") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val playerIds = (1..100).map { UserId.generate() }

        // When - add 100 players concurrently
        val jobs = playerIds.map { playerId ->
            async {
                queue.addPlayer(playerId)
            }
        }
        jobs.awaitAll()

        // Then - all players should be in queue
        queue.getQueueSize() shouldBe 100
        playerIds.forEach { playerId ->
            queue.isPlayerInQueue(playerId) shouldBe true
        }
    }

    test("concurrent findMatch calls should not create race conditions") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val playerIds = (1..20).map { UserId.generate() }

        // Add 20 players
        playerIds.forEach { playerId ->
            queue.addPlayer(playerId)
            delay(5)
        }

        // When - call findMatch 10 times concurrently (should create 10 matches)
        val jobs = (1..10).map {
            async {
                queue.findMatch()
            }
        }
        val matches = jobs.awaitAll()

        // Then - should have exactly 10 matches (no duplicates, no null)
        matches.forEach { match ->
            match shouldNotBe null
        }

        // All players should be matched (queue empty)
        queue.getQueueSize() shouldBe 0

        // Verify no player was matched twice
        val matchedPlayerIds = matches.flatMap { match ->
            listOf(match!!.first.userId, match.second.userId)
        }
        matchedPlayerIds.size shouldBe 20
        matchedPlayerIds.distinct().size shouldBe 20 // No duplicates
    }

    test("concurrent add and remove should be thread-safe") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val player1 = UserId.generate()
        val player2 = UserId.generate()
        val player3 = UserId.generate()

        queue.addPlayer(player1)
        queue.addPlayer(player2)
        queue.addPlayer(player3)

        // When - remove player2 while trying to find match concurrently
        val removeJob = async {
            delay(5)
            queue.removePlayer(player2)
        }

        val findMatchJob = async {
            delay(10)
            queue.findMatch()
        }

        removeJob.await()
        val match = findMatchJob.await()

        // Then - match should not include player2 (was removed)
        match shouldNotBe null
        match!!.first.userId shouldNotBe player2
        match.second.userId shouldNotBe player2
    }
})
