package com.gchess.matchmaking.application.usecase

import com.gchess.matchmaking.infrastructure.adapter.driven.InMemoryMatchmakingQueue
import com.gchess.shared.domain.model.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LeaveMatchmakingUseCaseTest : FunSpec({

    test("execute should remove player from queue successfully") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val useCase = LeaveMatchmakingUseCase(queue)
        val playerId = UserId.generate()

        // Add player to queue
        queue.addPlayer(playerId)

        // When
        val result = useCase.execute(playerId)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull() shouldBe true
        queue.isPlayerInQueue(playerId) shouldBe false
    }

    test("execute should return false when player is not in queue") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val useCase = LeaveMatchmakingUseCase(queue)
        val playerId = UserId.generate()

        // When
        val result = useCase.execute(playerId)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull() shouldBe false
    }

    test("execute should only remove specified player") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val useCase = LeaveMatchmakingUseCase(queue)
        val player1 = UserId.generate()
        val player2 = UserId.generate()
        val player3 = UserId.generate()

        queue.addPlayer(player1)
        queue.addPlayer(player2)
        queue.addPlayer(player3)

        // When - remove player2
        useCase.execute(player2)

        // Then - only player2 removed
        queue.isPlayerInQueue(player1) shouldBe true
        queue.isPlayerInQueue(player2) shouldBe false
        queue.isPlayerInQueue(player3) shouldBe true
        queue.getQueueSize() shouldBe 2
    }

    test("execute should allow same player to leave multiple times without error") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val useCase = LeaveMatchmakingUseCase(queue)
        val playerId = UserId.generate()

        queue.addPlayer(playerId)
        useCase.execute(playerId)

        // When - leave again
        val result = useCase.execute(playerId)

        // Then - should not fail, just return false
        result.isSuccess shouldBe true
        result.getOrNull() shouldBe false
    }

    test("execute should work correctly after multiple joins and leaves") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val useCase = LeaveMatchmakingUseCase(queue)
        val player1 = UserId.generate()
        val player2 = UserId.generate()

        // When - join, leave, join, leave
        queue.addPlayer(player1)
        queue.addPlayer(player2)
        useCase.execute(player1)
        queue.addPlayer(player1)
        useCase.execute(player2)

        // Then
        queue.isPlayerInQueue(player1) shouldBe true
        queue.isPlayerInQueue(player2) shouldBe false
        queue.getQueueSize() shouldBe 1
    }
})
