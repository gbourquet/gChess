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

import com.gchess.chess.domain.model.*
import com.gchess.shared.domain.model.PlayerSide

/**
 * Standard implementation of chess rules following FIDE (International Chess Federation) rules.
 *
 * This Domain Service encapsulates all the business logic for:
 * - Move generation for each piece type
 * - Move validation
 * - Check/checkmate/stalemate detection
 * - Special moves (castling, en passant, promotion)
 */
class StandardChessRules : ChessRules {

    override fun legalMovesFor(position: ChessPosition): List<Move> {
        val moves = mutableListOf<Move>()
        val sideToMove = position.sideToMove
        val opponentSide = sideToMove.opposite()

        // Find our king
        val kingBitboard = if (sideToMove == PlayerSide.WHITE) position.whiteKings else position.blackKings
        if (kingBitboard == 0L) return emptyList() // No king (shouldn't happen)
        val kingSquare = kingBitboard.countTrailingZeroBits()

        // Check if we're in check and find attackers
        val attackers = findAttackers(position, kingSquare, opponentSide)
        val inCheck = attackers.isNotEmpty()

        if (inCheck) {
            // In check: must block, capture attacker, or move king
            // IMPORTANT: Still need to check for pins even in check!
            val pinnedPieces = detectPinnedPieces(position, kingSquare, sideToMove, opponentSide)

            if (attackers.size > 1) {
                // Double check: only king moves are legal
                for ((pos, piece) in position.getPiecesBySide(sideToMove)) {
                    if (piece.type == PieceType.KING) {
                        moves.addAll(generateLegalKingMoves(pos, piece, position, opponentSide))
                    }
                }
            } else {
                // Single check: can block or capture
                val attacker = attackers[0]
                val blockOrCaptureMask = calculateBlockOrCaptureMask(kingSquare, attacker, position)

                for ((pos, piece) in position.getPiecesBySide(sideToMove)) {
                    if (piece.type == PieceType.KING) {
                        // King can move out of check
                        moves.addAll(generateLegalKingMoves(pos, piece, position, opponentSide))
                    } else {
                        // Other pieces can only block or capture the attacker
                        // BUT: pinned pieces can't move off their pin ray
                        val pinRay = pinnedPieces[pos]
                        val pseudoMoves = generatePseudoLegalMoves(pos, piece, position)

                        if (pinRay != null) {
                            // Pinned piece: must stay on pin ray AND block/capture
                            moves.addAll(pseudoMoves.filter { move ->
                                val destBit = 1L shl ChessPosition.positionToIndex(move.to)
                                (blockOrCaptureMask and destBit) != 0L && (pinRay and destBit) != 0L
                            })
                        } else {
                            // Non-pinned piece: just need to block/capture
                            moves.addAll(pseudoMoves.filter { move ->
                                val destBit = 1L shl ChessPosition.positionToIndex(move.to)
                                (blockOrCaptureMask and destBit) != 0L
                            })
                        }
                    }
                }
            }
        } else {
            // Not in check: normal move generation with pin detection
            val pinnedPieces = detectPinnedPieces(position, kingSquare, sideToMove, opponentSide)

            for ((pos, piece) in position.getPiecesBySide(sideToMove)) {
                if (piece.type == PieceType.KING) {
                    // King moves: exclude attacked squares
                    moves.addAll(generateLegalKingMoves(pos, piece, position, opponentSide))
                } else {
                    // Check if this piece is pinned
                    val pinRay = pinnedPieces[pos]
                    if (pinRay != null) {
                        // Pinned piece: only moves along the pin ray are legal
                        val pseudoMoves = generatePseudoLegalMoves(pos, piece, position)
                        moves.addAll(pseudoMoves.filter { move ->
                            val destBit = 1L shl ChessPosition.positionToIndex(move.to)
                            (pinRay and destBit) != 0L
                        })
                    } else {
                        // Non-pinned piece: all pseudo-legal moves are legal
                        moves.addAll(generatePseudoLegalMoves(pos, piece, position))
                    }
                }
            }
        }

        return moves
    }

