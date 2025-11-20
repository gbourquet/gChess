package com.gchess.matchmaking.domain.model

import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Entity representing a successful match between two players.
 *
 * A match is created when two players are paired together from the queue.
 * It contains the game ID, player assignments, and expiration information.
 *
 * @property whitePlayerId The player controlling white pieces
 * @property blackPlayerId The player controlling black pieces
 * @property gameId The unique identifier of the created game
 * @property matchedAt The timestamp when the match was created
 * @property expiresAt The timestamp when this match expires (TTL)
 */
data class Match(
    val whitePlayerId: PlayerId,
    val blackPlayerId: PlayerId,
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
         * @param whitePlayerId The player controlling white pieces
         * @param blackPlayerId The player controlling black pieces
         * @param gameId The unique identifier of the created game
         * @param ttlMinutes The time-to-live in minutes (defaults to DEFAULT_TTL_MINUTES)
         * @param now The current time (defaults to Clock.System.now())
         * @return A new Match instance
         */
        fun create(
            whitePlayerId: PlayerId,
            blackPlayerId: PlayerId,
            gameId: GameId,
            ttlMinutes: Int = DEFAULT_TTL_MINUTES,
            now: Instant = Clock.System.now()
        ): Match {
            return Match(
                whitePlayerId = whitePlayerId,
                blackPlayerId = blackPlayerId,
                gameId = gameId,
                matchedAt = now,
                expiresAt = now + ttlMinutes.minutes
            )
        }
    }
}
