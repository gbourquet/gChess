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
package com.gchess.matchmaking.infrastructure.adapter.driver.dto

import com.gchess.matchmaking.application.usecase.MatchmakingResult
import kotlinx.serialization.Serializable

/**
 * DTO for matchmaking status responses.
 *
 * This DTO represents the different states a player can be in:
 * - status: "WAITING" - Player is in queue
 * - status: "MATCHED" - Player has been matched
 * - status: "NOT_FOUND" - Player is neither in queue nor matched
 */
@Serializable
data class MatchmakingStatusDTO(
    val status: String,
    val queuePosition: Int? = null,
    val gameId: String? = null,
    val yourColor: String? = null
)

/**
 * Converts a MatchmakingResult to a DTO.
 */
fun MatchmakingResult.toDTO(): MatchmakingStatusDTO {
    return when (this) {
        is MatchmakingResult.NotFound -> MatchmakingStatusDTO(
            status = "NOT_FOUND"
        )

        is MatchmakingResult.Waiting -> MatchmakingStatusDTO(
            status = "WAITING",
            queuePosition = this.queuePosition
        )

        is MatchmakingResult.Matched -> MatchmakingStatusDTO(
            status = "MATCHED",
            gameId = this.gameId.value,
            yourColor = this.yourColor.name
        )
    }
}