    /**
     * Finds all pieces attacking a square.
     * Returns list of attacker square indices.
     */
    private fun findAttackers(position: ChessPosition, targetSquare: Int, attackingSide: PlayerSide): List<Int> {
        val attackers = mutableListOf<Int>()
        val occupied = position.occupiedSquares()

        // Get attacking side's bitboards
        val pawns: Long
        val knights: Long
        val bishops: Long
        val rooks: Long
        val queens: Long

        if (attackingSide == PlayerSide.WHITE) {
            pawns = position.whitePawns
            knights = position.whiteKnights
            bishops = position.whiteBishops
            rooks = position.whiteRooks
            queens = position.whiteQueens
        } else {
            pawns = position.blackPawns
            knights = position.blackKnights
            bishops = position.blackBishops
            rooks = position.blackRooks
            queens = position.blackQueens
        }

        // Check pawn attacks
        // IMPORTANT: Use opposite table (same reason as in isSquareAttacked)
        val pawnAttacks = if (attackingSide == PlayerSide.WHITE) {
            AttackTables.BLACK_PAWN_ATTACKS[targetSquare]
        } else {
            AttackTables.WHITE_PAWN_ATTACKS[targetSquare]
        }
        var attackingPawns = pawns and pawnAttacks
        while (attackingPawns != 0L) {
            attackers.add(attackingPawns.countTrailingZeroBits())
            attackingPawns = attackingPawns and (attackingPawns - 1)
        }

        // Check knight attacks
        val knightAttacks = AttackTables.KNIGHT_ATTACKS[targetSquare]
        var attackingKnights = knights and knightAttacks
        while (attackingKnights != 0L) {
            attackers.add(attackingKnights.countTrailingZeroBits())
            attackingKnights = attackingKnights and (attackingKnights - 1)
        }

        // Check bishop/queen diagonal attacks
        val bishopAttacks = AttackTables.generateSliderAttacks(targetSquare, occupied, AttackTables.BISHOP_DIRECTIONS)
        var attackingBishops = (bishops or queens) and bishopAttacks
        while (attackingBishops != 0L) {
            attackers.add(attackingBishops.countTrailingZeroBits())
            attackingBishops = attackingBishops and (attackingBishops - 1)
        }

        // Check rook/queen attacks
        val rookAttacks = AttackTables.generateSliderAttacks(targetSquare, occupied, AttackTables.ROOK_DIRECTIONS)
        var attackingRooks = (rooks or queens) and rookAttacks
        while (attackingRooks != 0L) {
            attackers.add(attackingRooks.countTrailingZeroBits())
            attackingRooks = attackingRooks and (attackingRooks - 1)
        }

        return attackers
    }

    /**
     * Calculates bitboard of squares where blocking or capturing would resolve check.
     * For slider attacks: includes all squares between king and attacker + attacker square.
     * For non-slider attacks: only the attacker square (capture only).
     */
    private fun calculateBlockOrCaptureMask(kingSquare: Int, attackerSquare: Int, position: ChessPosition): Long {
        val attacker = position.pieceAt(ChessPosition.indexToPosition(attackerSquare))
        var mask = 1L shl attackerSquare // Can always capture the attacker

        // For sliders, can also block
        if (attacker?.type in listOf(PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN)) {
            // Calculate ray between king and attacker
            val kFile = kingSquare % 8
            val kRank = kingSquare / 8
            val aFile = attackerSquare % 8
            val aRank = attackerSquare / 8

            val df = when {
                aFile > kFile -> 1
                aFile < kFile -> -1
                else -> 0
            }
            val dr = when {
                aRank > kRank -> 1
                aRank < kRank -> -1
                else -> 0
            }

            var f = kFile + df
            var r = kRank + dr
            while (f != aFile || r != aRank) {
                mask = mask or (1L shl (r * 8 + f))
                f += df
                r += dr
            }
        }

        return mask
    }

    /**
     * Detects pinned pieces using bitboard ray-casting.
     *
     * A piece is pinned if removing it would expose the king to attack by a slider.
     *
     * @return Map of pinned piece positions to their pin ray bitboard
     */
    private fun detectPinnedPieces(
        position: ChessPosition,
        kingSquare: Int,
        ourSide: PlayerSide,
        opponentSide: PlayerSide
    ): Map<Position, Long> {
        val pinnedPieces = mutableMapOf<Position, Long>()
        val occupied = position.occupiedSquares()

        // Get opponent's sliders (bishops, rooks, queens)
        val opponentBishops: Long
        val opponentRooks: Long
        val opponentQueens: Long

        if (opponentSide == PlayerSide.WHITE) {
            opponentBishops = position.whiteBishops
            opponentRooks = position.whiteRooks
            opponentQueens = position.whiteQueens
        } else {
            opponentBishops = position.blackBishops
            opponentRooks = position.blackRooks
            opponentQueens = position.blackQueens
        }

        // Check diagonal pins (bishops and queens)
        for (direction in AttackTables.BISHOP_DIRECTIONS) {
            checkPinAlongRay(
                kingSquare, direction, occupied, opponentBishops or opponentQueens,
                ourSide, position, pinnedPieces
            )
        }

        // Check horizontal/vertical pins (rooks and queens)
        for (direction in AttackTables.ROOK_DIRECTIONS) {
            checkPinAlongRay(
                kingSquare, direction, occupied, opponentRooks or opponentQueens,
                ourSide, position, pinnedPieces
            )
        }

        return pinnedPieces
    }

