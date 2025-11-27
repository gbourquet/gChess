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
import kotlin.time.ExperimentalTime

/**
 * Entity representing a successful match between two users.
 *
 * A match is created when two users are paired together from the queue.
 * It contains the game ID, user assignments, and expiration information.
 *
 * @property whitePlayer The player controlling white pieces
 * @property blackPlayer The player controlling black pieces
 * @property gameId The unique identifier of the created game
 */
data class Match @OptIn(ExperimentalTime::class) constructor(
    val whitePlayer: Player,
    val blackPlayer: Player,
    val gameId: GameId,
) {

    companion object {

        /**
         * Creates a new Match with automatic expiration calculation.
         *
         * @param whitePlayer The player controlling white pieces
         * @param blackPlayer The player controlling black pieces
         * @param gameId The unique identifier of the created game
         * @return A new Match instance
         */
        fun create(
            whitePlayer: Player,
            blackPlayer: Player,
            gameId: GameId,
        ): Match {
            return Match(
                whitePlayer = whitePlayer,
                blackPlayer = blackPlayer,
                gameId = gameId,
            )
        }
    }
}
