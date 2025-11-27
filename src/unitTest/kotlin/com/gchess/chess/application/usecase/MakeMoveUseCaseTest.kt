/*
 * Copyright (c) 2024-2025 Guillaume Bourquet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.gchess.chess.application.usecase

import com.gchess.chess.domain.model.*
import com.gchess.chess.domain.port.GameEventNotifier
import com.gchess.chess.domain.port.GameRepository
import com.gchess.chess.domain.service.ChessRules
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.PlayerSide
import com.gchess.shared.domain.model.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class MakeMoveUseCaseTest : FunSpec({

    test("execute should successfully make a valid move and notify") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val chessRules = mockk<ChessRules>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = MakeMoveUseCase(gameRepository, chessRules, gameEventNotifier)

        val gameId = GameId.generate()
        val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
        val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)
        val initialPosition = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()

        val game = Game(
            id = gameId,
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = initialPosition,
            currentSide = PlayerSide.WHITE,
            status = GameStatus.IN_PROGRESS,
            moveHistory = emptyList()
        )

        val move = Move(
            from = Position.fromAlgebraic("e2"),
            to = Position.fromAlgebraic("e4"),
            promotion = null
        )

        val expectedBoard = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1".toChessPosition()
        val gameAfterMove = game.copy(
            board = expectedBoard,
            currentSide = PlayerSide.BLACK,
            moveHistory = listOf(move)
        )

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game
        every { chessRules.isMoveLegal(initialPosition, move) } returns true
        every { chessRules.isCheckmate(any()) } returns false
        every { chessRules.isStalemate(any()) } returns false
        every { chessRules.isFiftyMoveRule(any()) } returns false
        every { chessRules.isInsufficientMaterial(any()) } returns false
        coEvery { gameRepository.save(any()) } returns gameAfterMove
        coEvery { gameEventNotifier.notifyMoveExecuted(any(), move) } returns Unit

        // When
        val result = useCase.execute(gameId, whitePlayer, move)

        // Then
        result.isSuccess shouldBe true

        // Verify interactions
        coVerify { gameRepository.findById(gameId) }
        verify { chessRules.isMoveLegal(initialPosition, move) }
        coVerify { gameRepository.save(any()) }
        coVerify { gameEventNotifier.notifyMoveExecuted(any(), move) }
        coVerify(exactly = 0) { gameEventNotifier.notifyMoveRejected(any(), any()) }
    }

    test("execute should fail when game not found") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val chessRules = mockk<ChessRules>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = MakeMoveUseCase(gameRepository, chessRules, gameEventNotifier)

        val gameId = GameId.generate()
        val player = Player.create(UserId.generate(), PlayerSide.WHITE)
        val move = Move(
            from = Position.fromAlgebraic("e2"),
            to = Position.fromAlgebraic("e4"),
            promotion = null
        )

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns null

        // When
        val result = useCase.execute(gameId, player, move)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "Game not found"

        // Verify - no notifier calls on failure
        coVerify { gameRepository.findById(gameId) }
        coVerify(exactly = 0) { gameRepository.save(any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyMoveExecuted(any(), any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyMoveRejected(any(), any()) }
    }

    test("execute should fail when not player's turn") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val chessRules = mockk<ChessRules>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = MakeMoveUseCase(gameRepository, chessRules, gameEventNotifier)

        val gameId = GameId.generate()
        val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
        val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)
        val initialPosition = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()

        val game = Game(
            id = gameId,
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = initialPosition,
            currentSide = PlayerSide.WHITE, // White's turn
            status = GameStatus.IN_PROGRESS,
            moveHistory = emptyList()
        )

        val move = Move(
            from = Position.fromAlgebraic("e7"),
            to = Position.fromAlgebraic("e5"),
            promotion = null
        )

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game

        // When - black player tries to move on white's turn
        val result = useCase.execute(gameId, blackPlayer, move)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "It's not your turn"

        // Verify - no move validation or notifications
        coVerify { gameRepository.findById(gameId) }
        verify(exactly = 0) { chessRules.isMoveLegal(any(), any()) }
        coVerify(exactly = 0) { gameRepository.save(any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyMoveExecuted(any(), any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyMoveRejected(any(), any()) }
    }

    test("execute should fail when game is already finished") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val chessRules = mockk<ChessRules>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = MakeMoveUseCase(gameRepository, chessRules, gameEventNotifier)

        val gameId = GameId.generate()
        val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
        val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)
        val position = "8/8/8/8/8/5k2/8/4K3 w - - 0 1".toChessPosition()

        val game = Game(
            id = gameId,
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = position,
            currentSide = PlayerSide.WHITE,
            status = GameStatus.CHECKMATE, // Game already finished
            moveHistory = emptyList()
        )

        val move = Move(
            from = Position.fromAlgebraic("e1"),
            to = Position.fromAlgebraic("e2"),
            promotion = null
        )

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game

        // When
        val result = useCase.execute(gameId, whitePlayer, move)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "Game is already finished"

        // Verify - no move validation or notifications
        coVerify { gameRepository.findById(gameId) }
        verify(exactly = 0) { chessRules.isMoveLegal(any(), any()) }
        coVerify(exactly = 0) { gameRepository.save(any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyMoveExecuted(any(), any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyMoveRejected(any(), any()) }
    }

    test("execute should fail when move is illegal") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val chessRules = mockk<ChessRules>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = MakeMoveUseCase(gameRepository, chessRules, gameEventNotifier)

        val gameId = GameId.generate()
        val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
        val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)
        val initialPosition = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()

        val game = Game(
            id = gameId,
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = initialPosition,
            currentSide = PlayerSide.WHITE,
            status = GameStatus.IN_PROGRESS,
            moveHistory = emptyList()
        )

        val illegalMove = Move(
            from = Position.fromAlgebraic("e2"),
            to = Position.fromAlgebraic("e5"), // Illegal - pawn can't move 3 squares
            promotion = null
        )

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game
        every { chessRules.isMoveLegal(initialPosition, illegalMove) } returns false

        // When
        val result = useCase.execute(gameId, whitePlayer, illegalMove)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "Invalid move"

        // Verify - move validation called but no save or notification
        coVerify { gameRepository.findById(gameId) }
        verify { chessRules.isMoveLegal(initialPosition, illegalMove) }
        coVerify(exactly = 0) { gameRepository.save(any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyMoveExecuted(any(), any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyMoveRejected(any(), any()) }
    }

    test("execute should detect checkmate and update game status") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val chessRules = mockk<ChessRules>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = MakeMoveUseCase(gameRepository, chessRules, gameEventNotifier)

        val gameId = GameId.generate()
        val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
        val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)

        // Fool's mate setup - one move before checkmate
        val positionBeforeCheckmate = "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2".toChessPosition()

        val game = Game(
            id = gameId,
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = positionBeforeCheckmate,
            currentSide = PlayerSide.BLACK,
            status = GameStatus.IN_PROGRESS,
            moveHistory = emptyList()
        )

        val checkmateMove = Move(
            from = Position.fromAlgebraic("d8"),
            to = Position.fromAlgebraic("h4"), // Checkmate!
            promotion = null
        )

        "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3".toChessPosition()

        val savedGameSlot = slot<Game>()

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game
        every { chessRules.isMoveLegal(any(), checkmateMove) } returns true
        every { chessRules.isCheckmate(any()) } returns true
        every { chessRules.isStalemate(any()) } returns false
        every { chessRules.isFiftyMoveRule(any()) } returns false
        every { chessRules.isInsufficientMaterial(any()) } returns false
        coEvery { gameRepository.save(capture(savedGameSlot)) } answers { savedGameSlot.captured }
        coEvery { gameEventNotifier.notifyMoveExecuted(any(), checkmateMove) } returns Unit

        // When
        val result = useCase.execute(gameId, blackPlayer, checkmateMove)

        // Then
        result.isSuccess shouldBe true
        val updatedGame = result.getOrNull()!!
        updatedGame.status shouldBe GameStatus.CHECKMATE

        // Verify interactions
        coVerify { gameRepository.findById(gameId) }
        verify { chessRules.isMoveLegal(positionBeforeCheckmate, checkmateMove) }
        verify { chessRules.isCheckmate(any()) }
        coVerify { gameRepository.save(match { it.status == GameStatus.CHECKMATE }) }
        coVerify { gameEventNotifier.notifyMoveExecuted(match { it.status == GameStatus.CHECKMATE }, checkmateMove) }
    }

    test("execute should detect stalemate and update game status") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val chessRules = mockk<ChessRules>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = MakeMoveUseCase(gameRepository, chessRules, gameEventNotifier)

        val gameId = GameId.generate()
        val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
        val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)

        // Position before stalemate
        val positionBeforeStalemate = "7k/5Q2/6K1/8/8/8/8/8 w - - 0 1".toChessPosition()

        val game = Game(
            id = gameId,
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = positionBeforeStalemate,
            currentSide = PlayerSide.WHITE,
            status = GameStatus.IN_PROGRESS,
            moveHistory = emptyList()
        )

        val stalemateMove = Move(
            from = Position.fromAlgebraic("f7"),
            to = Position.fromAlgebraic("f6"), // Stalemate!
            promotion = null
        )

        "7k/8/5QK1/8/8/8/8/8 b - - 1 1".toChessPosition()

        val savedGameSlot = slot<Game>()

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game
        every { chessRules.isMoveLegal(any(), stalemateMove) } returns true
        every { chessRules.isCheckmate(any()) } returns false
        every { chessRules.isStalemate(any()) } returns true
        every { chessRules.isFiftyMoveRule(any()) } returns false
        every { chessRules.isInsufficientMaterial(any()) } returns false
        coEvery { gameRepository.save(capture(savedGameSlot)) } answers { savedGameSlot.captured }
        coEvery { gameEventNotifier.notifyMoveExecuted(any(), stalemateMove) } returns Unit

        // When
        val result = useCase.execute(gameId, whitePlayer, stalemateMove)

        // Then
        result.isSuccess shouldBe true
        val updatedGame = result.getOrNull()!!
        updatedGame.status shouldBe GameStatus.STALEMATE

        // Verify interactions
        coVerify { gameRepository.findById(gameId) }
        verify { chessRules.isMoveLegal(positionBeforeStalemate, stalemateMove) }
        verify { chessRules.isCheckmate(any()) }
        verify { chessRules.isStalemate(any()) }
        coVerify { gameRepository.save(match { it.status == GameStatus.STALEMATE }) }
        coVerify { gameEventNotifier.notifyMoveExecuted(match { it.status == GameStatus.STALEMATE }, stalemateMove) }
    }
})
