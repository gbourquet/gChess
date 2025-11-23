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
package com.gchess.matchmaking.application.usecase

import com.gchess.shared.domain.model.PlayerSide
import com.gchess.shared.domain.model.GameId

/**
 * Result of a matchmaking status check.
 *
 * This sealed class represents the different states a player can be in:
 * - NotFound: Player is neither in queue nor matched
 * - Waiting: Player is in the queue waiting for an opponent
 * - Matched: Player has been matched and a game has been created
 */
sealed class MatchmakingResult {
    /**
     * Player is not in queue and has no active match.
     */
    data object NotFound : MatchmakingResult()

    /**
     * Player is waiting in the queue.
     *
     * @property queuePosition Position in the queue (1-indexed)
     */
    data class Waiting(val queuePosition: Int) : MatchmakingResult()

    /**
     * Player has been matched with an opponent.
     *
     * @property gameId ID of the created game
     * @property yourColor The color assigned to this player (WHITE or BLACK)
     */
    data class Matched(
        val gameId: GameId,
        val yourColor: PlayerSide
    ) : MatchmakingResult()
}
