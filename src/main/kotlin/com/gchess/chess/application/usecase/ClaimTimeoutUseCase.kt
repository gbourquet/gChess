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

import com.gchess.chess.domain.model.GameStatus
import com.gchess.chess.domain.port.GameEventNotifier
import com.gchess.chess.domain.port.GameRepository
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.PlayerSide
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Result of a timeout claim.
 */
sealed class ClaimTimeoutResult {
    /** The opponent truly ran out of time: game is saved as TIMEOUT and broadcast. */
    data class TimeoutConfirmed(val loserPlayerId: String) : ClaimTimeoutResult()

    /** The opponent still has time remaining: no state change. */
    data class TimeoutRejected(val remainingMs: Long) : ClaimTimeoutResult()

    /** The claim is invalid (game not found, no time control, wrong player, etc.). */
    data class ClaimError(val message: String) : ClaimTimeoutResult()
}

/**
 * Use case for claiming a timeout on behalf of the waiting player.
 *
 * The claimer must be the player who is NOT currently to move.
 * The server computes the opponent's remaining time using [Clock.System.now].
 * - If time has expired: game status is set to TIMEOUT, saved, and all participants notified.
 * - If time has NOT expired: the remaining milliseconds are returned to the claimer.
 */
class ClaimTimeoutUseCase(
    private val gameRepository: GameRepository,
    private val gameEventNotifier: GameEventNotifier
) {
    @OptIn(ExperimentalTime::class)
    suspend fun execute(gameId: GameId, claimer: Player, now: Instant): ClaimTimeoutResult {
        val game = gameRepository.findById(gameId)
            ?: return ClaimTimeoutResult.ClaimError("Game not found")

        if (game.isFinished()) {
            return ClaimTimeoutResult.ClaimError("Game is already finished")
        }

        // Only the waiting player can claim timeout (not the one who is to move)
        if (game.isPlayerTurn(claimer)) {
            return ClaimTimeoutResult.ClaimError("Cannot claim timeout on your own turn")
        }

        // Timeout only applies to timed games where both players have made their first move
        if (game.timeControl == null || game.timeControl.isUntimed || game.lastMoveAt == null || game.moveHistory.size < 2) {
            return ClaimTimeoutResult.ClaimError("No active time control")
        }

        val elapsedMs = (now - game.lastMoveAt).inWholeMilliseconds
        val currentPlayerTimeMs = if (game.currentSide == PlayerSide.WHITE) {
            game.whiteTimeRemainingMs ?: 0L
        } else {
            game.blackTimeRemainingMs ?: 0L
        }
        val remainingMs = currentPlayerTimeMs - elapsedMs

        if (remainingMs > 0) {
            return ClaimTimeoutResult.TimeoutRejected(remainingMs)
        }

        // Time expired: mark as TIMEOUT
        val loser = game.currentPlayer
        val timedOutGame = game.copy(
            status = GameStatus.TIMEOUT,
            winnerSide = loser.side.opposite(),
            whiteTimeRemainingMs = if (game.currentSide == PlayerSide.WHITE) remainingMs else game.whiteTimeRemainingMs,
            blackTimeRemainingMs = if (game.currentSide == PlayerSide.BLACK) remainingMs else game.blackTimeRemainingMs
        )
        gameRepository.save(timedOutGame)
        gameEventNotifier.notifyTimeout(timedOutGame, loser)
        return ClaimTimeoutResult.TimeoutConfirmed(loser.id.toString())
    }
}
