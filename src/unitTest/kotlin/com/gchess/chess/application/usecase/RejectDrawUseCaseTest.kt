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

class RejectDrawUseCaseTest : FunSpec({

    test("execute should successfully reject a draw offer") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = RejectDrawUseCase(gameRepository, gameEventNotifier)

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
            moveHistory = emptyList(),
            drawOfferedBy = PlayerSide.WHITE // White offered a draw
        )

        val gameWithRejectedOffer = game.copy(drawOfferedBy = null)

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game
        coEvery { gameRepository.save(match { it.drawOfferedBy == null }) } returns gameWithRejectedOffer
        coEvery { gameEventNotifier.notifyDrawRejected(gameWithRejectedOffer, blackPlayer) } returns Unit

        // When - black rejects white's draw offer
        val result = useCase.execute(gameId, blackPlayer)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull()!!.drawOfferedBy shouldBe null
        result.getOrNull()!!.status shouldBe GameStatus.IN_PROGRESS

        // Verify interactions
        coVerify { gameRepository.findById(gameId) }
        coVerify { gameRepository.save(match { it.drawOfferedBy == null }) }
        coVerify { gameEventNotifier.notifyDrawRejected(gameWithRejectedOffer, blackPlayer) }
    }

    test("execute should fail when game not found") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = RejectDrawUseCase(gameRepository, gameEventNotifier)

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
        coVerify(exactly = 0) { gameEventNotifier.notifyDrawRejected(any(), any()) }
    }

    test("execute should fail when player is not a participant") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = RejectDrawUseCase(gameRepository, gameEventNotifier)

        val gameId = GameId.generate()
        val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
        val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)
        val nonParticipant = Player.create(UserId.generate(), PlayerSide.WHITE) // Different player
        val position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()

        val game = Game(
            id = gameId,
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = position,
            currentSide = PlayerSide.WHITE,
            status = GameStatus.IN_PROGRESS,
            moveHistory = emptyList(),
            drawOfferedBy = PlayerSide.WHITE
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
        coVerify(exactly = 0) { gameEventNotifier.notifyDrawRejected(any(), any()) }
    }

    test("execute should fail when game is already finished") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = RejectDrawUseCase(gameRepository, gameEventNotifier)

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
            status = GameStatus.DRAW, // Game already finished
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
        coVerify(exactly = 0) { gameEventNotifier.notifyDrawRejected(any(), any()) }
    }

    test("execute should fail when no draw offer exists") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = RejectDrawUseCase(gameRepository, gameEventNotifier)

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
            moveHistory = emptyList(),
            drawOfferedBy = null // No draw offer
        )

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game

        // When
        val result = useCase.execute(gameId, blackPlayer)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "No draw offer to reject"

        // Verify - no save or notification
        coVerify { gameRepository.findById(gameId) }
        coVerify(exactly = 0) { gameRepository.save(any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyDrawRejected(any(), any()) }
    }

    test("execute should fail when player tries to reject their own draw offer") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = RejectDrawUseCase(gameRepository, gameEventNotifier)

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
            moveHistory = emptyList(),
            drawOfferedBy = PlayerSide.WHITE // White offered a draw
        )

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game

        // When - white tries to reject their own draw offer
        val result = useCase.execute(gameId, whitePlayer)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "You cannot reject your own draw offer"

        // Verify - no save or notification
        coVerify { gameRepository.findById(gameId) }
        coVerify(exactly = 0) { gameRepository.save(any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyDrawRejected(any(), any()) }
    }

    test("execute should allow white to reject black's draw offer") {
        // Given
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = RejectDrawUseCase(gameRepository, gameEventNotifier)

        val gameId = GameId.generate()
        val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
        val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)
        val position = "rnbqkbnr/ppp2ppp/8/3pp3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3".toChessPosition()

        val game = Game(
            id = gameId,
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = position,
            currentSide = PlayerSide.WHITE,
            status = GameStatus.IN_PROGRESS,
            moveHistory = emptyList(),
            drawOfferedBy = PlayerSide.BLACK // Black offered a draw
        )

        val gameWithRejectedOffer = game.copy(drawOfferedBy = null)

        // Mock behavior
        coEvery { gameRepository.findById(gameId) } returns game
        coEvery { gameRepository.save(match { it.drawOfferedBy == null }) } returns gameWithRejectedOffer
        coEvery { gameEventNotifier.notifyDrawRejected(gameWithRejectedOffer, whitePlayer) } returns Unit

        // When - white rejects black's draw offer
        val result = useCase.execute(gameId, whitePlayer)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull()!!.drawOfferedBy shouldBe null
        result.getOrNull()!!.status shouldBe GameStatus.IN_PROGRESS

        // Verify interactions
        coVerify { gameRepository.findById(gameId) }
        coVerify { gameRepository.save(match { it.drawOfferedBy == null }) }
        coVerify { gameEventNotifier.notifyDrawRejected(gameWithRejectedOffer, whitePlayer) }
    }
})