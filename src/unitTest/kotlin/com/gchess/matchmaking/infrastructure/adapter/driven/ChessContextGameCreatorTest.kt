package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.chess.application.usecase.CreateGameUseCase
import com.gchess.chess.domain.model.ChessPosition
import com.gchess.chess.domain.model.Game
import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.PlayerSide
import com.gchess.chess.domain.model.Position
import com.gchess.matchmaking.application.usecase.MatchmakingResult
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId
import com.gchess.shared.domain.model.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk

class ChessContextGameCreatorTest : FunSpec({

    test("createGame should return GameId on success") {
        // Given
        val whitePlayer = Player(
            id = PlayerId.generate(),
            userId = UserId.generate(),
            side = PlayerSide.WHITE
        )
        val blackPlayer = Player(
            id = PlayerId.generate(),
            userId = UserId.generate(),
            side = PlayerSide.BLACK
        )
        val expectedGame = Game(
            id=GameId.generate(),
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer)
        val createGameUseCase = mockk<CreateGameUseCase>()
        coEvery { createGameUseCase.execute(whitePlayer, blackPlayer) } returns Result.success(expectedGame)

        val gameCreator = ChessContextGameCreator(createGameUseCase)

        // When
        val result = gameCreator.createGame(whitePlayer, blackPlayer)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull() shouldBe expectedGame.id
    }

    test("createGame should return failure when CreateGameUseCase fails") {
        // Given
        val whitePlayer = Player(
            id = PlayerId.generate(),
            userId = UserId.generate(),
            side = PlayerSide.WHITE
        )
        val blackPlayer = Player(
            id = PlayerId.generate(),
            userId = UserId.generate(),
            side = PlayerSide.BLACK
        )
        val errorMessage = "Player does not exist"
        val createGameUseCase = mockk<CreateGameUseCase>()
        coEvery { createGameUseCase.execute(whitePlayer, blackPlayer) } returns Result.failure(Exception(errorMessage))
        val gameCreator = ChessContextGameCreator(createGameUseCase)

        // When
        val result = gameCreator.createGame(whitePlayer, blackPlayer)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<Exception>()
        result.exceptionOrNull()!!.message shouldBe errorMessage
    }

    test("createGame should extract GameId from created Game") {
        // Given
        val whitePlayer = Player(
            id = PlayerId.generate(),
            userId = UserId.generate(),
            side = PlayerSide.WHITE
        )
        val blackPlayer = Player(
            id = PlayerId.generate(),
            userId = UserId.generate(),
            side = PlayerSide.BLACK
        )
        val game1 = Game(
            id = GameId.generate(),
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = ChessPosition(),
            currentSide = PlayerSide.WHITE
        )
        val game2 = Game(
            id = GameId.generate(),
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = ChessPosition(),
            currentSide = PlayerSide.BLACK
        )
        val createGameUseCase = mockk<CreateGameUseCase>()
        coEvery { createGameUseCase.execute(whitePlayer, blackPlayer) } returns Result.success(game1) andThen Result.success(game2)
        val gameCreator = ChessContextGameCreator(createGameUseCase)

        // When - first call with gameId1
        val result1 = gameCreator.createGame(whitePlayer, blackPlayer)

        // Then - should return first GameId
        result1.getOrNull() shouldBe game1.id

        // When - second call with gameId2
        val result2 = gameCreator.createGame(whitePlayer, blackPlayer)

        // Then - should return second GameId
        result2.getOrNull() shouldBe game2.id
    }

    test("createGame should propagate exception message from CreateGameUseCase") {
        // Given
        val whitePlayer = Player(
            id = PlayerId.generate(),
            userId = UserId.generate(),
            side = PlayerSide.WHITE
        )
        val blackPlayer = Player(
            id = PlayerId.generate(),
            userId = UserId.generate(),
            side = PlayerSide.BLACK
        )
        val specificError = "White player abc123 does not exist"

        val createGameUseCase = mockk<CreateGameUseCase>()
        coEvery { createGameUseCase.execute(whitePlayer,blackPlayer) } returns Result.failure(Exception(specificError))

        val gameCreator = ChessContextGameCreator(createGameUseCase)

        // When
        val result = gameCreator.createGame(whitePlayer, blackPlayer)

        // Then - error message should be preserved
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe specificError
    }
})