    /**
     * Checks for a pin along a single ray from the king.
     */
    private fun checkPinAlongRay(
        kingSquare: Int,
        direction: Pair<Int, Int>,
        occupied: Long,
        attackers: Long,
        ourSide: PlayerSide,
        position: ChessPosition,
        pinnedPieces: MutableMap<Position, Long>
    ) {
        val (df, dr) = direction
        var f = (kingSquare % 8) + df
        var r = (kingSquare / 8) + dr
        var pinRay = 0L
        var ourPieceSquare: Int? = null

        while (f in 0..7 && r in 0..7) {
            val square = r * 8 + f
            val bit = 1L shl square
            pinRay = pinRay or bit

            if ((occupied and bit) != 0L) {
                // Found a piece
                if (ourPieceSquare == null) {
                    // First piece: check if it's ours
                    val piece = position.pieceAt(ChessPosition.indexToPosition(square))
                    if (piece?.side == ourSide) {
                        ourPieceSquare = square
                    } else {
                        // Opponent piece or wrong color - no pin
                        break
                    }
                } else {
                    // Second piece: check if it's an attacking slider
                    if ((attackers and bit) != 0L) {
                        // Pin detected!
                        pinnedPieces[ChessPosition.indexToPosition(ourPieceSquare)] = pinRay
                    }
                    break
                }
            }

            f += df
            r += dr
        }
    }

    /**
     * Generates legal king moves by excluding attacked squares.
     *
     * IMPORTANT: Must remove the king AND any captured piece from the board
     * before checking if the destination is attacked, otherwise the king
     * blocks slider attacks through its current position.
     */
    private fun generateLegalKingMoves(
        position: Position,
        piece: Piece,
        board: ChessPosition,
        opponentSide: PlayerSide
    ): List<Move> {
        val pseudoMoves = generatePseudoLegalMoves(position, piece, board)

        // Remove king from the board for attack detection
        val boardWithoutKing = board.setPiece(position, null)

        return pseudoMoves.filter { move ->
            // Also remove any piece being captured
            val boardForCheck = boardWithoutKing.setPiece(move.to, null)

            // Check if destination square is attacked in this modified board
            val destSquare = ChessPosition.positionToIndex(move.to)
            !isSquareAttacked(boardForCheck, destSquare, opponentSide)
        }
    }

    override fun isMoveLegal(position: ChessPosition, move: Move): Boolean {
        return legalMovesFor(position).contains(move)
    }

    override fun isInCheck(position: ChessPosition, side: PlayerSide): Boolean {
        // Find the king of the given side using bitboards (O(1))
        val kingBitboard = if (side == PlayerSide.WHITE) position.whiteKings else position.blackKings
        if (kingBitboard == 0L) return false // No king found

        val kingSquare = kingBitboard.countTrailingZeroBits()
        val opponentSide = side.opposite()

        // Use optimized attack detection
        return isSquareAttacked(position, kingSquare, opponentSide)
    }

