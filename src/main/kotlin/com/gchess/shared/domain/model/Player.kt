package com.gchess.shared.domain.model

import com.gchess.shared.domain.model.PlayerSide

/**
 * Value object representing a player's participation in a specific chess game.
 *
 * A Player is created when a User joins a game and represents their role in that game.
 * Each Player has:
 * - A unique PlayerId for this game participation
 * - A reference to the User's permanent UserId
 * - A side (WHITE or BLACK) they are playing
 *
 * This separation between User (permanent identity) and Player (game participation)
 * allows for future extensions like:
 * - Per-game statistics
 * - Time controls per player
 * - Game-specific settings
 *
 * Invariants:
 * - A player must have a side (WHITE or BLACK)
 * - The userId must reference an existing user
 */
data class Player(
    val id: PlayerId,
    val userId: UserId,
    val side: PlayerSide
) {
    companion object {
        /**
         * Creates a new Player with a generated PlayerId.
         *
         * @param userId The ID of the user participating in the game
         * @param side The side (WHITE or BLACK) this player is playing
         * @return A new Player instance
         */
        fun create(userId: UserId, side: PlayerSide): Player {
            return Player(
                id = PlayerId.generate(),
                userId = userId,
                side = side
            )
        }
    }
}