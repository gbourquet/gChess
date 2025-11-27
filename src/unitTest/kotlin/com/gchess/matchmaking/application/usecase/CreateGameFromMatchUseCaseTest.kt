package com.gchess.matchmaking.application.usecase

import com.gchess.matchmaking.domain.port.GameCreator
import com.gchess.matchmaking.domain.port.UserExistenceChecker
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.random.Random

class CreateGameFromMatchUseCaseTest : FunSpec({

    test("execute should create game with random color assignment") {
        // Given
        val user1 = UserId.generate()
        val user2 = UserId.generate()
        val gameId = GameId.generate()
        val gameCreator = mockk<GameCreator>()
        val userChecker = mockk<UserExistenceChecker>()

        coEvery { gameCreator.createGame(any(), any()) } returns Result.success(gameId)
        coEvery { userChecker.exists(any()) } returns true

        val useCase = CreateGameFromMatchUseCase(gameCreator, userChecker)

        // When
        val result = useCase.execute(user1, user2)

        // Then
        result.isSuccess shouldBe true
        val match = result.getOrNull()!!
        match.gameId shouldBe gameId

        // One player should be white, the other black
        val whitePlayer = match.whitePlayer
        val blackPlayer = match.blackPlayer
        (whitePlayer.userId == user1 && blackPlayer.userId == user2) ||
                (whitePlayer.userId == user2 && blackPlayer.userId == user1) shouldBe true
    }

    test("execute should assign colors with 50/50 distribution") {
        // Given
        val user1 = UserId.generate()
        val user2 = UserId.generate()
        val gameId = GameId.generate()
        val gameCreator = mockk<GameCreator>()
        val userChecker = mockk<UserExistenceChecker>()

        coEvery { userChecker.exists(any()) } returns true
        coEvery { gameCreator.createGame(any(), any()) } returns Result.success(gameId)

        // Use fixed seed for reproducible random
        val random = Random(42)
        val useCase = CreateGameFromMatchUseCase(gameCreator, userChecker, random)

        // When - create 100 matches
        val matches = (1..100).map {
            useCase.execute(user1, user2).getOrNull()!!
        }

        // Then - should have reasonable distribution (not all same color)
        val user1WhiteCount = matches.count { it.whitePlayer.userId == user1 }
        val user1BlackCount = matches.count { it.blackPlayer.userId == user1 }

        // With seed 42, we should have a mix (not 100 or 0)
        user1WhiteCount shouldNotBe 0
        user1WhiteCount shouldNotBe 100
        user1BlackCount shouldNotBe 0
        user1BlackCount shouldNotBe 100

        // Should sum to 100
        (user1WhiteCount + user1BlackCount) shouldBe 100
    }

    test("execute should return failure when GameCreator fails") {
        // Given
        val user1 = UserId.generate()
        val user2 = UserId.generate()
        val errorMessage = "Failed to create game"
        val gameCreator = mockk<GameCreator>()
        val userChecker = mockk<UserExistenceChecker>()

        coEvery { userChecker.exists(any()) } returns true
        coEvery { gameCreator.createGame(any(), any()) } returns Result.failure(Exception(errorMessage))

        val useCase = CreateGameFromMatchUseCase(gameCreator, userChecker)

        // When
        val result = useCase.execute(user1, user2)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<Exception>()
        result.exceptionOrNull()!!.message shouldBe errorMessage
    }

    test("execute should propagate GameCreator error") {
        // Given
        val user1 = UserId.generate()
        val user2 = UserId.generate()
        val specificError = "Player validation failed in Chess context"
        val gameCreator = mockk<GameCreator>()
        val userChecker = mockk<UserExistenceChecker>()

        coEvery { userChecker.exists(any()) } returns true
        coEvery { gameCreator.createGame(any(), any()) } returns Result.failure(Exception(specificError))

        val useCase = CreateGameFromMatchUseCase(gameCreator, userChecker)

        // When
        val result = useCase.execute(user1, user2)

        // Then
        result.exceptionOrNull()!!.message shouldBe specificError
    }

    test("execute should assign different colors to each player") {
        // Given
        val user1 = UserId.generate()
        val user2 = UserId.generate()
        val gameId = GameId.generate()
        val gameCreator = mockk<GameCreator>()
        val userChecker = mockk<UserExistenceChecker>()

        coEvery { userChecker.exists(any()) } returns true
        coEvery { gameCreator.createGame(any(), any()) } returns Result.success(gameId)

        val useCase = CreateGameFromMatchUseCase(gameCreator, userChecker)

        // When
        val result = useCase.execute(user1, user2)

        // Then
        val match = result.getOrNull()!!
        match.whitePlayer.userId shouldNotBe match.blackPlayer.userId
    }
})

