package com.gchess.chess.domain.model

import com.gchess.chess.domain.service.ChessRules
import com.gchess.chess.domain.service.StandardChessRules

/**
 * Bitboard representation of a chess board.
 * Uses 64-bit integers where each bit represents a square on the board.
 * Bit 0 = a1, Bit 1 = b1, ..., Bit 7 = h1, Bit 8 = a2, ..., Bit 63 = h8
 *
 * This is an efficient representation for chess engines, allowing fast
 * bitwise operations for move generation and board evaluation.
 */
data class ChessPosition(
    val whitePawns: Long = 0L,
    val whiteKnights: Long = 0L,
    val whiteBishops: Long = 0L,
    val whiteRooks: Long = 0L,
    val whiteQueens: Long = 0L,
    val whiteKings: Long = 0L,
    val blackPawns: Long = 0L,
    val blackKnights: Long = 0L,
    val blackBishops: Long = 0L,
    val blackRooks: Long = 0L,
    val blackQueens: Long = 0L,
    val blackKings: Long = 0L,
    val movedPieces: Long = 0L, // Tracks which pieces have moved
    val sideToMove: PlayerSide = PlayerSide.WHITE, // Which side has the move
    val castlingRights: CastlingRights = CastlingRights.NONE, // Castling availability for both players
    val enPassantSquare: String? = null, // En passant target square in algebraic notation (e.g., "e3")
    val halfmoveClock: Int = 0, // Number of halfmoves since last capture or pawn move (for 50-move rule)
    val fullmoveNumber: Int = 1 // The full move number (starts at 1, incremented after Black's move)
) {
    /**
     * Returns a bitboard with all white pieces
     */
    fun whitePieces(): Long = whitePawns or whiteKnights or whiteBishops or whiteRooks or whiteQueens or whiteKings

    /**
     * Returns a bitboard with all black pieces
     */
    fun blackPieces(): Long = blackPawns or blackKnights or blackBishops or blackRooks or blackQueens or blackKings

    /**
     * Returns a bitboard with all occupied squares
     */
    fun occupiedSquares(): Long = whitePieces() or blackPieces()

    /**
     * Returns a bitboard with all empty squares
     */
    fun emptySquares(): Long = occupiedSquares().inv()

    /**
     * Checks if a position is occupied
     */
    fun isOccupied(position: Position): Boolean {
        val bit = positionToBit(position)
        return (occupiedSquares() and bit) != 0L
    }

    /**
     * Gets the piece at a given position, or null if empty
     */
    fun pieceAt(position: Position): Piece? {
        val bit = positionToBit(position)
        val hasMoved = (movedPieces and bit) != 0L

        return when {
            (whitePawns and bit) != 0L -> Piece(PieceType.PAWN, PlayerSide.WHITE, hasMoved)
            (whiteKnights and bit) != 0L -> Piece(PieceType.KNIGHT, PlayerSide.WHITE, hasMoved)
            (whiteBishops and bit) != 0L -> Piece(PieceType.BISHOP, PlayerSide.WHITE, hasMoved)
            (whiteRooks and bit) != 0L -> Piece(PieceType.ROOK, PlayerSide.WHITE, hasMoved)
            (whiteQueens and bit) != 0L -> Piece(PieceType.QUEEN, PlayerSide.WHITE, hasMoved)
            (whiteKings and bit) != 0L -> Piece(PieceType.KING, PlayerSide.WHITE, hasMoved)
            (blackPawns and bit) != 0L -> Piece(PieceType.PAWN, PlayerSide.BLACK, hasMoved)
            (blackKnights and bit) != 0L -> Piece(PieceType.KNIGHT, PlayerSide.BLACK, hasMoved)
            (blackBishops and bit) != 0L -> Piece(PieceType.BISHOP, PlayerSide.BLACK, hasMoved)
            (blackRooks and bit) != 0L -> Piece(PieceType.ROOK, PlayerSide.BLACK, hasMoved)
            (blackQueens and bit) != 0L -> Piece(PieceType.QUEEN, PlayerSide.BLACK, hasMoved)
            (blackKings and bit) != 0L -> Piece(PieceType.KING, PlayerSide.BLACK, hasMoved)
            else -> null
        }
    }

    /**
     * Sets a piece at a given position
     */
    fun setPiece(position: Position, piece: Piece?): ChessPosition {
        val bit = positionToBit(position)
        val clearedBoard = clearSquare(position)

        return if (piece == null) {
            clearedBoard
        } else {
            val movedBits = if (piece.hasMoved) {
                clearedBoard.movedPieces or bit
            } else {
                clearedBoard.movedPieces and bit.inv()
            }

            when (piece.type to piece.side) {
                PieceType.PAWN to PlayerSide.WHITE -> clearedBoard.copy(
                    whitePawns = clearedBoard.whitePawns or bit,
                    movedPieces = movedBits
                )
                PieceType.KNIGHT to PlayerSide.WHITE -> clearedBoard.copy(
                    whiteKnights = clearedBoard.whiteKnights or bit,
                    movedPieces = movedBits
                )
                PieceType.BISHOP to PlayerSide.WHITE -> clearedBoard.copy(
                    whiteBishops = clearedBoard.whiteBishops or bit,
                    movedPieces = movedBits
                )
                PieceType.ROOK to PlayerSide.WHITE -> clearedBoard.copy(
                    whiteRooks = clearedBoard.whiteRooks or bit,
                    movedPieces = movedBits
                )
                PieceType.QUEEN to PlayerSide.WHITE -> clearedBoard.copy(
                    whiteQueens = clearedBoard.whiteQueens or bit,
                    movedPieces = movedBits
                )
                PieceType.KING to PlayerSide.WHITE -> clearedBoard.copy(
                    whiteKings = clearedBoard.whiteKings or bit,
                    movedPieces = movedBits
                )
                PieceType.PAWN to PlayerSide.BLACK -> clearedBoard.copy(
                    blackPawns = clearedBoard.blackPawns or bit,
                    movedPieces = movedBits
                )
                PieceType.KNIGHT to PlayerSide.BLACK -> clearedBoard.copy(
                    blackKnights = clearedBoard.blackKnights or bit,
                    movedPieces = movedBits
                )
                PieceType.BISHOP to PlayerSide.BLACK -> clearedBoard.copy(
                    blackBishops = clearedBoard.blackBishops or bit,
                    movedPieces = movedBits
                )
                PieceType.ROOK to PlayerSide.BLACK -> clearedBoard.copy(
                    blackRooks = clearedBoard.blackRooks or bit,
                    movedPieces = movedBits
                )
                PieceType.QUEEN to PlayerSide.BLACK -> clearedBoard.copy(
                    blackQueens = clearedBoard.blackQueens or bit,
                    movedPieces = movedBits
                )
                PieceType.KING to PlayerSide.BLACK -> clearedBoard.copy(
                    blackKings = clearedBoard.blackKings or bit,
                    movedPieces = movedBits
                )
                else -> clearedBoard
            }
        }
    }

    /**
     * Clears a square (removes any piece at this position)
     */
    private fun clearSquare(position: Position): ChessPosition {
        val clearMask = positionToBit(position).inv()
        return copy(
            whitePawns = whitePawns and clearMask,
            whiteKnights = whiteKnights and clearMask,
            whiteBishops = whiteBishops and clearMask,
            whiteRooks = whiteRooks and clearMask,
            whiteQueens = whiteQueens and clearMask,
            whiteKings = whiteKings and clearMask,
            blackPawns = blackPawns and clearMask,
            blackKnights = blackKnights and clearMask,
            blackBishops = blackBishops and clearMask,
            blackRooks = blackRooks and clearMask,
            blackQueens = blackQueens and clearMask,
            blackKings = blackKings and clearMask,
            movedPieces = movedPieces and clearMask
        )
    }

    /**
     * Moves a piece from one position to another, switches the side to move,
     * and updates castling rights if necessary
     * @param promotion The piece type to promote to (for pawn promotions)
     */
    fun movePiece(from: Position, to: Position, promotion: PieceType? = null): ChessPosition {
        val piece = pieceAt(from) ?: return this
        val isCapture = pieceAt(to) != null
        val isPawnMove = piece.type == PieceType.PAWN

        // Detect en passant capture
        val isEnPassant = isPawnMove &&
            enPassantSquare != null &&
            to.toAlgebraic() == enPassantSquare

        // Calculate en passant square if a pawn moves two squares
        val newEnPassant = if (isPawnMove) {
            val rankDiff = to.rank - from.rank
            when {
                // White pawn moves from rank 1 (index 1) to rank 3 (index 3)
                piece.side == PlayerSide.WHITE && rankDiff == 2 && from.rank == 1 -> {
                    Position(from.file, 2).toAlgebraic()
                }
                // Black pawn moves from rank 6 (index 6) to rank 4 (index 4)
                piece.side == PlayerSide.BLACK && rankDiff == -2 && from.rank == 6 -> {
                    Position(from.file, 5).toAlgebraic()
                }
                else -> null
            }
        } else {
            null
        }

        // Update halfmove clock: reset to 0 on capture or pawn move, otherwise increment
        val newHalfmoveClock = if (isCapture || isPawnMove) 0 else halfmoveClock + 1

        // Update fullmove number: increment after Black's move
        val newFullmoveNumber = if (sideToMove == PlayerSide.BLACK) fullmoveNumber + 1 else fullmoveNumber

        // Determine the piece to place on the destination square
        val destinationPiece = if (promotion != null && isPawnMove) {
            // Pawn promotion: replace with promoted piece
            Piece(promotion, piece.side, hasMoved = true)
        } else {
            // Normal move: same piece, marked as moved
            piece.withMoved()
        }

        var newBoard = setPiece(from, null)
            .setPiece(to, destinationPiece)
            .copy(
                sideToMove = sideToMove.opposite(),
                enPassantSquare = newEnPassant,
                halfmoveClock = newHalfmoveClock,
                fullmoveNumber = newFullmoveNumber
            )

        // Handle en passant capture: remove the captured pawn
        if (isEnPassant) {
            // The captured pawn is on the same file as 'to', but one rank behind
            val capturedPawnRank = if (piece.side == PlayerSide.WHITE) to.rank - 1 else to.rank + 1
            val capturedPawnPosition = Position(to.file, capturedPawnRank)
            newBoard = newBoard.setPiece(capturedPawnPosition, null)
        }

        // Handle castling: move the rook if king moves 2 squares horizontally
        val isCastling = piece.type == PieceType.KING && kotlin.math.abs(to.file - from.file) == 2
        if (isCastling) {
            // Determine which side is castling based on the king's destination
            val isKingsideCastling = to.file > from.file // King moves to the right

            if (piece.side == PlayerSide.WHITE) {
                if (isKingsideCastling) {
                    // White kingside castling: move rook from h1 to f1
                    val h1 = Position.fromAlgebraic("h1")
                    val f1 = Position.fromAlgebraic("f1")
                    val rook = newBoard.pieceAt(h1)
                    if (rook != null) {
                        newBoard = newBoard.setPiece(h1, null).setPiece(f1, rook.withMoved())
                    }
                } else {
                    // White queenside castling: move rook from a1 to d1
                    val a1 = Position.fromAlgebraic("a1")
                    val d1 = Position.fromAlgebraic("d1")
                    val rook = newBoard.pieceAt(a1)
                    if (rook != null) {
                        newBoard = newBoard.setPiece(a1, null).setPiece(d1, rook.withMoved())
                    }
                }
            } else {
                if (isKingsideCastling) {
                    // Black kingside castling: move rook from h8 to f8
                    val h8 = Position.fromAlgebraic("h8")
                    val f8 = Position.fromAlgebraic("f8")
                    val rook = newBoard.pieceAt(h8)
                    if (rook != null) {
                        newBoard = newBoard.setPiece(h8, null).setPiece(f8, rook.withMoved())
                    }
                } else {
                    // Black queenside castling: move rook from a8 to d8
                    val a8 = Position.fromAlgebraic("a8")
                    val d8 = Position.fromAlgebraic("d8")
                    val rook = newBoard.pieceAt(a8)
                    if (rook != null) {
                        newBoard = newBoard.setPiece(a8, null).setPiece(d8, rook.withMoved())
                    }
                }
            }
        }

        // Update castling rights based on piece type and position
        when {
            // King moves - lose both castling rights for that side
            piece.type == PieceType.KING -> {
                newBoard = newBoard.copy(
                    castlingRights = castlingRights.withoutRightsFor(piece.side)
                )
            }
            // White kingside rook moves
            piece.type == PieceType.ROOK && piece.side == PlayerSide.WHITE && from == Position.fromAlgebraic("h1") -> {
                newBoard = newBoard.copy(
                    castlingRights = castlingRights.withoutKingsideFor(PlayerSide.WHITE)
                )
            }
            // White queenside rook moves
            piece.type == PieceType.ROOK && piece.side == PlayerSide.WHITE && from == Position.fromAlgebraic("a1") -> {
                newBoard = newBoard.copy(
                    castlingRights = castlingRights.withoutQueensideFor(PlayerSide.WHITE)
                )
            }
            // Black kingside rook moves
            piece.type == PieceType.ROOK && piece.side == PlayerSide.BLACK && from == Position.fromAlgebraic("h8") -> {
                newBoard = newBoard.copy(
                    castlingRights = castlingRights.withoutKingsideFor(PlayerSide.BLACK)
                )
            }
            // Black queenside rook moves
            piece.type == PieceType.ROOK && piece.side == PlayerSide.BLACK && from == Position.fromAlgebraic("a8") -> {
                newBoard = newBoard.copy(
                    castlingRights = castlingRights.withoutQueensideFor(PlayerSide.BLACK)
                )
            }
        }

        return newBoard
    }

    /**
     * Gets all pieces with their positions
     */
    fun getAllPieces(): Map<Position, Piece> {
        val pieces = mutableMapOf<Position, Piece>()
        for (rank in 0..7) {
            for (file in 0..7) {
                val position = Position(file, rank)
                pieceAt(position)?.let { pieces[position] = it }
            }
        }
        return pieces
    }

    /**
     * Gets all pieces of a given side
     */
    fun getPiecesBySide(side: PlayerSide): Map<Position, Piece> {
        return getAllPieces().filterValues { it.side == side }
    }

    /**
     * Generates all legal moves for the current position.
     *
     * Delegates to the ChessRules domain service which encapsulates
     * all the business logic for move generation.
     *
     * Uses a singleton instance of StandardChessRules for performance.
     *
     * @return List of all legal moves in the current position
     */
    fun getLegalMoves(): List<Move> {
        return standardRules.legalMovesFor(this)
    }

    /**
     * Converts this ChessPosition to FEN (Forsyth-Edwards Notation)
     *
     * FEN format: position activeColor castling enPassant halfmove fullmove
     *
     * @return FEN string representing this position
     */
    fun toFen(): String {
        val position = buildPositionString()
        val activeColorChar = if (sideToMove == PlayerSide.WHITE) "w" else "b"
        val castling = buildCastlingRights()
        val enPassant = enPassantSquare ?: "-"

        return "$position $activeColorChar $castling $enPassant $halfmoveClock $fullmoveNumber"
    }

    /**
     * Builds the position part of FEN notation (piece placement)
     */
    private fun buildPositionString(): String {
        val ranks = mutableListOf<String>()

        // FEN goes from rank 8 to rank 1 (top to bottom)
        for (rank in 7 downTo 0) {
            var rankString = ""
            var emptyCount = 0

            for (file in 0..7) {
                val position = Position(file, rank)
                val piece = pieceAt(position)

                if (piece == null) {
                    emptyCount++
                } else {
                    // Add empty count if any
                    if (emptyCount > 0) {
                        rankString += emptyCount.toString()
                        emptyCount = 0
                    }
                    // Add piece character
                    rankString += piece.toFenChar()
                }
            }

            // Add remaining empty count
            if (emptyCount > 0) {
                rankString += emptyCount.toString()
            }

            ranks.add(rankString)
        }

        return ranks.joinToString("/")
    }

    /**
     * Builds the castling rights part of FEN notation
     */
    private fun buildCastlingRights(): String {
        return castlingRights.toFenString()
    }

    /**
     * Sets the castling rights based on FEN notation
     */
    internal fun updateCastlingRights(castlingRightsFen: String): ChessPosition {
        return copy(castlingRights = CastlingRights.fromFenString(castlingRightsFen))
    }

    /**
     * Marks pieces (kings and rooks) as moved based on castling rights
     * If a castling right is absent, the corresponding pieces should be marked as moved
     */
    internal fun markPiecesBasedOnCastlingRights(): ChessPosition {
        var result = this

        // White king should be marked as moved if neither castling right is available
        if (!castlingRights.canCastleKingside(PlayerSide.WHITE) && !castlingRights.canCastleQueenside(PlayerSide.WHITE)) {
            result = result.markPieceAsMoved(Position.fromAlgebraic("e1"))
        }

        // Black king should be marked as moved if neither castling right is available
        if (!castlingRights.canCastleKingside(PlayerSide.BLACK) && !castlingRights.canCastleQueenside(PlayerSide.BLACK)) {
            result = result.markPieceAsMoved(Position.fromAlgebraic("e8"))
        }

        // White kingside rook should be marked as moved if right not available
        if (!castlingRights.canCastleKingside(PlayerSide.WHITE)) {
            result = result.markPieceAsMoved(Position.fromAlgebraic("h1"))
        }

        // White queenside rook should be marked as moved if right not available
        if (!castlingRights.canCastleQueenside(PlayerSide.WHITE)) {
            result = result.markPieceAsMoved(Position.fromAlgebraic("a1"))
        }

        // Black kingside rook should be marked as moved if right not available
        if (!castlingRights.canCastleKingside(PlayerSide.BLACK)) {
            result = result.markPieceAsMoved(Position.fromAlgebraic("h8"))
        }

        // Black queenside rook should be marked as moved if right not available
        if (!castlingRights.canCastleQueenside(PlayerSide.BLACK)) {
            result = result.markPieceAsMoved(Position.fromAlgebraic("a8"))
        }

        return result
    }

    /**
     * Marks a piece at the given position as moved
     */
    private fun markPieceAsMoved(position: Position): ChessPosition {
        val piece = pieceAt(position) ?: return this
        return setPiece(position, piece.withMoved())
    }

    companion object {
        /**
         * Singleton instance of StandardChessRules for move generation.
         * Since StandardChessRules is stateless, we can safely reuse the same instance.
         */
        private val standardRules = StandardChessRules()

        /**
         * Converts a Position to a bit index (0-63)
         */
        fun positionToIndex(position: Position): Int {
            return position.rank * 8 + position.file
        }

        /**
         * Converts a Position to a bit mask (single bit set)
         */
        fun positionToBit(position: Position): Long {
            return 1L shl positionToIndex(position)
        }

        /**
         * Converts a bit index (0-63) to a Position
         */
        fun indexToPosition(index: Int): Position {
            require(index in 0..63) { "Index must be between 0 and 63" }
            return Position(index % 8, index / 8)
        }

        /**
         * Creates a ChessPosition with the initial chess position
         */
        fun initial(): ChessPosition {
            return ChessPosition(
                // White pieces
                whitePawns = 0x000000000000FF00L,   // rank 2
                whiteKnights = 0x0000000000000042L, // b1, g1
                whiteBishops = 0x0000000000000024L, // c1, f1
                whiteRooks = 0x0000000000000081L,   // a1, h1
                whiteQueens = 0x0000000000000008L,  // d1
                whiteKings = 0x0000000000000010L,   // e1

                // Black pieces
                blackPawns = 0x00FF000000000000L,   // rank 7
                blackKnights = (0x42L shl 56),      // b8, g8
                blackBishops = (0x24L shl 56),      // c8, f8
                blackRooks = (0x81L shl 56),        // a8, h8
                blackQueens = (0x08L shl 56),       // d8
                blackKings = (0x10L shl 56),        // e8

                // Castling rights (all available at start)
                castlingRights = CastlingRights.ALL
            )
        }

        /**
         * Prints a visual representation of a bitboard (for debugging)
         */
        fun visualize(bitboard: Long): String {
            val sb = StringBuilder()
            for (rank in 7 downTo 0) {
                sb.append("${rank + 1} ")
                for (file in 0..7) {
                    val bit = 1L shl (rank * 8 + file)
                    sb.append(if ((bitboard and bit) != 0L) "1 " else ". ")
                }
                sb.append("\n")
            }
            sb.append("  a b c d e f g h\n")
            return sb.toString()
        }
    }
}