    /**
     * Fast bitboard-based check if a square is attacked by a given side.
     *
     * Instead of generating all moves, we look from the target square
     * to see if there are attackers. Much faster than threatenedSquares().
     *
     * @param position The chess position
     * @param targetSquare The square to check (0-63)
     * @param attackingSide Which side might be attacking
     * @return true if the square is attacked
     */
    private fun isSquareAttacked(position: ChessPosition, targetSquare: Int, attackingSide: PlayerSide): Boolean {
        val occupied = position.occupiedSquares()

        // Get attacking side's bitboards
        val pawns: Long
        val knights: Long
        val bishops: Long
        val rooks: Long
        val queens: Long
        val kings: Long

        if (attackingSide == PlayerSide.WHITE) {
            pawns = position.whitePawns
            knights = position.whiteKnights
            bishops = position.whiteBishops
            rooks = position.whiteRooks
            queens = position.whiteQueens
            kings = position.whiteKings
        } else {
            pawns = position.blackPawns
            knights = position.blackKnights
            bishops = position.blackBishops
            rooks = position.blackRooks
            queens = position.blackQueens
            kings = position.blackKings
        }

        // Check pawn attacks
        // IMPORTANT: Use opposite table! BLACK_PAWN_ATTACKS[square] tells us where a
        // black pawn at square attacks, but we want to know where black pawns COULD BE
        // to attack targetSquare. A white pawn at targetSquare would attack those squares!
        val pawnAttacks = if (attackingSide == PlayerSide.WHITE) {
            AttackTables.BLACK_PAWN_ATTACKS[targetSquare]  // Where white pawns could be
        } else {
            AttackTables.WHITE_PAWN_ATTACKS[targetSquare]  // Where black pawns could be
        }
        if ((pawns and pawnAttacks) != 0L) return true

        // Check knight attacks (lookup table, O(1))
        val knightAttacks = AttackTables.KNIGHT_ATTACKS[targetSquare]
        if ((knights and knightAttacks) != 0L) return true

        // Check king attacks (lookup table, O(1))
        val kingAttacks = AttackTables.KING_ATTACKS[targetSquare]
        if ((kings and kingAttacks) != 0L) return true

        // Check bishop/queen diagonal attacks (ray-casting)
        val bishopAttacks = AttackTables.generateSliderAttacks(targetSquare, occupied, AttackTables.BISHOP_DIRECTIONS)
        if (((bishops or queens) and bishopAttacks) != 0L) return true

        // Check rook/queen horizontal/vertical attacks (ray-casting)
        val rookAttacks = AttackTables.generateSliderAttacks(targetSquare, occupied, AttackTables.ROOK_DIRECTIONS)
        if (((rooks or queens) and rookAttacks) != 0L) return true

        return false
    }

    override fun threatenedSquares(position: ChessPosition, by: PlayerSide): Set<Position> {
        return position.getPiecesBySide(by)
            .flatMap { (pos, piece) ->
                if (piece.type == PieceType.PAWN) {
                    // For pawns, only diagonal attacks threaten squares, not forward moves
                    generatePawnThreatenedSquares(pos, piece)
                } else {
                    // Don't include castling moves when calculating threatened squares
                    generatePseudoLegalMoves(pos, piece, position, includeCastling = false).map { it.to }
                }
            }
            .toSet()
    }

    /**
     * Generates squares threatened by a pawn (diagonal attacks only, not forward moves)
     */
    private fun generatePawnThreatenedSquares(position: Position, piece: Piece): List<Position> {
        val direction = if (piece.side == PlayerSide.WHITE) 1 else -1

        return listOf(position.file - 1, position.file + 1)
            .filter { it in 0..7 }
            .map { captureFile -> Position(captureFile, position.rank + direction) }
            .filter { it.rank in 0..7 }
    }

    override fun isCheckmate(position: ChessPosition): Boolean {
        val sideToMove = position.sideToMove

        // Checkmate requires:
        // 1. The king must be in check
        // 2. There must be no legal moves available
        return isInCheck(position, sideToMove) && legalMovesFor(position).isEmpty()
    }

    override fun isStalemate(position: ChessPosition): Boolean {
        val sideToMove = position.sideToMove

        // Stalemate requires:
        // 1. The king must NOT be in check
        // 2. There must be no legal moves available
        return !isInCheck(position, sideToMove) && legalMovesFor(position).isEmpty()
    }

    override fun isFiftyMoveRule(position: ChessPosition): Boolean {
        // The fifty-move rule applies when 50 consecutive moves (100 half-moves)
        // have been made without any pawn move or capture
        return position.halfmoveClock >= 100
    }

    override fun isThreefoldRepetition(currentPosition: ChessPosition, positionHistory: List<ChessPosition>): Boolean {
        // Extract the significant part of FEN (excluding move counters)
        // Two positions are the same if they have identical:
        // - Piece placement
        // - Side to move
        // - Castling rights
        // - En passant target square
        val currentKey = positionKey(currentPosition)

        // Count how many times this position appears in history
        val occurrences = positionHistory.count { positionKey(it) == currentKey }

        // Threefold repetition occurs when the position appears 3 or more times
        return occurrences >= 3
    }

    /**
     * Extracts a position key for comparison (FEN without move counters).
     * Two positions are considered identical if they have the same key.
     */
    private fun positionKey(position: ChessPosition): String {
        // FEN format: "position activeColor castling enPassant halfmove fullmove"
        // We only care about the first 4 parts for position equality
        val fen = position.toFen()
        val parts = fen.split(" ")
        return "${parts[0]} ${parts[1]} ${parts[2]} ${parts[3]}"
    }

