package com.gchess.chess.domain.service

import com.gchess.chess.domain.model.*

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
        return position.getPiecesBySide(position.sideToMove)
            .flatMap { (pos, piece) -> generatePseudoLegalMoves(pos, piece, position) }
            .filter { move -> !wouldMoveCauseCheck(move, position) }
    }

    override fun isMoveLegal(position: ChessPosition, move: Move): Boolean {
        return legalMovesFor(position).contains(move)
    }

    override fun isInCheck(position: ChessPosition, side: PlayerSide): Boolean {
        // Find the king of the given side
        val kingPosition = position.getPiecesBySide(side)
            .entries
            .find { (_, piece) -> piece.type == PieceType.KING }
            ?.key
            ?: return false // No king found (shouldn't happen in a valid game)

        // Check if the king's position is threatened by the opponent
        val opponentSide = if (side == PlayerSide.WHITE) PlayerSide.BLACK else PlayerSide.WHITE
        return threatenedSquares(position, opponentSide).contains(kingPosition)
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
        // TODO: Implement checkmate detection
        return false
    }

    override fun isStalemate(position: ChessPosition): Boolean {
        // TODO: Implement stalemate detection
        return false
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
            // Temporarily remove the king to check if destination is threatened
            val boardWithoutKing = board.setPiece(move.from, null)
            val threatenedSquares = threatenedSquares(boardWithoutKing, opponentSide)
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
