package com.gchess.matchmaking.application.usecase

import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.random.Random

class CreateGameFromMatchUseCaseTest : FunSpec({

    test("execute should create game with random color assignment") {
        // Given
        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()
        val gameId = GameId.generate()
        val gameCreator = FakeGameCreator(gameId)

        val useCase = CreateGameFromMatchUseCase(gameCreator)

        // When
        val result = useCase.execute(player1, player2)

        // Then
        result.isSuccess shouldBe true
        val match = result.getOrNull()!!
        match.gameId shouldBe gameId

        // One player should be white, the other black
        val whitePlayer = match.whitePlayerId
        val blackPlayer = match.blackPlayerId
        (whitePlayer == player1 && blackPlayer == player2) ||
            (whitePlayer == player2 && blackPlayer == player1) shouldBe true
    }

    test("execute should assign colors with 50/50 distribution") {
        // Given
        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()
        val gameId = GameId.generate()
        val gameCreator = FakeGameCreator(gameId)

        // Use fixed seed for reproducible random
        val random = Random(42)
        val useCase = CreateGameFromMatchUseCase(gameCreator, random)

        // When - create 100 matches
        val matches = (1..100).map {
            useCase.execute(player1, player2).getOrNull()!!
        }

        // Then - should have reasonable distribution (not all same color)
        val player1WhiteCount = matches.count { it.whitePlayerId == player1 }
        val player1BlackCount = matches.count { it.blackPlayerId == player1 }

        // With seed 42, we should have a mix (not 100 or 0)
        player1WhiteCount shouldNotBe 0
        player1WhiteCount shouldNotBe 100
        player1BlackCount shouldNotBe 0
        player1BlackCount shouldNotBe 100

        // Should sum to 100
        (player1WhiteCount + player1BlackCount) shouldBe 100
    }

    test("execute should return failure when GameCreator fails") {
        // Given
        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()
        val errorMessage = "Failed to create game"
        val gameCreator = FakeGameCreator(failure = Exception(errorMessage))

        val useCase = CreateGameFromMatchUseCase(gameCreator)

        // When
        val result = useCase.execute(player1, player2)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<Exception>()
        result.exceptionOrNull()!!.message shouldBe errorMessage
    }

    test("execute should propagate GameCreator error") {
        // Given
        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()
        val specificError = "Player validation failed in Chess context"
        val gameCreator = FakeGameCreator(failure = Exception(specificError))

        val useCase = CreateGameFromMatchUseCase(gameCreator)

        // When
        val result = useCase.execute(player1, player2)

        // Then
        result.exceptionOrNull()!!.message shouldBe specificError
    }

    test("execute should assign different colors to each player") {
        // Given
        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()
        val gameId = GameId.generate()
        val gameCreator = FakeGameCreator(gameId)

        val useCase = CreateGameFromMatchUseCase(gameCreator)

        // When
        val result = useCase.execute(player1, player2)

        // Then
        val match = result.getOrNull()!!
        match.whitePlayerId shouldNotBe match.blackPlayerId
    }

    test("execute should set matchedAt and expiresAt timestamps") {
        // Given
        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()
        val gameId = GameId.generate()
        val gameCreator = FakeGameCreator(gameId)

        val useCase = CreateGameFromMatchUseCase(gameCreator)

        // When
        val result = useCase.execute(player1, player2)

        // Then
        val match = result.getOrNull()!!
        match.matchedAt shouldNotBe null
        match.expiresAt shouldNotBe null
        match.expiresAt shouldNotBe match.matchedAt
    }
})

