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
