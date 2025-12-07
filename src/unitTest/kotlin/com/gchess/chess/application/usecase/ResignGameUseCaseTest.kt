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
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.PlayerSide
import com.gchess.shared.domain.model.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class ResignGameUseCaseTest : FunSpec({

    test("execute should successfully resign from a game") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ResignGameUseCase(gameRepository, gameEventNotifier)

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

        val resignedGame = game.copy(status = GameStatus.RESIGNED)

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game
        coEvery { gameRepository.save(match { it.status == GameStatus.RESIGNED }) } returns resignedGame
        coEvery { gameEventNotifier.notifyGameResigned(resignedGame, whitePlayer) } returns Unit

        // When
        val result = useCase.execute(gameId, whitePlayer)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull()!!.status shouldBe GameStatus.RESIGNED

        // Verify interactions
        coVerify { gameRepository.findById(gameId) }
        coVerify { gameRepository.save(match { it.status == GameStatus.RESIGNED }) }
        coVerify { gameEventNotifier.notifyGameResigned(resignedGame, whitePlayer) }
    }

    test("execute should fail when game not found") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ResignGameUseCase(gameRepository, gameEventNotifier)

        val gameId = GameId.generate()
        val player = Player.create(UserId.generate(), PlayerSide.WHITE)

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns null

        // When
        val result = useCase.execute(gameId, player)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "Game not found"

        // Verify - no save or notification on failure
        coVerify { gameRepository.findById(gameId) }
        coVerify(exactly = 0) { gameRepository.save(any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyGameResigned(any(), any()) }
    }

    test("execute should fail when player is not a participant") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ResignGameUseCase(gameRepository, gameEventNotifier)

        val gameId = GameId.generate()
        val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
        val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)
        val nonParticipant = Player.create(UserId.generate(), PlayerSide.WHITE) // Different player
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

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game

        // When
        val result = useCase.execute(gameId, nonParticipant)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "You are not a participant in this game"

        // Verify - no save or notification
        coVerify { gameRepository.findById(gameId) }
        coVerify(exactly = 0) { gameRepository.save(any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyGameResigned(any(), any()) }
    }

    test("execute should fail when game is already finished") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ResignGameUseCase(gameRepository, gameEventNotifier)

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

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game

        // When
        val result = useCase.execute(gameId, whitePlayer)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "Game is already finished"

        // Verify - no save or notification
        coVerify { gameRepository.findById(gameId) }
        coVerify(exactly = 0) { gameRepository.save(any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyGameResigned(any(), any()) }
    }

    test("execute should allow black player to resign") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ResignGameUseCase(gameRepository, gameEventNotifier)

        val gameId = GameId.generate()
        val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
        val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)
        val position = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2".toChessPosition()

        val game = Game(
            id = gameId,
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = position,
            currentSide = PlayerSide.WHITE,
            status = GameStatus.IN_PROGRESS,
            moveHistory = emptyList()
        )

        val resignedGame = game.copy(status = GameStatus.RESIGNED)

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game
        coEvery { gameRepository.save(match { it.status == GameStatus.RESIGNED }) } returns resignedGame
        coEvery { gameEventNotifier.notifyGameResigned(resignedGame, blackPlayer) } returns Unit

        // When - black player resigns
        val result = useCase.execute(gameId, blackPlayer)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull()!!.status shouldBe GameStatus.RESIGNED

        // Verify interactions
        coVerify { gameRepository.findById(gameId) }
        coVerify { gameRepository.save(match { it.status == GameStatus.RESIGNED }) }
        coVerify { gameEventNotifier.notifyGameResigned(resignedGame, blackPlayer) }
    }
})