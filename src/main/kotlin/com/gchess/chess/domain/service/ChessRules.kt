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
package com.gchess.chess.domain.service

import com.gchess.chess.domain.model.ChessPosition
import com.gchess.shared.domain.model.PlayerSide
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

    /**
     * Checks if the fifty-move rule applies.
     * The game is drawn if 50 consecutive moves (100 half-moves) have been made
     * without any pawn move or capture.
     *
     * @param position The current chess position (contains halfmoveClock)
     * @return true if the fifty-move rule applies, false otherwise
     */
    fun isFiftyMoveRule(position: ChessPosition): Boolean

    /**
     * Checks if the threefold repetition rule applies.
     * The game is drawn if the same position occurs three times
     * (same pieces on same squares, same side to move, same castling/en passant rights).
     *
     * @param currentPosition The current chess position
     * @param positionHistory List of all previous positions in the game (including current)
     * @return true if threefold repetition occurred, false otherwise
     */
    fun isThreefoldRepetition(currentPosition: ChessPosition, positionHistory: List<ChessPosition>): Boolean

    /**
     * Checks if there is insufficient material to checkmate.
     * The game is drawn if neither player has enough pieces to deliver checkmate.
     * Examples:
     * - King vs King
     * - King + Bishop vs King
     * - King + Knight vs King
     * - King + Bishop vs King + Bishop (same color bishops)
     *
     * @param position The current chess position
     * @return true if insufficient material for checkmate, false otherwise
     */
    fun isInsufficientMaterial(position: ChessPosition): Boolean
}
