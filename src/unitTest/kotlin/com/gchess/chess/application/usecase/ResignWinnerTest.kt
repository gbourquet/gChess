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
@file:OptIn(kotlin.time.ExperimentalTime::class)

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

class ResignWinnerTest : FunSpec({

    test("BUG REPRODUCER: when white player resigns, winnerSide should be BLACK") {
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

        val resignedGame = game.copy(status = GameStatus.RESIGNED, winnerSide = PlayerSide.BLACK)

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game
        coEvery { gameRepository.save(match { it.status == GameStatus.RESIGNED }) } returns resignedGame
        coEvery { gameEventNotifier.notifyGameResigned(resignedGame, whitePlayer) } returns Unit

        // When: White player resigns
        val result = useCase.execute(gameId, whitePlayer)

        // Then: winnerSide should be BLACK (opponent of resigning player)
        result.isSuccess shouldBe true
        result.getOrNull()!!.winnerSide shouldBe PlayerSide.BLACK
    }

    test("BUG REPRODUCER: when black player resigns, winnerSide should be WHITE") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ResignGameUseCase(gameRepository, gameEventNotifier)

        val gameId = GameId.generate()
        val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
        val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)
        val position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()

        val game = Game(
            id = gameId,
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = position,
            currentSide = PlayerSide.WHITE,
            status = GameStatus.IN_PROGRESS,
            moveHistory = emptyList()
        )

        val resignedGame = game.copy(status = GameStatus.RESIGNED, winnerSide = PlayerSide.WHITE)

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game
        coEvery { gameRepository.save(match { it.status == GameStatus.RESIGNED }) } returns resignedGame
        coEvery { gameEventNotifier.notifyGameResigned(resignedGame, blackPlayer) } returns Unit

        // When: Black player resigns
        val result = useCase.execute(gameId, blackPlayer)

        // Then: winnerSide should be WHITE (opponent of resigning player)
        result.isSuccess shouldBe true
        result.getOrNull()!!.winnerSide shouldBe PlayerSide.WHITE
    }
})