    override fun isInsufficientMaterial(position: ChessPosition): Boolean {
        // Count pieces for each side
        val whitePieces = position.getPiecesBySide(PlayerSide.WHITE)
        val blackPieces = position.getPiecesBySide(PlayerSide.BLACK)

        // Count piece types (excluding kings)
        val whiteCounts = countPieceTypes(whitePieces)
        val blackCounts = countPieceTypes(blackPieces)

        // Total non-king pieces
        val whiteTotalPieces = whiteCounts.values.sum()
        val blackTotalPieces = blackCounts.values.sum()

        // Case 1: King vs King
        if (whiteTotalPieces == 0 && blackTotalPieces == 0) {
            return true
        }

        // Case 2: King + Bishop vs King
        if (whiteTotalPieces == 1 && whiteCounts[PieceType.BISHOP] == 1 && blackTotalPieces == 0) {
            return true
        }
        if (blackTotalPieces == 1 && blackCounts[PieceType.BISHOP] == 1 && whiteTotalPieces == 0) {
            return true
        }

        // Case 3: King + Knight vs King
        if (whiteTotalPieces == 1 && whiteCounts[PieceType.KNIGHT] == 1 && blackTotalPieces == 0) {
            return true
        }
        if (blackTotalPieces == 1 && blackCounts[PieceType.KNIGHT] == 1 && whiteTotalPieces == 0) {
            return true
        }

        // Case 4: King + Bishop vs King + Bishop (same color bishops)
        if (whiteTotalPieces == 1 && whiteCounts[PieceType.BISHOP] == 1 &&
            blackTotalPieces == 1 && blackCounts[PieceType.BISHOP] == 1) {
            // Check if bishops are on same color squares
            val whiteBishopSquare = whitePieces.entries.find { it.value.type == PieceType.BISHOP }?.key
            val blackBishopSquare = blackPieces.entries.find { it.value.type == PieceType.BISHOP }?.key

            if (whiteBishopSquare != null && blackBishopSquare != null) {
                val whiteBishopColor = (whiteBishopSquare.file + whiteBishopSquare.rank) % 2
                val blackBishopColor = (blackBishopSquare.file + blackBishopSquare.rank) % 2
                if (whiteBishopColor == blackBishopColor) {
                    return true
                }
            }
        }

        // All other cases: sufficient material for checkmate
        return false
    }

    /**
     * Counts the number of pieces of each type (excluding kings).
     */
    private fun countPieceTypes(pieces: Map<Position, Piece>): Map<PieceType, Int> {
        return pieces.values
            .filter { it.type != PieceType.KING }
            .groupingBy { it.type }
            .eachCount()
    }

    // ========== Private: Move Generation ==========

    /**
     * Generates all pseudo-legal moves for a piece at a given position.
     * Pseudo-legal means moves that follow piece movement rules but might leave the king in check.
     */
    private fun generatePseudoLegalMoves(
        position: Position,
        piece: Piece,
        board: ChessPosition,
        includeCastling: Boolean = true
    ): List<Move> {
        return when (piece.type) {
            PieceType.PAWN -> generatePawnMoves(position, piece, board)
            PieceType.KNIGHT -> generateKnightMoves(position, piece, board)
            PieceType.BISHOP -> generateBishopMoves(position, piece, board)
            PieceType.ROOK -> generateRookMoves(position, piece, board)
            PieceType.QUEEN -> generateQueenMoves(position, piece, board)
            PieceType.KING -> generateKingMoves(position, piece, board, includeCastling)
        }
    }

    // ========== Pawn Moves ==========

    /**
     * Generates pseudo-legal pawn moves.
     * Includes: forward moves, double push, captures, en passant, promotions
     */
    private fun generatePawnMoves(position: Position, piece: Piece, board: ChessPosition): List<Move> {
        val direction = if (piece.side == PlayerSide.WHITE) 1 else -1
        val startRank = if (piece.side == PlayerSide.WHITE) 1 else 6
        val promotionRank = if (piece.side == PlayerSide.WHITE) 7 else 0

        val forwardMoves = generatePawnForwardMoves(position, direction, startRank, promotionRank, board)
        val captureMoves = generatePawnCaptures(position, piece.side, direction, promotionRank, board)
        val enPassantMoves = generateEnPassantMoves(position, direction, board)

        return forwardMoves + captureMoves + enPassantMoves
    }

