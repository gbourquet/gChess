@file:OptIn(kotlin.time.ExperimentalTime::class)

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
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kotlin.time.Instant

class ClaimTimeoutUseCaseTest : FunSpec({

    val t0 = Instant.fromEpochMilliseconds(0L)

    val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
    val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)

    val move1 = Move(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"))
    val move2 = Move(Position.fromAlgebraic("e7"), Position.fromAlgebraic("e5"))
    // moveHistory avec ≥ 2 coups = horloges actives
    val twoMoves = listOf(move1, move2)

    val timeControl = TimeControl(totalTimeSeconds = 300, incrementSeconds = 3)

    fun gameInProgress(
        currentSide: PlayerSide = PlayerSide.WHITE,
        moveHistory: List<Move> = twoMoves,
        lastMoveAt: Instant? = t0,
        whiteTimeRemainingMs: Long = 300_000L,
        blackTimeRemainingMs: Long = 300_000L,
        tc: TimeControl? = timeControl
    ) = Game(
        id = GameId.generate(),
        whitePlayer = whitePlayer,
        blackPlayer = blackPlayer,
        currentSide = currentSide,
        status = GameStatus.IN_PROGRESS,
        timeControl = tc,
        whiteTimeRemainingMs = whiteTimeRemainingMs,
        blackTimeRemainingMs = blackTimeRemainingMs,
        lastMoveAt = lastMoveAt,
        moveHistory = moveHistory
    )

    // ===== TimeoutRejected =====

    test("retourne TimeoutRejected si l'adversaire a encore du temps") {
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ClaimTimeoutUseCase(gameRepository, gameEventNotifier)

        // Blanc doit jouer, il lui reste 10s. 3s se sont écoulées → remainingMs = 7_000 > 0
        val game = gameInProgress(
            currentSide = PlayerSide.WHITE,
            whiteTimeRemainingMs = 10_000L,
            lastMoveAt = t0
        )
        val now = Instant.fromEpochMilliseconds(3_000L)

        coEvery { gameRepository.findById(any()) } returns game

        // Noir réclame le timeout de blanc
        val result = useCase.execute(game.id, blackPlayer, now)

        result.shouldBeInstanceOf<ClaimTimeoutResult.TimeoutRejected>()
        (result as ClaimTimeoutResult.TimeoutRejected).remainingMs shouldBe 7_000L

        coVerify(exactly = 0) { gameRepository.save(any()) }
        coVerify(exactly = 0) { gameEventNotifier.notifyTimeout(any(), any()) }
    }

    // ===== TimeoutConfirmed =====

    test("retourne TimeoutConfirmed et sauvegarde le jeu si le temps est écoulé") {
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ClaimTimeoutUseCase(gameRepository, gameEventNotifier)

        // Blanc doit jouer, il lui reste 5s. 8s se sont écoulées → remainingMs = -3_000 ≤ 0
        val game = gameInProgress(
            currentSide = PlayerSide.WHITE,
            whiteTimeRemainingMs = 5_000L,
            lastMoveAt = t0
        )
        val now = Instant.fromEpochMilliseconds(8_000L)

        coEvery { gameRepository.findById(any()) } returns game
        coEvery { gameRepository.save(any()) } returns mockk()
        coEvery { gameEventNotifier.notifyTimeout(any(), any()) } returns Unit

        val result = useCase.execute(game.id, blackPlayer, now)

        result.shouldBeInstanceOf<ClaimTimeoutResult.TimeoutConfirmed>()
        (result as ClaimTimeoutResult.TimeoutConfirmed).loserPlayerId shouldBe whitePlayer.id.toString()

        coVerify { gameRepository.save(match { it.status == GameStatus.TIMEOUT }) }
        coVerify { gameEventNotifier.notifyTimeout(match { it.status == GameStatus.TIMEOUT }, whitePlayer) }
    }

    test("TimeoutConfirmed : c'est le joueur noir qui flag") {
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ClaimTimeoutUseCase(gameRepository, gameEventNotifier)

        val game = gameInProgress(
            currentSide = PlayerSide.BLACK,
            blackTimeRemainingMs = 2_000L,
            lastMoveAt = t0,
            moveHistory = listOf(move1, move2, move1) // 3 coups
        )
        val now = Instant.fromEpochMilliseconds(6_000L) // 6s écoulées, 2s restantes → flag

        coEvery { gameRepository.findById(any()) } returns game
        coEvery { gameRepository.save(any()) } returns mockk()
        coEvery { gameEventNotifier.notifyTimeout(any(), any()) } returns Unit

        val result = useCase.execute(game.id, whitePlayer, now)

        result.shouldBeInstanceOf<ClaimTimeoutResult.TimeoutConfirmed>()
        (result as ClaimTimeoutResult.TimeoutConfirmed).loserPlayerId shouldBe blackPlayer.id.toString()

        coVerify { gameEventNotifier.notifyTimeout(any(), blackPlayer) }
    }

    // ===== ClaimError =====

    test("retourne ClaimError si le jeu n'existe pas") {
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ClaimTimeoutUseCase(gameRepository, gameEventNotifier)

        coEvery { gameRepository.findById(any()) } returns null

        val result = useCase.execute(GameId.generate(), blackPlayer, t0)

        result.shouldBeInstanceOf<ClaimTimeoutResult.ClaimError>()
        (result as ClaimTimeoutResult.ClaimError).message shouldBe "Game not found"
    }

    test("retourne ClaimError si le jeu est déjà terminé") {
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ClaimTimeoutUseCase(gameRepository, gameEventNotifier)

        val game = gameInProgress().copy(status = GameStatus.CHECKMATE)
        coEvery { gameRepository.findById(any()) } returns game

        val result = useCase.execute(game.id, blackPlayer, t0)

        result.shouldBeInstanceOf<ClaimTimeoutResult.ClaimError>()
        (result as ClaimTimeoutResult.ClaimError).message shouldBe "Game is already finished"
    }

    test("retourne ClaimError si c'est le tour du réclamant (il ne peut pas se flaguer lui-même)") {
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ClaimTimeoutUseCase(gameRepository, gameEventNotifier)

        // C'est le tour de blanc, blanc essaie de réclamer
        val game = gameInProgress(currentSide = PlayerSide.WHITE)
        coEvery { gameRepository.findById(any()) } returns game

        val result = useCase.execute(game.id, whitePlayer, t0)

        result.shouldBeInstanceOf<ClaimTimeoutResult.ClaimError>()
        (result as ClaimTimeoutResult.ClaimError).message shouldBe "Cannot claim timeout on your own turn"
    }

    test("retourne ClaimError si le jeu n'a pas de contrôle du temps") {
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ClaimTimeoutUseCase(gameRepository, gameEventNotifier)

        val game = gameInProgress(tc = null)
        coEvery { gameRepository.findById(any()) } returns game

        val result = useCase.execute(game.id, blackPlayer, t0)

        result.shouldBeInstanceOf<ClaimTimeoutResult.ClaimError>()
        (result as ClaimTimeoutResult.ClaimError).message shouldBe "No active time control"
    }

    test("retourne ClaimError si le contrôle du temps est illimité (totalTimeSeconds == 0)") {
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ClaimTimeoutUseCase(gameRepository, gameEventNotifier)

        val game = gameInProgress(tc = TimeControl.UNLIMITED)
        coEvery { gameRepository.findById(any()) } returns game

        val result = useCase.execute(game.id, blackPlayer, t0)

        result.shouldBeInstanceOf<ClaimTimeoutResult.ClaimError>()
        (result as ClaimTimeoutResult.ClaimError).message shouldBe "No active time control"
    }

    test("retourne ClaimError si les horloges n'ont pas encore démarré (moins de 2 coups joués)") {
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ClaimTimeoutUseCase(gameRepository, gameEventNotifier)

        // Seulement le 1er coup de blanc joué : horloge de noir pas encore démarrée
        val game = gameInProgress(
            currentSide = PlayerSide.BLACK,
            moveHistory = listOf(move1),
            lastMoveAt = t0
        )
        coEvery { gameRepository.findById(any()) } returns game

        val result = useCase.execute(game.id, whitePlayer, Instant.fromEpochMilliseconds(400_000L))

        result.shouldBeInstanceOf<ClaimTimeoutResult.ClaimError>()
        (result as ClaimTimeoutResult.ClaimError).message shouldBe "No active time control"
    }

    test("retourne ClaimError si lastMoveAt est null (aucun coup joué)") {
        val gameRepository = mockk<GameRepository>()
        val gameEventNotifier = mockk<GameEventNotifier>()
        val useCase = ClaimTimeoutUseCase(gameRepository, gameEventNotifier)

        val game = gameInProgress(
            currentSide = PlayerSide.WHITE,
            moveHistory = emptyList(),
            lastMoveAt = null
        )
        coEvery { gameRepository.findById(any()) } returns game

        val result = useCase.execute(game.id, blackPlayer, Instant.fromEpochMilliseconds(400_000L))

        result.shouldBeInstanceOf<ClaimTimeoutResult.ClaimError>()
        (result as ClaimTimeoutResult.ClaimError).message shouldBe "No active time control"
    }
})
