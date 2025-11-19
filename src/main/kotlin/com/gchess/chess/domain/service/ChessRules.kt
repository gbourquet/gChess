package com.gchess.chess.domain.service

import com.gchess.chess.domain.model.ChessPosition
import com.gchess.chess.domain.model.PlayerSide
import com.gchess.chess.domain.model.Move
import com.gchess.chess.domain.model.Position

/**
 * Domain Service that encapsulates the rules of chess.
 * This represents the knowledge of how chess pieces move and what moves are legal.
 *
 * In DDD terms, this is a Domain Service because the logic doesn't naturally
 * belong to any single Entity or Value Object - it requires knowledge of the
 * entire position to determine legal moves.
 */
interface ChessRules {

    /**
     * Returns all legal moves for the current position.
     * A legal move is one that:
     * - Follows the movement rules for the piece type
     * - Does not leave the player's own king in check
     * - Respects special rules (castling rights, en passant, etc.)
     *
     * @param position The current chess position
     * @return List of all legal moves in this position
     */
    fun legalMovesFor(position: ChessPosition): List<Move>

    /**
     * Checks if a specific move is legal in the current position.
     *
     * @param position The current chess position
     * @param move The move to validate
     * @return true if the move is legal, false otherwise
     */
    fun isMoveLegal(position: ChessPosition, move: Move): Boolean

    /**
     * Checks if the king of the given side is currently in check.
     *
     * @param position The current chess position
     * @param side The side of the king to check
     * @return true if the king is in check, false otherwise
     */
    fun isInCheck(position: ChessPosition, side: PlayerSide): Boolean

    /**
     * Returns all squares that are under attack by the given side.
     * A square is "under attack" if a piece of that side could capture on that square.
     *
     * @param position The current chess position
     * @param by The attacking side
     * @return Set of positions that are under attack
     */
    fun threatenedSquares(position: ChessPosition, by: PlayerSide): Set<Position>

    /**
     * Checks if the position is checkmate for the side to move.
     * Checkmate occurs when:
     * - The king is in check
     * - There are no legal moves available
     *
     * @param position The current chess position
     * @return true if it's checkmate, false otherwise
     */
    fun isCheckmate(position: ChessPosition): Boolean

    /**
     * Checks if the position is stalemate for the side to move.
     * Stalemate occurs when:
     * - The king is NOT in check
     * - There are no legal moves available
     *
     * @param position The current chess position
     * @return true if it's stalemate, false otherwise
     */
    fun isStalemate(position: ChessPosition): Boolean
}
