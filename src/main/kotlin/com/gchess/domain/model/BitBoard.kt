package com.gchess.domain.model

/**
 * Bitboard representation of a chess board.
 * Uses 64-bit integers where each bit represents a square on the board.
 * Bit 0 = a1, Bit 1 = b1, ..., Bit 7 = h1, Bit 8 = a2, ..., Bit 63 = h8
 *
 * This is an efficient representation for chess engines, allowing fast
 * bitwise operations for move generation and board evaluation.
 */
data class BitBoard(
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
    val sideToMove: Color = Color.WHITE, // Which side has the move
    val whiteKingSideCastle: Boolean = false, // White can castle kingside
    val whiteQueenSideCastle: Boolean = false, // White can castle queenside
    val blackKingSideCastle: Boolean = false, // Black can castle kingside
    val blackQueenSideCastle: Boolean = false, // Black can castle queenside
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
            (whitePawns and bit) != 0L -> Piece(PieceType.PAWN, Color.WHITE, hasMoved)
            (whiteKnights and bit) != 0L -> Piece(PieceType.KNIGHT, Color.WHITE, hasMoved)
            (whiteBishops and bit) != 0L -> Piece(PieceType.BISHOP, Color.WHITE, hasMoved)
            (whiteRooks and bit) != 0L -> Piece(PieceType.ROOK, Color.WHITE, hasMoved)
            (whiteQueens and bit) != 0L -> Piece(PieceType.QUEEN, Color.WHITE, hasMoved)
            (whiteKings and bit) != 0L -> Piece(PieceType.KING, Color.WHITE, hasMoved)
            (blackPawns and bit) != 0L -> Piece(PieceType.PAWN, Color.BLACK, hasMoved)
            (blackKnights and bit) != 0L -> Piece(PieceType.KNIGHT, Color.BLACK, hasMoved)
            (blackBishops and bit) != 0L -> Piece(PieceType.BISHOP, Color.BLACK, hasMoved)
            (blackRooks and bit) != 0L -> Piece(PieceType.ROOK, Color.BLACK, hasMoved)
            (blackQueens and bit) != 0L -> Piece(PieceType.QUEEN, Color.BLACK, hasMoved)
            (blackKings and bit) != 0L -> Piece(PieceType.KING, Color.BLACK, hasMoved)
            else -> null
        }
    }

    /**
     * Sets a piece at a given position
     */
    fun setPiece(position: Position, piece: Piece?): BitBoard {
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

            when (piece.type to piece.color) {
                PieceType.PAWN to Color.WHITE -> clearedBoard.copy(
                    whitePawns = clearedBoard.whitePawns or bit,
                    movedPieces = movedBits
                )
                PieceType.KNIGHT to Color.WHITE -> clearedBoard.copy(
                    whiteKnights = clearedBoard.whiteKnights or bit,
                    movedPieces = movedBits
                )
                PieceType.BISHOP to Color.WHITE -> clearedBoard.copy(
                    whiteBishops = clearedBoard.whiteBishops or bit,
                    movedPieces = movedBits
                )
                PieceType.ROOK to Color.WHITE -> clearedBoard.copy(
                    whiteRooks = clearedBoard.whiteRooks or bit,
                    movedPieces = movedBits
                )
                PieceType.QUEEN to Color.WHITE -> clearedBoard.copy(
                    whiteQueens = clearedBoard.whiteQueens or bit,
                    movedPieces = movedBits
                )
                PieceType.KING to Color.WHITE -> clearedBoard.copy(
                    whiteKings = clearedBoard.whiteKings or bit,
                    movedPieces = movedBits
                )
                PieceType.PAWN to Color.BLACK -> clearedBoard.copy(
                    blackPawns = clearedBoard.blackPawns or bit,
                    movedPieces = movedBits
                )
                PieceType.KNIGHT to Color.BLACK -> clearedBoard.copy(
                    blackKnights = clearedBoard.blackKnights or bit,
                    movedPieces = movedBits
                )
                PieceType.BISHOP to Color.BLACK -> clearedBoard.copy(
                    blackBishops = clearedBoard.blackBishops or bit,
                    movedPieces = movedBits
                )
                PieceType.ROOK to Color.BLACK -> clearedBoard.copy(
                    blackRooks = clearedBoard.blackRooks or bit,
                    movedPieces = movedBits
                )
                PieceType.QUEEN to Color.BLACK -> clearedBoard.copy(
                    blackQueens = clearedBoard.blackQueens or bit,
                    movedPieces = movedBits
                )
                PieceType.KING to Color.BLACK -> clearedBoard.copy(
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
    private fun clearSquare(position: Position): BitBoard {
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
     */
    fun movePiece(from: Position, to: Position): BitBoard {
        val piece = pieceAt(from) ?: return this
        val isCapture = pieceAt(to) != null
        val isPawnMove = piece.type == PieceType.PAWN

        // Calculate en passant square if a pawn moves two squares
        val newEnPassant = if (isPawnMove) {
            val rankDiff = to.rank - from.rank
            when {
                // White pawn moves from rank 1 (index 1) to rank 3 (index 3)
                piece.color == Color.WHITE && rankDiff == 2 && from.rank == 1 -> {
                    Position(from.file, 2).toAlgebraic()
                }
                // Black pawn moves from rank 6 (index 6) to rank 4 (index 4)
                piece.color == Color.BLACK && rankDiff == -2 && from.rank == 6 -> {
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
        val newFullmoveNumber = if (sideToMove == Color.BLACK) fullmoveNumber + 1 else fullmoveNumber

        var newBoard = setPiece(from, null)
            .setPiece(to, piece.withMoved())
            .copy(
                sideToMove = sideToMove.opposite(),
                enPassantSquare = newEnPassant,
                halfmoveClock = newHalfmoveClock,
                fullmoveNumber = newFullmoveNumber
            )

        // Update castling rights based on piece type and position
        when {
            // White king moves - lose both white castling rights
            piece.type == PieceType.KING && piece.color == Color.WHITE -> {
                newBoard = newBoard.copy(
                    whiteKingSideCastle = false,
                    whiteQueenSideCastle = false
                )
            }
            // Black king moves - lose both black castling rights
            piece.type == PieceType.KING && piece.color == Color.BLACK -> {
                newBoard = newBoard.copy(
                    blackKingSideCastle = false,
                    blackQueenSideCastle = false
                )
            }
            // White kingside rook moves
            piece.type == PieceType.ROOK && piece.color == Color.WHITE && from == Position.fromAlgebraic("h1") -> {
                newBoard = newBoard.copy(whiteKingSideCastle = false)
            }
            // White queenside rook moves
            piece.type == PieceType.ROOK && piece.color == Color.WHITE && from == Position.fromAlgebraic("a1") -> {
                newBoard = newBoard.copy(whiteQueenSideCastle = false)
            }
            // Black kingside rook moves
            piece.type == PieceType.ROOK && piece.color == Color.BLACK && from == Position.fromAlgebraic("h8") -> {
                newBoard = newBoard.copy(blackKingSideCastle = false)
            }
            // Black queenside rook moves
            piece.type == PieceType.ROOK && piece.color == Color.BLACK && from == Position.fromAlgebraic("a8") -> {
                newBoard = newBoard.copy(blackQueenSideCastle = false)
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
     * Gets all pieces of a given color
     */
    fun getPiecesByColor(color: Color): Map<Position, Piece> {
        return getAllPieces().filterValues { it.color == color }
    }

    /**
     * Converts this BitBoard to FEN (Forsyth-Edwards Notation)
     *
     * FEN format: position activeColor castling enPassant halfmove fullmove
     *
     * @return FEN string representing this position
     */
    fun toFen(): String {
        val position = buildPositionString()
        val activeColorChar = if (sideToMove == Color.WHITE) "w" else "b"
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
        var rights = ""

        if (whiteKingSideCastle) rights += "K"
        if (whiteQueenSideCastle) rights += "Q"
        if (blackKingSideCastle) rights += "k"
        if (blackQueenSideCastle) rights += "q"

        return rights.ifEmpty { "-" }
    }

    /**
     * Sets the castling rights based on FEN notation
     */
    internal fun updateCastlingRights(castlingRights: String): BitBoard {
        return copy(
            whiteKingSideCastle = castlingRights.contains('K'),
            whiteQueenSideCastle = castlingRights.contains('Q'),
            blackKingSideCastle = castlingRights.contains('k'),
            blackQueenSideCastle = castlingRights.contains('q')
        )
    }

    /**
     * Marks pieces (kings and rooks) as moved based on castling rights
     * If a castling right is absent, the corresponding pieces should be marked as moved
     */
    internal fun markPiecesBasedOnCastlingRights(castlingRights: String): BitBoard {
        var result = this

        // White king should be marked as moved if neither K nor Q is present
        if (!castlingRights.contains('K') && !castlingRights.contains('Q')) {
            result = result.markPieceAsMoved(Position.fromAlgebraic("e1"))
        }

        // Black king should be marked as moved if neither k nor q is present
        if (!castlingRights.contains('k') && !castlingRights.contains('q')) {
            result = result.markPieceAsMoved(Position.fromAlgebraic("e8"))
        }

        // White kingside rook should be marked as moved if K is not present
        if (!castlingRights.contains('K')) {
            result = result.markPieceAsMoved(Position.fromAlgebraic("h1"))
        }

        // White queenside rook should be marked as moved if Q is not present
        if (!castlingRights.contains('Q')) {
            result = result.markPieceAsMoved(Position.fromAlgebraic("a1"))
        }

        // Black kingside rook should be marked as moved if k is not present
        if (!castlingRights.contains('k')) {
            result = result.markPieceAsMoved(Position.fromAlgebraic("h8"))
        }

        // Black queenside rook should be marked as moved if q is not present
        if (!castlingRights.contains('q')) {
            result = result.markPieceAsMoved(Position.fromAlgebraic("a8"))
        }

        return result
    }

    /**
     * Marks a piece at the given position as moved
     */
    private fun markPieceAsMoved(position: Position): BitBoard {
        val piece = pieceAt(position) ?: return this
        return setPiece(position, piece.withMoved())
    }

    companion object {
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
         * Creates a BitBoard with the initial chess position
         */
        fun initial(): BitBoard {
            return BitBoard(
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
                whiteKingSideCastle = true,
                whiteQueenSideCastle = true,
                blackKingSideCastle = true,
                blackQueenSideCastle = true
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
 * Extension function to convert a FEN string to a BitBoard
 *
 * @receiver FEN string (e.g., "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
 * @return BitBoard representing the position
 * @throws IllegalArgumentException if the FEN string is invalid
 */
fun String.toBitBoard(): BitBoard {
    val parts = this.trim().split(" ")
    require(parts.size == 6) { "Invalid FEN: must have 6 parts separated by spaces" }

    val positionPart = parts[0]
    var bitBoard = BitBoard()

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
        "w" -> Color.WHITE
        "b" -> Color.BLACK
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
        .markPiecesBasedOnCastlingRights(castlingRights)
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
    val color = if (this.isUpperCase()) Color.WHITE else Color.BLACK
    val type = when (this.uppercaseChar()) {
        'P' -> PieceType.PAWN
        'N' -> PieceType.KNIGHT
        'B' -> PieceType.BISHOP
        'R' -> PieceType.ROOK
        'Q' -> PieceType.QUEEN
        'K' -> PieceType.KING
        else -> throw IllegalArgumentException("Invalid piece character in FEN: $this")
    }
    return Piece(type, color, hasMoved = false)
}
