package com.gchess.matchmaking.domain.model

/**
 * Enum representing the possible matchmaking states for a player.
 *
 * @property WAITING Player is in the queue waiting for an opponent
 * @property MATCHED Player has been matched with an opponent and a game has been created
 */
enum class MatchmakingStatus {
    WAITING,
    MATCHED
}
