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
package com.gchess.matchmaking.domain.model

import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.UserId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Entity representing a successful match between two users.
 *
 * A match is created when two users are paired together from the queue.
 * It contains the game ID, user assignments, and expiration information.
 *
 * @property whitePlayer The player controlling white pieces
 * @property blackPLayer The player controlling black pieces
 * @property gameId The unique identifier of the created game
 * @property matchedAt The timestamp when the match was created
 * @property expiresAt The timestamp when this match expires (TTL)
 */
data class Match(
    val whitePlayer: Player,
    val blackPlayer: Player,
    val gameId: GameId,
    val matchedAt: Instant,
    val expiresAt: Instant
) {
    /**
     * Checks if this match has expired based on the current time.
     *
     * @param now The current time to compare against (defaults to Clock.System.now())
     * @return true if the match has expired, false otherwise
     */
    fun isExpired(now: Instant = Clock.System.now()): Boolean {
        return now > expiresAt
    }

    companion object {
        /**
         * Default TTL (Time To Live) for matches in minutes.
         */
        const val DEFAULT_TTL_MINUTES = 5

        /**
         * Creates a new Match with automatic expiration calculation.
         *
         * @param whiteUserId The user controlling white pieces
         * @param blackUserId The user controlling black pieces
         * @param gameId The unique identifier of the created game
         * @param ttlMinutes The time-to-live in minutes (defaults to DEFAULT_TTL_MINUTES)
         * @param now The current time (defaults to Clock.System.now())
         * @return A new Match instance
         */
        fun create(
            whitePlayer: Player,
            blackPlayer: Player,
            gameId: GameId,
            ttlMinutes: Int = DEFAULT_TTL_MINUTES,
            now: Instant = Clock.System.now()
        ): Match {
            return Match(
                whitePlayer = whitePlayer,
                blackPlayer = blackPlayer,
                gameId = gameId,
                matchedAt = now,
                expiresAt = now + ttlMinutes.minutes
            )
        }
    }
}
