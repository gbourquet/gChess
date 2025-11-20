package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.chess.domain.model.Game
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ChessContextGameCreatorTest : FunSpec({

    test("createGame should return GameId on success") {
        // Given
        val whitePlayerId = PlayerId.generate()
        val blackPlayerId = PlayerId.generate()
        val expectedGameId = GameId.generate()

        // Fake CreateGameUseCase that always succeeds
        val fakeCreateGameUseCase = FakeCreateGameUseCase(
            result = Result.success(Game(
                id = expectedGameId,
                whitePlayer = whitePlayerId,
                blackPlayer = blackPlayerId
            ))
        )

        val gameCreator = ChessContextGameCreator(fakeCreateGameUseCase)

        // When
        val result = gameCreator.createGame(whitePlayerId, blackPlayerId)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull() shouldBe expectedGameId
    }

    test("createGame should return failure when CreateGameUseCase fails") {
        // Given
        val whitePlayerId = PlayerId.generate()
        val blackPlayerId = PlayerId.generate()
        val errorMessage = "Player does not exist"

        // Fake CreateGameUseCase that always fails
        val fakeCreateGameUseCase = FakeCreateGameUseCase(
            result = Result.failure(Exception(errorMessage))
        )

        val gameCreator = ChessContextGameCreator(fakeCreateGameUseCase)

        // When
        val result = gameCreator.createGame(whitePlayerId, blackPlayerId)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<Exception>()
        result.exceptionOrNull()!!.message shouldBe errorMessage
    }

    test("createGame should extract GameId from created Game") {
        // Given
        val whitePlayerId = PlayerId.generate()
        val blackPlayerId = PlayerId.generate()
        val gameId1 = GameId.generate()
        val gameId2 = GameId.generate()

        // Fake CreateGameUseCase with stateful behavior
        val fakeCreateGameUseCase = FakeCreateGameUseCase()
        val gameCreator = ChessContextGameCreator(fakeCreateGameUseCase)

        // When - first call with gameId1
        fakeCreateGameUseCase.result = Result.success(Game(
            id = gameId1,
            whitePlayer = whitePlayerId,
            blackPlayer = blackPlayerId
        ))
        val result1 = gameCreator.createGame(whitePlayerId, blackPlayerId)

        // Then - should return first GameId
        result1.getOrNull() shouldBe gameId1

        // When - second call with gameId2
        fakeCreateGameUseCase.result = Result.success(Game(
            id = gameId2,
            whitePlayer = blackPlayerId,
            blackPlayer = whitePlayerId
        ))
        val result2 = gameCreator.createGame(blackPlayerId, whitePlayerId)

        // Then - should return second GameId
        result2.getOrNull() shouldBe gameId2
    }

    test("createGame should propagate exception message from CreateGameUseCase") {
        // Given
        val whitePlayerId = PlayerId.generate()
        val blackPlayerId = PlayerId.generate()
        val specificError = "White player abc123 does not exist"

        // Fake CreateGameUseCase with specific error
        val fakeCreateGameUseCase = FakeCreateGameUseCase(
            result = Result.failure(Exception(specificError))
        )

        val gameCreator = ChessContextGameCreator(fakeCreateGameUseCase)

        // When
        val result = gameCreator.createGame(whitePlayerId, blackPlayerId)

        // Then - error message should be preserved
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe specificError
    }
})