    /**
     * Generates forward pawn moves (single and double push)
     */
    private fun generatePawnForwardMoves(
        position: Position,
        direction: Int,
        startRank: Int,
        promotionRank: Int,
        board: ChessPosition
    ): List<Move> {
        val oneForward = Position(position.file, position.rank + direction)

        return if (oneForward.rank in 0..7 && !board.isOccupied(oneForward)) {
            val singleMove = if (oneForward.rank == promotionRank) {
                createPromotionMoves(position, oneForward)
            } else {
                listOf(Move(position, oneForward))
            }

            val doubleMove = if (position.rank == startRank) {
                val twoForward = Position(position.file, position.rank + direction * 2)
                if (!board.isOccupied(twoForward)) listOf(Move(position, twoForward)) else emptyList()
            } else {
                emptyList()
            }

            singleMove + doubleMove
        } else {
            emptyList()
        }
    }

    /**
     * Generates pawn capture moves (diagonal)
     */
    private fun generatePawnCaptures(
        position: Position,
        side: PlayerSide,
        direction: Int,
        promotionRank: Int,
        board: ChessPosition
    ): List<Move> {
        return listOf(position.file - 1, position.file + 1)
            .filter { it in 0..7 }
            .map { captureFile -> Position(captureFile, position.rank + direction) }
            .filter { capturePos ->
                val targetPiece = board.pieceAt(capturePos)
                targetPiece != null && targetPiece.side != side
            }
            .flatMap { capturePos ->
                if (capturePos.rank == promotionRank) {
                    createPromotionMoves(position, capturePos)
                } else {
                    listOf(Move(position, capturePos))
                }
            }
    }

    /**
     * Generates en passant capture moves
     */
    private fun generateEnPassantMoves(position: Position, direction: Int, board: ChessPosition): List<Move> {
        return board.enPassantSquare?.let { epSquare ->
            val epPosition = Position.fromAlgebraic(epSquare)
            listOf(position.file - 1, position.file + 1)
                .filter { it in 0..7 }
                .map { captureFile -> Position(captureFile, position.rank + direction) }
                .filter { it == epPosition }
                .map { Move(position, it) }
        } ?: emptyList()
    }

    /**
     * Creates all 4 promotion moves for a pawn move
     */
    private fun createPromotionMoves(from: Position, to: Position): List<Move> {
        return listOf(
            Move(from, to, PieceType.QUEEN),
            Move(from, to, PieceType.ROOK),
            Move(from, to, PieceType.BISHOP),
            Move(from, to, PieceType.KNIGHT)
        )
    }

    // ========== Knight Moves ==========

    /**
     * Generates pseudo-legal knight moves.
     * Knight moves in an L-shape: 2 squares in one direction and 1 square perpendicular.
     */
    private fun generateKnightMoves(position: Position, piece: Piece, board: ChessPosition): List<Move> =
        // All 8 possible L-shaped knight moves (2+1 or 1+2 in any direction)
        listOf(
            Pair(2, 1), Pair(2, -1),
            Pair(-2, 1), Pair(-2, -1),
            Pair(1, 2), Pair(1, -2),
            Pair(-1, 2), Pair(-1, -2)
        )
            .map { (dFile, dRank) -> Pair(position.file + dFile, position.rank + dRank) }
            .filter { (file, rank) -> file in 0..7 && rank in 0..7 }
            .map { (file, rank) -> Position(file, rank) }
            .filter { targetPos ->
                val targetPiece = board.pieceAt(targetPos)
                targetPiece == null || targetPiece.side != piece.side
            }
            .map { targetPos -> Move(position, targetPos) }

    // ========== Sliding Pieces Helper ==========

    /**
     * Generates moves along a ray (direction) until blocked by a piece or board edge.
     * Used for sliding pieces: bishops, rooks, and queens.
     */
    private fun generateRayMoves(
        position: Position,
        piece: Piece,
        board: ChessPosition,
        fileDirection: Int,
        rankDirection: Int
    ): List<Move> =
        generateSequence(1) { it + 1 }
            .map { distance ->
                val file = position.file + fileDirection * distance
                val rank = position.rank + rankDirection * distance
                Triple(file, rank, distance)
            }
            .takeWhile { (file, rank, _) -> file in 0..7 && rank in 0..7 }
            .map { (file, rank, _) ->
                val targetPos = Position(file, rank)
                val targetPiece = board.pieceAt(targetPos)
                Pair(targetPos, targetPiece)
            }
            .takeWhileInclusive { (_, targetPiece) -> targetPiece == null }
            .filter { (_, targetPiece) -> targetPiece == null || targetPiece.side != piece.side }
            .map { (targetPos, _) -> Move(position, targetPos) }
            .toList()

