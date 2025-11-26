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
package com.gchess.chess.infrastructure.adapter.driver.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base sealed class for all WebSocket messages.
 * All messages follow the structure: {"type": "MessageType", "payload": {...}}
 */
@Serializable
sealed class GameWebSocketMessage {
    abstract val type: String
}

// ========== Gameplay Messages ==========

/**
 * Client → Server: Player attempts to make a move
 */
@Serializable
@SerialName("MoveAttempt")
data class MoveAttemptMessage(
    @SerialName("myType")
    override val type: String = "MoveAttempt",
    val from: String, // e.g., "e2"
    val to: String,   // e.g., "e4"
    val promotion: String? = null // "QUEEN", "ROOK", "BISHOP", "KNIGHT"
) : GameWebSocketMessage()

/**
 * Server → Client: Move executed successfully
 * Broadcast to both players (and spectators)
 */
@Serializable
@SerialName("MoveExecuted")
data class MoveExecutedMessage(
    @SerialName("myType")
    override val type: String = "MoveExecuted",
    val move: MoveDto,
    val newPositionFen: String, // FEN notation of the new position
    val gameStatus: String, // "IN_PROGRESS", "CHECK", "CHECKMATE", "STALEMATE", "DRAW"
    val currentSide: String // "WHITE" or "BLACK" - whose turn it is now
) : GameWebSocketMessage()

@Serializable
data class MoveDto(
    val from: String,
    val to: String,
    val promotion: String? = null
)

/**
 * Server → Client: Move rejected (invalid move)
 * Sent only to the player who attempted the move
 */
@Serializable
@SerialName("MoveRejected")
data class MoveRejectedMessage(
    @SerialName("myType")
    override val type: String = "MoveRejected",
    val reason: String
) : GameWebSocketMessage()

/**
 * Server → Client: Complete game state synchronization
 * Sent on connection or reconnection
 */
@Serializable
@SerialName("GameStateSync")
data class GameStateSyncMessage(
    @SerialName("myType")
    override val type: String = "GameStateSync",
    val gameId: String,
    val positionFen: String,
    val moveHistory: List<MoveDto>,
    val gameStatus: String,
    val currentSide: String,
    val whitePlayerId: String,
    val blackPlayerId: String
) : GameWebSocketMessage()

/**
 * Server → Client: Player disconnected
 */
@Serializable
@SerialName("PlayerDisconnected")
data class PlayerDisconnectedMessage(
    @SerialName("myType")
    override val type: String = "PlayerDisconnected",
    val playerId: String
) : GameWebSocketMessage()

/**
 * Server → Client: Player reconnected
 */
@Serializable
@SerialName("PlayerReconnected")
data class PlayerReconnectedMessage(
    @SerialName("myType")
    override val type: String = "PlayerReconnected",
    val playerId: String
) : GameWebSocketMessage()

// ========== Common Messages ==========

/**
 * Server → Client: Generic error message
 */
@Serializable
@SerialName("Error")
data class GameErrorMessage(
    @SerialName("myType")
    override val type: String = "Error",
    val code: String,
    val message: String
) : GameWebSocketMessage()

/**
 * Server → Client: Authentication successful
 * Sent immediately after successful WebSocket authentication
 */
@Serializable
@SerialName("AuthSuccess")
data class GameAuthSuccessMessage(
    @SerialName("myType")
    override val type: String = "AuthSuccess",
    val userId: String
) : GameWebSocketMessage()

/**
 * Server → Client: Authentication failed
 */
@Serializable
@SerialName("AuthFailed")
data class GameAuthFailedMessage(
    @SerialName("myType")
    override val type: String = "AuthFailed",
    val reason: String
) : GameWebSocketMessage()
