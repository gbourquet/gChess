package com.gchess.matchmaking.infrastructure.adapter.driver.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class MatchmakingMessage {
    abstract val type: String
}
/**
 * Client → Server: User wants to join the matchmaking queue
 */
@Serializable
@SerialName("JoinQueue")
data class JoinQueueMessage(
    @SerialName("myType")
    override val type: String = "JoinQueue"
) : MatchmakingMessage()

/**
 * Server → Client: User's queue position update
 */
@Serializable
@SerialName("QueuePositionUpdate")
data class QueuePositionUpdateMessage(
    @SerialName("myType")
    override val type: String = "QueuePositionUpdate",
    val position: Int
) : MatchmakingMessage()

/**
 * Server → Client: Match found, game created
 */
@Serializable
@SerialName("MatchFound")
data class MatchFoundMessage(
    @SerialName("myType")
    override val type: String = "MatchFound",
    val gameId: String,
    val yourColor: String, // "WHITE" or "BLACK"
    val playerId: String, // PlayerId for this participation
    val opponentUserId: String? = null // Optional: opponent's UserId for display
) : MatchmakingMessage()

/**
 * Server → Client: Matchmaking error
 */
@Serializable
@SerialName("MatchmakingError")
data class MatchmakingErrorMessage(
    @SerialName("myType")
    override val type: String = "MatchmakingError",
    val code: String,
    val message: String
) : MatchmakingMessage()


/**
 * Server → Client: Generic error message
 */
@Serializable
@SerialName("Error")
data class ErrorMessage(
    @SerialName("myType")
    override val type: String = "Error",
    val code: String,
    val message: String
) : MatchmakingMessage()

/**
 * Server → Client: Authentication successful
 * Sent immediately after successful WebSocket authentication
 */
@Serializable
@SerialName("AuthSuccess")
data class AuthSuccessMessage(
    @SerialName("myType")
    override val type: String = "AuthSuccess",
    val userId: String
) : MatchmakingMessage()

/**
 * Server → Client: Authentication failed
 */
@Serializable
@SerialName("AuthFailed")
data class AuthFailedMessage(
    @SerialName("myType")
    override val type: String = "AuthFailed",
    val reason: String
) : MatchmakingMessage()
