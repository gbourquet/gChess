package com.gchess.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.assertions.throwables.shouldThrow

class BitBoardTest : StringSpec({

    "initial board should have correct white pawns" {
        val bitBoard = BitBoard.initial()

        for (file in 0..7) {
            val position = Position(file, 1)
            val piece = bitBoard.pieceAt(position)
            piece.shouldNotBeNull()
            piece.type shouldBe PieceType.PAWN
            piece.color shouldBe Color.WHITE
        }
    }

    "initial board should have correct black pawns" {
        val bitBoard = BitBoard.initial()

        for (file in 0..7) {
            val position = Position(file, 6)
            val piece = bitBoard.pieceAt(position)
            piece.shouldNotBeNull()
            piece.type shouldBe PieceType.PAWN
            piece.color shouldBe Color.BLACK
        }
    }

    "initial board should have all pieces in correct positions" {
        val bitBoard = BitBoard.initial()

        // White back rank
        bitBoard.pieceAt(Position.fromAlgebraic("a1"))?.type shouldBe PieceType.ROOK
        bitBoard.pieceAt(Position.fromAlgebraic("b1"))?.type shouldBe PieceType.KNIGHT
        bitBoard.pieceAt(Position.fromAlgebraic("c1"))?.type shouldBe PieceType.BISHOP
        bitBoard.pieceAt(Position.fromAlgebraic("d1"))?.type shouldBe PieceType.QUEEN
        bitBoard.pieceAt(Position.fromAlgebraic("e1"))?.type shouldBe PieceType.KING
        bitBoard.pieceAt(Position.fromAlgebraic("f1"))?.type shouldBe PieceType.BISHOP
        bitBoard.pieceAt(Position.fromAlgebraic("g1"))?.type shouldBe PieceType.KNIGHT
        bitBoard.pieceAt(Position.fromAlgebraic("h1"))?.type shouldBe PieceType.ROOK

        // Black back rank
        bitBoard.pieceAt(Position.fromAlgebraic("a8"))?.type shouldBe PieceType.ROOK
        bitBoard.pieceAt(Position.fromAlgebraic("b8"))?.type shouldBe PieceType.KNIGHT
        bitBoard.pieceAt(Position.fromAlgebraic("c8"))?.type shouldBe PieceType.BISHOP
        bitBoard.pieceAt(Position.fromAlgebraic("d8"))?.type shouldBe PieceType.QUEEN
        bitBoard.pieceAt(Position.fromAlgebraic("e8"))?.type shouldBe PieceType.KING
        bitBoard.pieceAt(Position.fromAlgebraic("f8"))?.type shouldBe PieceType.BISHOP
        bitBoard.pieceAt(Position.fromAlgebraic("g8"))?.type shouldBe PieceType.KNIGHT
        bitBoard.pieceAt(Position.fromAlgebraic("h8"))?.type shouldBe PieceType.ROOK
    }

    "initial board should have empty squares in the middle" {
        val bitBoard = BitBoard.initial()

        for (rank in 2..5) {
            for (file in 0..7) {
                val position = Position(file, rank)
                bitBoard.pieceAt(position).shouldBeNull()
                bitBoard.isOccupied(position) shouldBe false
            }
        }
    }

    "setPiece should place piece at correct position" {
        val bitBoard = BitBoard()
        val position = Position.fromAlgebraic("e4")
        val piece = Piece(PieceType.KNIGHT, Color.WHITE)

        val newBoard = bitBoard.setPiece(position, piece)

        newBoard.pieceAt(position) shouldBe piece
        newBoard.isOccupied(position) shouldBe true
    }

    "setPiece with null should remove piece" {
        val bitBoard = BitBoard.initial()
        val position = Position.fromAlgebraic("e2")

        bitBoard.pieceAt(position).shouldNotBeNull()

        val newBoard = bitBoard.setPiece(position, null)

        newBoard.pieceAt(position).shouldBeNull()
        newBoard.isOccupied(position) shouldBe false
    }

    "setPiece should replace piece at occupied square" {
        val bitBoard = BitBoard.initial()
        val position = Position.fromAlgebraic("e2")
        val newPiece = Piece(PieceType.QUEEN, Color.BLACK)

        val oldPiece = bitBoard.pieceAt(position)
        oldPiece.shouldNotBeNull()
        oldPiece.type shouldBe PieceType.PAWN

        val newBoard = bitBoard.setPiece(position, newPiece)
        val replacedPiece = newBoard.pieceAt(position)

        replacedPiece.shouldNotBeNull()
        replacedPiece.type shouldBe PieceType.QUEEN
        replacedPiece.color shouldBe Color.BLACK
    }

    "movePiece should move piece from one square to another" {
        val bitBoard = BitBoard.initial()
        val from = Position.fromAlgebraic("e2")
        val to = Position.fromAlgebraic("e4")

        val originalPiece = bitBoard.pieceAt(from)
        originalPiece.shouldNotBeNull()

        val newBoard = bitBoard.movePiece(from, to)

        newBoard.pieceAt(from).shouldBeNull()
        newBoard.pieceAt(to).shouldNotBeNull()
        newBoard.pieceAt(to)?.type shouldBe originalPiece.type
        newBoard.pieceAt(to)?.color shouldBe originalPiece.color
    }

    "movePiece should mark piece as moved" {
        val bitBoard = BitBoard.initial()
        val from = Position.fromAlgebraic("e2")
        val to = Position.fromAlgebraic("e4")

        val originalPiece = bitBoard.pieceAt(from)
        originalPiece.shouldNotBeNull()
        originalPiece.hasMoved shouldBe false

        val newBoard = bitBoard.movePiece(from, to)
        val movedPiece = newBoard.pieceAt(to)

        movedPiece.shouldNotBeNull()
        movedPiece.hasMoved shouldBe true
    }

    "movePiece from empty square should return unchanged board" {
        val bitBoard = BitBoard.initial()
        val from = Position.fromAlgebraic("e4")
        val to = Position.fromAlgebraic("e5")

        val newBoard = bitBoard.movePiece(from, to)

        newBoard shouldBe bitBoard
    }

    "capture move should remove captured piece" {
        val bitBoard = BitBoard.initial()
        val afterFirstMove = bitBoard.movePiece(
            Position.fromAlgebraic("e2"),
            Position.fromAlgebraic("e4")
        )
        val withBlackPiece = afterFirstMove.setPiece(
            Position.fromAlgebraic("d5"),
            Piece(PieceType.PAWN, Color.BLACK)
        )
        val afterCapture = withBlackPiece.movePiece(
            Position.fromAlgebraic("e4"),
            Position.fromAlgebraic("d5")
        )

        afterCapture.pieceAt(Position.fromAlgebraic("e4")).shouldBeNull()
        val pieceAtD5 = afterCapture.pieceAt(Position.fromAlgebraic("d5"))
        pieceAtD5.shouldNotBeNull()
        pieceAtD5.color shouldBe Color.WHITE
    }

    "whitePieces should return all white pieces" {
        val bitBoard = BitBoard.initial()
        val whitePieces = bitBoard.whitePieces()

        whitePieces.countOneBits() shouldBe 16
    }

    "blackPieces should return all black pieces" {
        val bitBoard = BitBoard.initial()
        val blackPieces = bitBoard.blackPieces()

        blackPieces.countOneBits() shouldBe 16
    }

    "occupiedSquares should return all occupied squares" {
        val bitBoard = BitBoard.initial()
        val occupied = bitBoard.occupiedSquares()

        occupied.countOneBits() shouldBe 32
    }

    "emptySquares should return all empty squares" {
        val bitBoard = BitBoard.initial()
        val empty = bitBoard.emptySquares()

        empty.countOneBits() shouldBe 32
    }

    "getAllPieces should return all pieces with positions" {
        val bitBoard = BitBoard.initial()
        val pieces = bitBoard.getAllPieces()

        pieces.size shouldBe 32
    }

    "getPiecesByColor should return only white pieces" {
        val bitBoard = BitBoard.initial()
        val whitePieces = bitBoard.getPiecesByColor(Color.WHITE)

        whitePieces.size shouldBe 16
        whitePieces.values.all { it.color == Color.WHITE } shouldBe true
    }

    "getPiecesByColor should return only black pieces" {
        val bitBoard = BitBoard.initial()
        val blackPieces = bitBoard.getPiecesByColor(Color.BLACK)

        blackPieces.size shouldBe 16
        blackPieces.values.all { it.color == Color.BLACK } shouldBe true
    }

    "positionToIndex should convert position to correct index" {
        BitBoard.positionToIndex(Position(0, 0)) shouldBe 0
        BitBoard.positionToIndex(Position(7, 0)) shouldBe 7
        BitBoard.positionToIndex(Position(0, 1)) shouldBe 8
        BitBoard.positionToIndex(Position(7, 7)) shouldBe 63
    }

    "indexToPosition should convert index to correct position" {
        BitBoard.indexToPosition(0) shouldBe Position(0, 0)
        BitBoard.indexToPosition(7) shouldBe Position(7, 0)
        BitBoard.indexToPosition(8) shouldBe Position(0, 1)
        BitBoard.indexToPosition(63) shouldBe Position(7, 7)
    }

    "indexToPosition should throw for invalid index" {
        shouldThrow<IllegalArgumentException> {
            BitBoard.indexToPosition(-1)
        }
        shouldThrow<IllegalArgumentException> {
            BitBoard.indexToPosition(64)
        }
    }

    "positionToBit should create correct bit mask" {
        BitBoard.positionToBit(Position(0, 0)) shouldBe 1L
        BitBoard.positionToBit(Position(7, 0)) shouldBe (1L shl 7)
        BitBoard.positionToBit(Position(7, 7)) shouldBe (1L shl 63)
    }

    "visualize should produce readable output" {
        val bitboard = 0x00000000000000FFL
        val visualization = BitBoard.visualize(bitboard)

        visualization.shouldNotBeNull()
        visualization.contains("a b c d e f g h") shouldBe true
        visualization.contains("1") shouldBe true
    }

    "empty BitBoard should have no pieces" {
        val bitBoard = BitBoard()

        bitBoard.whitePieces() shouldBe 0L
        bitBoard.blackPieces() shouldBe 0L
        bitBoard.occupiedSquares() shouldBe 0L
        bitBoard.getAllPieces().size shouldBe 0
    }

    "initial position should generate correct FEN" {
        val bitBoard = BitBoard.initial()
        val fen = bitBoard.toFen()

        fen shouldBe "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }

    "toFen should include en passant square when provided" {
        val bitBoard = BitBoard.initial().copy(enPassantSquare = "e3")
        val fen = bitBoard.toFen()

        fen.split(" ")[3] shouldBe "e3"
    }

    "toFen should show no en passant when not provided" {
        val bitBoard = BitBoard.initial()
        val fen = bitBoard.toFen()

        fen.split(" ")[3] shouldBe "-"
    }

    "toFen should include halfmove clock" {
        val bitBoard = BitBoard.initial().copy(halfmoveClock = 5)
        val fen = bitBoard.toFen()

        fen.split(" ")[4] shouldBe "5"
    }

    "toFen should include fullmove number" {
        val bitBoard = BitBoard.initial().copy(fullmoveNumber = 42)
        val fen = bitBoard.toFen()

        fen.split(" ")[5] shouldBe "42"
    }

    "empty board should generate correct FEN" {
        val bitBoard = BitBoard()
        val fen = bitBoard.toFen()

        fen shouldBe "8/8/8/8/8/8/8/8 w - - 0 1"
    }

    "castling rights should be correct after king moves" {
        val bitBoard = BitBoard.initial()
            .movePiece(Position.fromAlgebraic("e1"), Position.fromAlgebraic("e2"))
        val fen = bitBoard.toFen()

        val castlingRights = fen.split(" ")[2]
        castlingRights.contains('K') shouldBe false
        castlingRights.contains('Q') shouldBe false
        castlingRights.contains('k') shouldBe true
        castlingRights.contains('q') shouldBe true
    }

    "castling rights should be correct after rook moves" {
        val bitBoard = BitBoard.initial()
            .movePiece(Position.fromAlgebraic("h1"), Position.fromAlgebraic("h2"))
        val fen = bitBoard.toFen()

        val castlingRights = fen.split(" ")[2]
        castlingRights.contains('K') shouldBe false
        castlingRights.contains('Q') shouldBe true
        castlingRights.contains('k') shouldBe true
        castlingRights.contains('q') shouldBe true
    }

    "position with custom pieces should generate correct FEN" {
        val bitBoard = BitBoard()
            .setPiece(Position.fromAlgebraic("e4"), Piece(PieceType.KING, Color.WHITE))
            .setPiece(Position.fromAlgebraic("e8"), Piece(PieceType.KING, Color.BLACK))
            .setPiece(Position.fromAlgebraic("d1"), Piece(PieceType.QUEEN, Color.WHITE))

        val fen = bitBoard.toFen()
        val position = fen.split(" ")[0]

        position shouldBe "4k3/8/8/8/4K3/8/8/3Q4"
    }

    "FEN should count consecutive empty squares correctly" {
        val bitBoard = BitBoard()
            .setPiece(Position.fromAlgebraic("a8"), Piece(PieceType.ROOK, Color.BLACK))
            .setPiece(Position.fromAlgebraic("h8"), Piece(PieceType.ROOK, Color.BLACK))

        val fen = bitBoard.toFen()
        val position = fen.split(" ")[0]

        position shouldBe "r6r/8/8/8/8/8/8/8"
    }

    "String.toBitBoard should parse initial position FEN" {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val bitBoard = fen.toBitBoard()

        bitBoard.getAllPieces().size shouldBe 32
        bitBoard.pieceAt(Position.fromAlgebraic("e1"))?.type shouldBe PieceType.KING
        bitBoard.pieceAt(Position.fromAlgebraic("e8"))?.type shouldBe PieceType.KING
        bitBoard.pieceAt(Position.fromAlgebraic("e2"))?.type shouldBe PieceType.PAWN
    }

    "String.toBitBoard should parse empty board FEN" {
        val fen = "8/8/8/8/8/8/8/8 w - - 0 1"
        val bitBoard = fen.toBitBoard()

        bitBoard.getAllPieces().size shouldBe 0
        bitBoard.occupiedSquares() shouldBe 0L
    }

    "String.toBitBoard should parse custom position FEN" {
        val fen = "4k3/8/8/8/4K3/8/8/3Q4 w - - 0 1"
        val bitBoard = fen.toBitBoard()

        bitBoard.pieceAt(Position.fromAlgebraic("e4"))?.type shouldBe PieceType.KING
        bitBoard.pieceAt(Position.fromAlgebraic("e4"))?.color shouldBe Color.WHITE
        bitBoard.pieceAt(Position.fromAlgebraic("e8"))?.type shouldBe PieceType.KING
        bitBoard.pieceAt(Position.fromAlgebraic("e8"))?.color shouldBe Color.BLACK
        bitBoard.pieceAt(Position.fromAlgebraic("d1"))?.type shouldBe PieceType.QUEEN
        bitBoard.pieceAt(Position.fromAlgebraic("d1"))?.color shouldBe Color.WHITE
    }

    "String.toBitBoard should handle castling rights correctly" {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val bitBoard = fen.toBitBoard()

        // All pieces should not be marked as moved
        bitBoard.pieceAt(Position.fromAlgebraic("e1"))?.hasMoved shouldBe false
        bitBoard.pieceAt(Position.fromAlgebraic("h1"))?.hasMoved shouldBe false
        bitBoard.pieceAt(Position.fromAlgebraic("a1"))?.hasMoved shouldBe false
    }

    "String.toBitBoard should handle no castling rights" {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1"
        val bitBoard = fen.toBitBoard()

        // Kings and rooks should be marked as moved
        bitBoard.pieceAt(Position.fromAlgebraic("e1"))?.hasMoved shouldBe true
        bitBoard.pieceAt(Position.fromAlgebraic("e8"))?.hasMoved shouldBe true
        bitBoard.pieceAt(Position.fromAlgebraic("a1"))?.hasMoved shouldBe true
        bitBoard.pieceAt(Position.fromAlgebraic("h1"))?.hasMoved shouldBe true
        bitBoard.pieceAt(Position.fromAlgebraic("a8"))?.hasMoved shouldBe true
        bitBoard.pieceAt(Position.fromAlgebraic("h8"))?.hasMoved shouldBe true
    }

    "String.toBitBoard should handle partial castling rights" {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w Kq - 0 1"
        val bitBoard = fen.toBitBoard()

        // White kingside rook and black queenside rook should not be marked as moved
        bitBoard.pieceAt(Position.fromAlgebraic("h1"))?.hasMoved shouldBe false
        bitBoard.pieceAt(Position.fromAlgebraic("a8"))?.hasMoved shouldBe false

        // Other rooks should be marked as moved
        bitBoard.pieceAt(Position.fromAlgebraic("a1"))?.hasMoved shouldBe true
        bitBoard.pieceAt(Position.fromAlgebraic("h8"))?.hasMoved shouldBe true
    }

    "String.toBitBoard should throw for invalid FEN with wrong number of parts" {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq"

        shouldThrow<IllegalArgumentException> {
            fen.toBitBoard()
        }
    }

    "String.toBitBoard should throw for invalid FEN with wrong number of ranks" {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1"

        shouldThrow<IllegalArgumentException> {
            fen.toBitBoard()
        }
    }

    "String.toBitBoard should throw for invalid piece character" {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPXPPP/RNBQKBNR w KQkq - 0 1"

        shouldThrow<IllegalArgumentException> {
            fen.toBitBoard()
        }
    }

    "round trip FEN conversion should preserve position" {
        val originalFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val bitBoard = originalFen.toBitBoard()
        val reconstructedFen = bitBoard.toFen()

        reconstructedFen shouldBe originalFen
    }

    "round trip FEN conversion with custom position should preserve position" {
        val originalFen = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
        val bitBoard = originalFen.toBitBoard()
        val reconstructedFen = bitBoard.toFen()

        reconstructedFen shouldBe originalFen
    }

    "movePiece should increment halfmove clock for non-pawn, non-capture moves" {
        val bitBoard = BitBoard.initial().copy(halfmoveClock = 3)
        val newBoard = bitBoard.movePiece(
            Position.fromAlgebraic("b1"),
            Position.fromAlgebraic("c3")
        )

        newBoard.halfmoveClock shouldBe 4
    }

    "movePiece should reset halfmove clock on pawn move" {
        val bitBoard = BitBoard.initial().copy(halfmoveClock = 5)
        val newBoard = bitBoard.movePiece(
            Position.fromAlgebraic("e2"),
            Position.fromAlgebraic("e4")
        )

        newBoard.halfmoveClock shouldBe 0
    }

    "movePiece should reset halfmove clock on capture" {
        val bitBoard = BitBoard.initial()
            .copy(halfmoveClock = 5)
            .movePiece(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"))
            .setPiece(Position.fromAlgebraic("d5"), Piece(PieceType.PAWN, Color.BLACK))

        val newBoard = bitBoard.movePiece(
            Position.fromAlgebraic("e4"),
            Position.fromAlgebraic("d5")
        )

        newBoard.halfmoveClock shouldBe 0
    }

    "movePiece should not increment fullmove number after White's move" {
        val bitBoard = BitBoard.initial().copy(fullmoveNumber = 10)
        val newBoard = bitBoard.movePiece(
            Position.fromAlgebraic("e2"),
            Position.fromAlgebraic("e4")
        )

        newBoard.fullmoveNumber shouldBe 10
    }

    "movePiece should increment fullmove number after Black's move" {
        val bitBoard = BitBoard.initial()
            .copy(sideToMove = Color.BLACK, fullmoveNumber = 10)
        val newBoard = bitBoard.movePiece(
            Position.fromAlgebraic("e7"),
            Position.fromAlgebraic("e5")
        )

        newBoard.fullmoveNumber shouldBe 11
    }
})
