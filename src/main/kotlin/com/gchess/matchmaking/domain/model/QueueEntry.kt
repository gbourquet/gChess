package com.gchess.matchmaking.domain.model

import com.gchess.shared.domain.model.PlayerId
import kotlinx.datetime.Instant

/**
 * Value object representing a player waiting in the matchmaking queue.
 *
 * @property playerId The unique identifier of the player
 * @property joinedAt The timestamp when the player joined the queue
 */
data class QueueEntry(
    val playerId: PlayerId,
    val joinedAt: Instant
)
