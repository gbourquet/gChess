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
package com.gchess.chess.domain.model

import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.PlayerSide
import com.gchess.shared.domain.model.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

class GameClockTest : FunSpec({

    val t0 = Instant.fromEpochMilliseconds(0L)
    val t3s = Instant.fromEpochMilliseconds(3_000L)   // 3 secondes plus tard
    val t5s = Instant.fromEpochMilliseconds(5_000L)   // 5 secondes plus tard
    val t8s = Instant.fromEpochMilliseconds(8_000L)   // 8 secondes plus tard

    val timeControl = TimeControl(totalTimeSeconds = 300, incrementSeconds = 3)
    val whitePlayer = Player.create(UserId.generate(), PlayerSide.WHITE)
    val blackPlayer = Player.create(UserId.generate(), PlayerSide.BLACK)

    val move1 = Move(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"))
    val move2 = Move(Position.fromAlgebraic("e7"), Position.fromAlgebraic("e5"))

    fun baseGame(
        moveHistory: List<Move> = emptyList(),
        currentSide: PlayerSide = PlayerSide.WHITE,
        lastMoveAt: Instant? = t0,
        whiteTimeRemainingMs: Long = 300_000L,
        blackTimeRemainingMs: Long = 300_000L
    ) = Game(
        id = GameId.generate(),
        whitePlayer = whitePlayer,
        blackPlayer = blackPlayer,
        currentSide = currentSide,
        timeControl = timeControl,
        whiteTimeRemainingMs = whiteTimeRemainingMs,
        blackTimeRemainingMs = blackTimeRemainingMs,
        lastMoveAt = lastMoveAt,
        moveHistory = moveHistory
    )

    // ===== Premier coup de chaque joueur : pas de décompte =====

    test("le 1er coup de blanc (lastMoveAt null) ne décompte pas et met à jour lastMoveAt") {
        val game = baseGame(moveHistory = emptyList(), currentSide = PlayerSide.WHITE, lastMoveAt = null)

        val result = game.applyClockTick(t3s)

        result.whiteTimeRemainingMs shouldBe 300_000L
        result.blackTimeRemainingMs shouldBe 300_000L
        result.lastMoveAt shouldBe t3s
    }

    test("le 1er coup de noir (moveHistory.size == 1) ne décompte pas et met à jour lastMoveAt") {
        // lastMoveAt est positionné après le 1er coup de blanc, mais c'est le 1er coup de noir
        val game = baseGame(
            moveHistory = listOf(move1),
            currentSide = PlayerSide.BLACK,
            lastMoveAt = t0
        )

        val result = game.applyClockTick(t3s)

        result.whiteTimeRemainingMs shouldBe 300_000L
        result.blackTimeRemainingMs shouldBe 300_000L
        result.lastMoveAt shouldBe t3s
    }

    // ===== Décompte actif à partir du 2ème coup de chaque joueur =====

    test("le 2ème coup de blanc (moveHistory.size == 2) décompte l'horloge de blanc et ajoute l'incrément") {
        // 3 secondes se sont écoulées depuis le dernier coup de noir
        val game = baseGame(
            moveHistory = listOf(move1, move2),
            currentSide = PlayerSide.WHITE,
            lastMoveAt = t0,
            whiteTimeRemainingMs = 300_000L
        )

        val result = game.applyClockTick(t3s)

        // 300_000 - 3_000 (élapsed) + 3_000 (incrément) = 300_000
        result.whiteTimeRemainingMs shouldBe 300_000L
        result.blackTimeRemainingMs shouldBe 300_000L
        result.lastMoveAt shouldBe t3s
    }

    test("le 2ème coup de blanc déduit correctement quand l'incrément ne compense pas") {
        // 5 secondes écoulées, incrément 3s
        val game = baseGame(
            moveHistory = listOf(move1, move2),
            currentSide = PlayerSide.WHITE,
            lastMoveAt = t0,
            whiteTimeRemainingMs = 300_000L
        )

        val result = game.applyClockTick(t5s)

        // 300_000 - 5_000 + 3_000 = 298_000
        result.whiteTimeRemainingMs shouldBe 298_000L
        result.blackTimeRemainingMs shouldBe 300_000L
        result.lastMoveAt shouldBe t5s
    }

    test("le 2ème coup de noir (moveHistory.size == 3) décompte l'horloge de noir") {
        val game = baseGame(
            moveHistory = listOf(move1, move2, move1),
            currentSide = PlayerSide.BLACK,
            lastMoveAt = t0,
            blackTimeRemainingMs = 295_000L
        )

        val result = game.applyClockTick(t5s)

        // 295_000 - 5_000 + 3_000 = 293_000
        result.blackTimeRemainingMs shouldBe 293_000L
        result.whiteTimeRemainingMs shouldBe 300_000L
        result.lastMoveAt shouldBe t5s
    }

    test("l'horloge de l'adversaire n'est jamais modifiée lors d'un coup") {
        val game = baseGame(
            moveHistory = listOf(move1, move2),
            currentSide = PlayerSide.WHITE,
            lastMoveAt = t0,
            whiteTimeRemainingMs = 280_000L,
            blackTimeRemainingMs = 275_000L
        )

        val result = game.applyClockTick(t8s)

        // Blanc joue : seul le temps de blanc change
        result.whiteTimeRemainingMs shouldBe 275_000L   // 280_000 - 8_000 + 3_000
        result.blackTimeRemainingMs shouldBe 275_000L   // inchangé
    }

    // ===== Cas particuliers =====

    test("jeu sans contrôle du temps : applyClockTick ne décompte pas") {
        val game = Game(
            id = GameId.generate(),
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            currentSide = PlayerSide.WHITE,
            timeControl = null,
            moveHistory = listOf(move1, move2),
            lastMoveAt = t0
        )

        val result = game.applyClockTick(t5s)

        result.whiteTimeRemainingMs shouldBe null
        result.blackTimeRemainingMs shouldBe null
        result.lastMoveAt shouldBe t5s
    }

    test("jeu en temps illimité (totalTimeSeconds == 0) : applyClockTick ne décompte pas") {
        val game = Game(
            id = GameId.generate(),
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            currentSide = PlayerSide.WHITE,
            timeControl = TimeControl.UNLIMITED,
            moveHistory = listOf(move1, move2),
            lastMoveAt = t0
        )

        val result = game.applyClockTick(t5s)

        result.whiteTimeRemainingMs shouldBe null
        result.lastMoveAt shouldBe t5s
    }
})