    /**
     * Helper extension to take elements while predicate is true, including the first false element.
     */
    private fun <T> Sequence<T>.takeWhileInclusive(predicate: (T) -> Boolean): Sequence<T> = sequence {
        for (item in this@takeWhileInclusive) {
            yield(item)
            if (!predicate(item)) break
        }
    }

    // ========== Bishop Moves ==========

    /**
     * Generates pseudo-legal bishop moves.
     * Bishop moves diagonally in all 4 diagonal directions.
     */
    private fun generateBishopMoves(position: Position, piece: Piece, board: ChessPosition): List<Move> =
        listOf(
            Pair(1, 1),   // up-right
            Pair(1, -1),  // down-right
            Pair(-1, 1),  // up-left
            Pair(-1, -1)  // down-left
        ).flatMap { (fileDir, rankDir) ->
            generateRayMoves(position, piece, board, fileDir, rankDir)
        }

    // ========== Rook Moves ==========

    /**
     * Generates pseudo-legal rook moves.
     * Rook moves orthogonally (horizontally and vertically) in all 4 directions.
     */
    private fun generateRookMoves(position: Position, piece: Piece, board: ChessPosition): List<Move> =
        listOf(
            Pair(0, 1),   // up (north)
            Pair(0, -1),  // down (south)
            Pair(1, 0),   // right (east)
            Pair(-1, 0)   // left (west)
        ).flatMap { (fileDir, rankDir) ->
            generateRayMoves(position, piece, board, fileDir, rankDir)
        }

    // ========== Queen Moves ==========

    /**
     * Generates pseudo-legal queen moves.
     * Queen combines rook and bishop movement patterns: moves in all 8 directions.
     */
    private fun generateQueenMoves(position: Position, piece: Piece, board: ChessPosition): List<Move> =
        listOf(
            // Orthogonal (rook-like)
            Pair(0, 1),   // up (north)
            Pair(0, -1),  // down (south)
            Pair(1, 0),   // right (east)
            Pair(-1, 0),  // left (west)
            // Diagonal (bishop-like)
            Pair(1, 1),   // up-right (northeast)
            Pair(1, -1),  // down-right (southeast)
            Pair(-1, 1),  // up-left (northwest)
            Pair(-1, -1)  // down-left (southwest)
        ).flatMap { (fileDir, rankDir) ->
            generateRayMoves(position, piece, board, fileDir, rankDir)
        }

    // ========== King Moves ==========

    /**
     * Generates pseudo-legal king moves.
     * King moves one square in any direction (8 directions total).
     * Also includes castling moves if conditions are met.
     */
    private fun generateKingMoves(
        position: Position,
        piece: Piece,
        board: ChessPosition,
        includeCastling: Boolean = true
    ): List<Move> {
        val normalMoves = listOf(
            Pair(0, 1),   // up (north)
            Pair(0, -1),  // down (south)
            Pair(1, 0),   // right (east)
            Pair(-1, 0),  // left (west)
            Pair(1, 1),   // up-right (northeast)
            Pair(1, -1),  // down-right (southeast)
            Pair(-1, 1),  // up-left (northwest)
            Pair(-1, -1)  // down-left (southwest)
        )
            .map { (fileDir, rankDir) -> Pair(position.file + fileDir, position.rank + rankDir) }
            .filter { (file, rank) -> file in 0..7 && rank in 0..7 }
            .map { (file, rank) -> Position(file, rank) }
            .filter { targetPos ->
                val targetPiece = board.pieceAt(targetPos)
                targetPiece == null || targetPiece.side != piece.side
            }
            .map { targetPos -> Move(position, targetPos) }

        val castlingMoves = if (includeCastling) {
            generateCastlingMoves(position, piece, board)
        } else {
            emptyList()
        }

        return normalMoves + castlingMoves
    }