/**
 * Extension function to convert a FEN string to a ChessPosition
 *
 * @receiver FEN string (e.g., "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
 * @return ChessPosition representing the position
 * @throws IllegalArgumentException if the FEN string is invalid
 */
fun String.toChessPosition(): ChessPosition {
    val parts = this.trim().split(" ")
    require(parts.size == 6) { "Invalid FEN: must have 6 parts separated by spaces" }

    val positionPart = parts[0]
    var bitBoard = ChessPosition()

    // Parse piece positions
    val ranks = positionPart.split("/")
    require(ranks.size == 8) { "Invalid FEN: must have 8 ranks separated by /" }

    for ((rankIndex, rankString) in ranks.withIndex()) {
        val rank = 7 - rankIndex // FEN starts from rank 8 down to rank 1
        var file = 0

        for (char in rankString) {
            when {
                char.isDigit() -> {
                    // Empty squares
                    file += char.digitToInt()
                }
                else -> {
                    // Piece
                    require(file < 8) { "Invalid FEN: too many squares in rank ${rank + 1}" }
                    val piece = char.toPiece()
                    bitBoard = bitBoard.setPiece(Position(file, rank), piece)
                    file++
                }
            }
        }
        require(file == 8) { "Invalid FEN: rank ${rank + 1} has $file squares instead of 8" }
    }

    // Parse active color (side to move)
    val activeColor = when (parts[1]) {
        "w" -> PlayerSide.WHITE
        "b" -> PlayerSide.BLACK
        else -> throw IllegalArgumentException("Invalid FEN: active color must be 'w' or 'b', got '${parts[1]}'")
    }

    // Parse castling rights and update hasMoved flags
    val castlingRights = parts[2]

    // Parse en passant square
    val enPassant = if (parts[3] == "-") null else parts[3]

    // Parse halfmove clock
    val halfmove = parts[4].toIntOrNull() ?: throw IllegalArgumentException("Invalid FEN: halfmove clock must be a number, got '${parts[4]}'")

    // Parse fullmove number
    val fullmove = parts[5].toIntOrNull() ?: throw IllegalArgumentException("Invalid FEN: fullmove number must be a number, got '${parts[5]}'")

    bitBoard = bitBoard.updateCastlingRights(castlingRights)
        .markPiecesBasedOnCastlingRights()
        .copy(
            sideToMove = activeColor,
            enPassantSquare = enPassant,
            halfmoveClock = halfmove,
            fullmoveNumber = fullmove
        )

    return bitBoard
}

/**
 * Converts a FEN character to a Piece
 */
private fun Char.toPiece(): Piece {
    val side = if (this.isUpperCase()) PlayerSide.WHITE else PlayerSide.BLACK
    val type = when (this.uppercaseChar()) {
        'P' -> PieceType.PAWN
        'N' -> PieceType.KNIGHT
        'B' -> PieceType.BISHOP
        'R' -> PieceType.ROOK
        'Q' -> PieceType.QUEEN
        'K' -> PieceType.KING
        else -> throw IllegalArgumentException("Invalid piece character in FEN: $this")
    }
    return Piece(type, side, hasMoved = false)
}
