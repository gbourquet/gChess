package com.gchess.matchmaking.application.usecase

import com.gchess.shared.domain.model.PlayerSide
import com.gchess.matchmaking.domain.model.Match
import com.gchess.matchmaking.domain.model.QueueEntry
import com.gchess.matchmaking.domain.port.MatchRepository
import com.gchess.matchmaking.domain.port.MatchmakingQueue
import com.gchess.matchmaking.domain.port.UserExistenceChecker
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

class JoinMatchmakingUseCaseTest : FunSpec({

    test("execute should add user to queue and return WAITING when no match found") {
        // Given
        val queue = mockk<MatchmakingQueue>()
        val repository = mockk<MatchRepository>()
        val userChecker = mockk<UserExistenceChecker>()
        val createGameUseCase = mockk<CreateGameFromMatchUseCase>()

        val useCase = JoinMatchmakingUseCase(queue, repository, userChecker, createGameUseCase)
        val userId = UserId.generate()
        val queueEntry = QueueEntry(userId, Clock.System.now())

        // Mock behavior
        coEvery { userChecker.exists(userId) } returns true
        coEvery { repository.findByPlayer(userId) } returns null
        coEvery { queue.isPlayerInQueue(userId) } returns false
        coEvery { queue.addPlayer(userId) } returns queueEntry
        coEvery { queue.getQueueSize() } returns 10
        coEvery { queue.findMatch() } returns null

        // When
        val result = useCase.execute(userId)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull().shouldBeInstanceOf<MatchmakingResult.Waiting>()

        // Verify interactions
        coVerify { userChecker.exists(userId) }
        coVerify { repository.findByPlayer(userId) }
        coVerify { queue.isPlayerInQueue(userId) }
        coVerify { queue.addPlayer(userId) }
        coVerify { queue.findMatch() }
        coVerify { queue.getQueueSize() }
        confirmVerified(userChecker, repository, queue)
    }

    test("execute should fail when user does not exist") {
        // Given
        val queue = mockk<MatchmakingQueue>()
        val repository = mockk<MatchRepository>()
        val userChecker = mockk<UserExistenceChecker>()
        val createGameUseCase = mockk<CreateGameFromMatchUseCase>()

        val useCase = JoinMatchmakingUseCase(queue, repository, userChecker, createGameUseCase)
        val userId = UserId.generate()

        // Mock behavior
        coEvery { userChecker.exists(userId) } returns false

        // When
        val result = useCase.execute(userId)

        // Then
        result.isFailure shouldBe true

        // Verify - only userChecker should be called
        coVerify { userChecker.exists(userId) }
        coVerify(exactly = 0) { queue.addPlayer(any()) }
        confirmVerified(userChecker, queue)
    }

    test("execute should fail when user is already in queue") {
        // Given
        val queue = mockk<MatchmakingQueue>()
        val repository = mockk<MatchRepository>()
        val userChecker = mockk<UserExistenceChecker>()
        val createGameUseCase = mockk<CreateGameFromMatchUseCase>()

        val useCase = JoinMatchmakingUseCase(queue, repository, userChecker, createGameUseCase)
        val userId = UserId.generate()

        // Mock behavior
        coEvery { userChecker.exists(userId) } returns true
        coEvery { repository.findByPlayer(userId) } returns null
        coEvery { queue.isPlayerInQueue(userId) } returns true

        // When
        val result = useCase.execute(userId)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "User is already in the matchmaking queue"

        // Verify
        coVerify { userChecker.exists(userId) }
        coVerify { queue.isPlayerInQueue(userId) }
        coVerify(exactly = 0) { queue.addPlayer(any()) }
    }

    test("execute should fail when user already has a match") {
        // Given
        val queue = mockk<MatchmakingQueue>()
        val repository = mockk<MatchRepository>()
        val userChecker = mockk<UserExistenceChecker>()
        val createGameUseCase = mockk<CreateGameFromMatchUseCase>()

        val useCase = JoinMatchmakingUseCase(queue, repository, userChecker, createGameUseCase)
        val userId = UserId.generate()
        val now = Clock.System.now()

        val existingMatch = Match(
            whiteUserId = userId,
            blackUserId = UserId.generate(),
            gameId = GameId.generate(),
            matchedAt = now,
            expiresAt = now + 5.minutes
        )

        // Mock behavior
        coEvery { userChecker.exists(userId) } returns true
        coEvery { queue.isPlayerInQueue(userId) } returns true

        // When
        val result = useCase.execute(userId)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "User is already in the matchmaking queue"

        // Verify
        coVerify { userChecker.exists(userId) }
        coVerify(exactly = 0) { queue.addPlayer(any()) }
    }

    test("execute should create match and game when match is found") {
        // Given
        val queue = mockk<MatchmakingQueue>()
        val repository = mockk<MatchRepository>()
        val userChecker = mockk<UserExistenceChecker>()
        val createGameUseCase = mockk<CreateGameFromMatchUseCase>()

        val useCase = JoinMatchmakingUseCase(queue, repository, userChecker, createGameUseCase)

        val user1 = UserId.generate()
        val user2 = UserId.generate()
        val gameId = GameId.generate()
        val now = Clock.System.now()

        val queueEntry1 = QueueEntry(user1, now)
        val queueEntry2 = QueueEntry(user2, now)
        val match = Match(
            whiteUserId = user1,
            blackUserId = user2,
            gameId = gameId,
            matchedAt = now,
            expiresAt = now + 5.minutes
        )

        // Mock behavior for user1
        coEvery { userChecker.exists(user1) } returns true
        coEvery { repository.findByPlayer(user1) } returns null
        coEvery { queue.isPlayerInQueue(user1) } returns false
        coEvery { queue.addPlayer(user1) } returns queueEntry1
        coEvery { queue.findMatch() } returns Pair(queueEntry1,queueEntry2)

        // Mock createGameUseCase
        coEvery { createGameUseCase.execute(user1, user2) } returns Result.success(match)

        // Mock queue removal
        coEvery { queue.removePlayer(user1) } returns true
        coEvery { queue.removePlayer(user2) } returns true

        // Mock repository save
        coEvery { repository.save(match) } just Runs

        // When
        val result = useCase.execute(user1)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull().shouldBeInstanceOf<MatchmakingResult.Matched>()
        val matched = result.getOrNull() as MatchmakingResult.Matched
        matched.gameId shouldBe gameId
        matched.yourColor shouldBe PlayerSide.WHITE // Based on match assignment

        // Verify interactions
        coVerify { queue.addPlayer(user1) }
        coVerify { queue.findMatch() }
        coVerify { createGameUseCase.execute(user1, user2) }
        coVerify { repository.save(match) }
    }

    test("execute should propagate game creation failure") {
        // Given
        val queue = mockk<MatchmakingQueue>()
        val repository = mockk<MatchRepository>()
        val userChecker = mockk<UserExistenceChecker>()
        val createGameUseCase = mockk<CreateGameFromMatchUseCase>()

        val useCase = JoinMatchmakingUseCase(queue, repository, userChecker, createGameUseCase)

        val user1 = UserId.generate()
        val user2 = UserId.generate()
        val now = Clock.System.now()
        val errorMessage = "Failed to create game in Chess context"
        val queueEntry1 = QueueEntry(user1, now)
        val queueEntry2 = QueueEntry(user2, now)

        // Mock behavior
        coEvery { userChecker.exists(user1) } returns true
        coEvery { repository.findByPlayer(user1) } returns null
        coEvery { queue.isPlayerInQueue(user1) } returns false
        coEvery { queue.addPlayer(user1) } returns QueueEntry(user1, now)
        coEvery { queue.findMatch() } returns Pair(queueEntry1, queueEntry2)
        coEvery { createGameUseCase.execute(user1, user2) } returns Result.failure(Exception(errorMessage))
        coEvery { queue.getQueueSize() } returns 10
        // When
        val result = useCase.execute(user1)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe errorMessage

        // Verify
        coVerify { createGameUseCase.execute(user1, user2) }
        coVerify(exactly = 0) { repository.save(any()) }
    }
})