    /**
     * Generates castling moves if all conditions are met:
     * 1. King and rook haven't moved (castling rights)
     * 2. No pieces between king and rook
     * 3. King is not currently in check
     * 4. King doesn't pass through a square under attack
     * 5. King doesn't end on a square under attack
     */
    private fun generateCastlingMoves(position: Position, piece: Piece, board: ChessPosition): List<Move> {
        val moves = mutableListOf<Move>()

        // King must not be in check to castle
        if (isInCheck(board, piece.side)) {
            return emptyList()
        }

        val opponentSide = piece.side.opposite()
        val threatenedSquares = threatenedSquares(board, opponentSide)

        if (piece.side == PlayerSide.WHITE) {
            // White kingside castling (petit roque)
            if (board.castlingRights.canCastleKingside(PlayerSide.WHITE) && position == Position.fromAlgebraic("e1")) {
                val f1 = Position.fromAlgebraic("f1")
                val g1 = Position.fromAlgebraic("g1")

                // Check no pieces between king and rook
                if (!board.isOccupied(f1) && !board.isOccupied(g1)) {
                    // Check king doesn't pass through or land on attacked squares
                    if (!threatenedSquares.contains(f1) && !threatenedSquares.contains(g1)) {
                        moves.add(Move(position, g1))
                    }
                }
            }

            // White queenside castling (grand roque)
            if (board.castlingRights.canCastleQueenside(PlayerSide.WHITE) && position == Position.fromAlgebraic("e1")) {
                val d1 = Position.fromAlgebraic("d1")
                val c1 = Position.fromAlgebraic("c1")
                val b1 = Position.fromAlgebraic("b1")

                // Check no pieces between king and rook
                if (!board.isOccupied(d1) && !board.isOccupied(c1) && !board.isOccupied(b1)) {
                    // Check king doesn't pass through or land on attacked squares
                    if (!threatenedSquares.contains(d1) && !threatenedSquares.contains(c1)) {
                        moves.add(Move(position, c1))
                    }
                }
            }
        } else {
            // Black kingside castling (petit roque)
            if (board.castlingRights.canCastleKingside(PlayerSide.BLACK) && position == Position.fromAlgebraic("e8")) {
                val f8 = Position.fromAlgebraic("f8")
                val g8 = Position.fromAlgebraic("g8")

                // Check no pieces between king and rook
                if (!board.isOccupied(f8) && !board.isOccupied(g8)) {
                    // Check king doesn't pass through or land on attacked squares
                    if (!threatenedSquares.contains(f8) && !threatenedSquares.contains(g8)) {
                        moves.add(Move(position, g8))
                    }
                }
            }

            // Black queenside castling (grand roque)
            if (board.castlingRights.canCastleQueenside(PlayerSide.BLACK) && position == Position.fromAlgebraic("e8")) {
                val d8 = Position.fromAlgebraic("d8")
                val c8 = Position.fromAlgebraic("c8")
                val b8 = Position.fromAlgebraic("b8")

                // Check no pieces between king and rook
                if (!board.isOccupied(d8) && !board.isOccupied(c8) && !board.isOccupied(b8)) {
                    // Check king doesn't pass through or land on attacked squares
                    if (!threatenedSquares.contains(d8) && !threatenedSquares.contains(c8)) {
                        moves.add(Move(position, c8))
                    }
                }
            }
        }

        return moves
    }

    // ========== Check Detection ==========

    /**
     * Checks if a move would leave the moving player's king in check
     */
    private fun wouldMoveCauseCheck(move: Move, board: ChessPosition): Boolean {
        val movingPiece = board.pieceAt(move.from)
        val opponentSide = if (board.sideToMove == PlayerSide.WHITE) PlayerSide.BLACK else PlayerSide.WHITE

        // Special case for king moves: check if destination square is attacked
        if (movingPiece?.type == PieceType.KING) {
            // For king moves, check if the destination square is threatened
            // Temporarily remove the king AND any piece being captured to check if destination is threatened
            val boardWithoutKing = board.setPiece(move.from, null)
            val boardWithoutKingAndTarget = boardWithoutKing.setPiece(move.to, null)
            val threatenedSquares = threatenedSquares(boardWithoutKingAndTarget, opponentSide)
            return threatenedSquares.contains(move.to)
        }

        // For other pieces: check if removing the piece exposes the king
        // This handles pinned pieces correctly
        val boardWithoutPiece = board.setPiece(move.from, null)
        val kingExposedAfterRemoval = isInCheck(boardWithoutPiece, board.sideToMove)

        if (!kingExposedAfterRemoval) {
            // Piece is not pinned, now check if placing it at destination causes check
            // (e.g., discovered check by moving a blocking piece)
            val newPosition = board.movePiece(move.from, move.to, move.promotion)
            return isInCheck(newPosition, board.sideToMove)
        } else {
            // Piece is pinned, check if it moves along the pin line
            val newPosition = board.movePiece(move.from, move.to, move.promotion)
            return isInCheck(newPosition, board.sideToMove)
        }
    }
}
