package com.gchess.chess.domain.service

import com.gchess.chess.domain.model.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for StandardChessRules Domain Service.
 * Tests the business logic of chess move generation and validation.
 */
class StandardChessRulesTest : StringSpec({

    val rules = StandardChessRules()

    // ========== Pawn Move Generation Tests ==========

    "should generate single square pawn advance for white" {
        // Arrange: White pawn on e2, nothing blocking
        val position = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e2") }

        // Assert: Pawn can move one square forward to e3
        pawnMoves.any { it.to == Position.fromAlgebraic("e3") } shouldBe true
    }

    "should generate double square pawn advance from starting position for white" {
        // Arrange: White pawn on e2, can move two squares
        val position = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e2") }

        // Assert: Pawn can move two squares forward to e4
        pawnMoves.any { it.to == Position.fromAlgebraic("e4") } shouldBe true
    }

    "should not allow double push if square is blocked" {
        // Arrange: White pawn on e2, black pawn on e4 blocking
        val position = "4k3/8/8/8/4p3/8/4P3/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e2") }

        // Assert: Should only be able to move to e3, not e4
        pawnMoves.any { it.to == Position.fromAlgebraic("e4") } shouldBe false
        pawnMoves.any { it.to == Position.fromAlgebraic("e3") } shouldBe true
    }

    "should not allow pawn advance if directly blocked" {
        // Arrange: White pawn on e2, black pawn on e3 directly blocking
        val position = "4k3/8/8/8/8/4p3/4P3/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e2") }

        // Assert: Pawn is completely blocked
        pawnMoves.isEmpty() shouldBe true
    }

    "should generate pawn captures for white" {
        // Arrange: White pawn on e4, black pawns on d5 and f5
        val position = "4k3/8/8/3p1p2/4P3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Should be able to capture on d5 and f5, and advance to e5
        pawnMoves.any { it.to == Position.fromAlgebraic("d5") } shouldBe true
        pawnMoves.any { it.to == Position.fromAlgebraic("f5") } shouldBe true
        pawnMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
        // Verify position after capturing on d5
        val captureD5 = pawnMoves.first { it.to == Position.fromAlgebraic("d5") }
        val newPosition = position.movePiece(captureD5.from, captureD5.to)
        newPosition.toFen() shouldBe "4k3/8/8/3P1p2/8/8/8/4K3 b - - 0 1"
    }

    "should not allow pawn to capture forward" {
        // Arrange: White pawn on e4, black pawn directly in front on e5
        val position = "4k3/8/8/4p3/4P3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Pawn cannot capture forward or move forward
        pawnMoves.isEmpty() shouldBe true
    }

    "should not allow pawn to capture own pieces" {
        // Arrange: White pawn on e4, white pawns on d5 and f5
        val position = "4k3/8/8/3P1P2/4P3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Should only be able to advance to e5
        pawnMoves.any { it.to == Position.fromAlgebraic("d5") } shouldBe false
        pawnMoves.any { it.to == Position.fromAlgebraic("f5") } shouldBe false
        pawnMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
    }

    "should generate pawn moves for black in correct direction" {
        // Arrange: Black pawn on e7, can move one or two squares down
        val position = "4k3/4p3/8/8/8/8/8/4K3 b - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e7") }

        // Assert: Black pawns move down (decreasing rank)
        pawnMoves.any { it.to == Position.fromAlgebraic("e6") } shouldBe true
        pawnMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
    }

    "should generate black pawn captures" {
        // Arrange: Black pawn on e5, white pawns on d4 and f4
        val position = "4k3/8/8/4p3/3P1P2/8/8/4K3 b - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e5") }

        // Assert: Should be able to capture on d4 and f4, and advance to e4
        pawnMoves.any { it.to == Position.fromAlgebraic("d4") } shouldBe true
        pawnMoves.any { it.to == Position.fromAlgebraic("f4") } shouldBe true
        pawnMoves.any { it.to == Position.fromAlgebraic("e4") } shouldBe true
    }

    "should handle pawn on edge of board - a file" {
        // Arrange: White pawn on a4, black pawn on b5
        val position = "4k3/8/8/1p6/P7/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("a4") }

        // Assert: Can advance to a5 and capture on b5 (no capture to the left as it's off board)
        pawnMoves.any { it.to == Position.fromAlgebraic("a5") } shouldBe true
        pawnMoves.any { it.to == Position.fromAlgebraic("b5") } shouldBe true
        pawnMoves.size shouldBe 2
    }

    "should handle pawn on edge of board - h file" {
        // Arrange: White pawn on h4, black pawn on g5
        val position = "4k3/8/8/6p1/7P/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("h4") }

        // Assert: Can advance to h5 and capture on g5 (no capture to the right as it's off board)
        pawnMoves.any { it.to == Position.fromAlgebraic("h5") } shouldBe true
        pawnMoves.any { it.to == Position.fromAlgebraic("g5") } shouldBe true
        pawnMoves.size shouldBe 2
    }

    "should generate en passant capture for white" {
        // Arrange: White pawn on e5, black pawn just moved from f7 to f5, en passant on f6
        val position = "4k3/8/8/4Pp2/8/8/8/4K3 w - f6 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e5") }

        // Assert: Should be able to capture en passant on f6
        pawnMoves.any { it.to == Position.fromAlgebraic("f6") } shouldBe true
        // Verify position after en passant (white pawn moves to f6, black pawn on f5 is removed)
        val enPassantMove = pawnMoves.first { it.to == Position.fromAlgebraic("f6") }
        val newPosition = position.movePiece(enPassantMove.from, enPassantMove.to)
        newPosition.toFen() shouldBe "4k3/8/5P2/8/8/8/8/4K3 b - - 0 1"
    }

    "should generate en passant capture for black" {
        // Arrange: Black pawn on e4, white pawn just moved from e2 to e4, en passant on f3
        val position = "4k3/8/8/8/4pP2/8/8/4K3 b - f3 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Should be able to capture en passant on f3
        pawnMoves.any { it.to == Position.fromAlgebraic("f3") } shouldBe true
    }

    "should not generate en passant if not available" {
        // Arrange: White pawn on e5, black pawn on f5, but no en passant square set
        val position = "4k3/8/8/4Pp2/8/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e5") }

        // Assert: Should not be able to move to f6 (no en passant, and f5 is occupied)
        pawnMoves.any { it.to == Position.fromAlgebraic("f6") } shouldBe false
    }

    "should handle pawn near promotion rank" {
        // Arrange: White pawn on e7, one square away from promotion (e8 is free)
        val position = "3k4/4P3/8/8/8/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e7") }

        // Assert: Should generate 4 promotion moves (Queen, Rook, Bishop, Knight)
        pawnMoves.filter { it.to == Position.fromAlgebraic("e8") }.size shouldBe 4
        pawnMoves.any { it.promotion == PieceType.QUEEN } shouldBe true
        pawnMoves.any { it.promotion == PieceType.ROOK } shouldBe true
        pawnMoves.any { it.promotion == PieceType.BISHOP } shouldBe true
        pawnMoves.any { it.promotion == PieceType.KNIGHT } shouldBe true
        // Verify position after promotion to Queen (pawn should become queen)
        val promotionMove = pawnMoves.first { it.promotion == PieceType.QUEEN }
        val newPosition = position.movePiece(promotionMove.from, promotionMove.to, promotionMove.promotion)
        newPosition.toFen() shouldBe "3kQ3/8/8/8/8/8/8/4K3 b - - 0 1"
    }

    "should generate promotion captures" {
        // Arrange: White pawn on e7, black pieces on d8 and f8, e8 is free
        val position = "3r1r1k/4P3/8/8/8/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e7") }

        // Assert: Should generate 4 promotions forward + 4 promotions capturing d8 + 4 promotions capturing f8 = 12 total
        val promotionsToD8 = pawnMoves.filter { it.to == Position.fromAlgebraic("d8") }
        val promotionsToE8 = pawnMoves.filter { it.to == Position.fromAlgebraic("e8") }
        val promotionsToF8 = pawnMoves.filter { it.to == Position.fromAlgebraic("f8") }
        promotionsToD8.size shouldBe 4
        promotionsToE8.size shouldBe 4
        promotionsToF8.size shouldBe 4
    }

    "should return empty list for position with no pieces" {
        // Arrange: Empty chess position
        val position = ChessPosition()

        // Act: Generate legal moves
        val allMoves = rules.legalMovesFor(position)

        // Assert: No moves available
        allMoves.isEmpty() shouldBe true
    }

    // ========== Knight Move Generation Tests ==========

    "should generate all 8 knight moves from center" {
        // Arrange: White knight on e4, no obstructions
        val position = "4k3/8/8/8/4N3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the knight
        val allMoves = rules.legalMovesFor(position)
        val knightMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Knight can move to: d6, f6, g5, g3, f2, d2, c3, c5
        knightMoves.size shouldBe 8
        knightMoves.any { it.to == Position.fromAlgebraic("d6") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("f6") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("g5") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("g3") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("f2") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("d2") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("c3") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("c5") } shouldBe true
    }

    "should generate limited knight moves from corner" {
        // Arrange: White knight on a1 (corner)
        val position = "4k3/8/8/8/8/8/8/N3K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the knight
        val allMoves = rules.legalMovesFor(position)
        val knightMoves = allMoves.filter { it.from == Position.fromAlgebraic("a1") }

        // Assert: Knight can only move to: b3, c2
        knightMoves.size shouldBe 2
        knightMoves.any { it.to == Position.fromAlgebraic("b3") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("c2") } shouldBe true
    }

    "should generate limited knight moves from edge" {
        // Arrange: White knight on e1 (bottom edge)
        val position = "4k3/8/8/8/8/8/8/4NK2 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the knight
        val allMoves = rules.legalMovesFor(position)
        val knightMoves = allMoves.filter { it.from == Position.fromAlgebraic("e1") }

        // Assert: Knight can move to: d3, f3, g2, c2
        knightMoves.size shouldBe 4
        knightMoves.any { it.to == Position.fromAlgebraic("d3") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("f3") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("g2") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("c2") } shouldBe true
    }

    "should allow knight to capture enemy pieces" {
        // Arrange: White knight on e4, black pawns on d6 and f6
        val position = "4k3/8/3p1p2/8/4N3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the knight
        val allMoves = rules.legalMovesFor(position)
        val knightMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Should be able to capture on d6 and f6
        knightMoves.any { it.to == Position.fromAlgebraic("d6") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("f6") } shouldBe true
        // Verify position after capturing on d6
        val captureD6 = knightMoves.first { it.to == Position.fromAlgebraic("d6") }
        val newPosition = position.movePiece(captureD6.from, captureD6.to)
        newPosition.toFen() shouldBe "4k3/8/3N1p2/8/8/8/8/4K3 b - - 0 1"
    }

    "should not allow knight to capture own pieces" {
        // Arrange: White knight on e4, white pawns on d6, f6, g5
        val position = "4k3/8/3P1P2/6P1/4N3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the knight
        val allMoves = rules.legalMovesFor(position)
        val knightMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Should not be able to move to d6, f6, or g5
        knightMoves.any { it.to == Position.fromAlgebraic("d6") } shouldBe false
        knightMoves.any { it.to == Position.fromAlgebraic("f6") } shouldBe false
        knightMoves.any { it.to == Position.fromAlgebraic("g5") } shouldBe false
        // But can move to g3, f2, d2, c3, c5
        knightMoves.size shouldBe 5
    }

    "should allow knight to jump over pieces" {
        // Arrange: White knight on e4, surrounded by pieces but can still move
        val position = "4k3/8/8/3ppp2/3pNp2/3ppp2/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the knight
        val allMoves = rules.legalMovesFor(position)
        val knightMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Knight can jump over surrounding pieces to reach d6, f6, g5, g3, f2, d2, c3, c5
        knightMoves.size shouldBe 8
    }

    "should generate knight moves for black" {
        // Arrange: Black knight on e5
        val position = "4k3/8/8/4n3/8/8/8/4K3 b - - 0 1".toChessPosition()

        // Act: Generate legal moves for the knight
        val allMoves = rules.legalMovesFor(position)
        val knightMoves = allMoves.filter { it.from == Position.fromAlgebraic("e5") }

        // Assert: Should have 8 moves
        knightMoves.size shouldBe 8
        knightMoves.any { it.to == Position.fromAlgebraic("d7") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("f7") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("g6") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("g4") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("f3") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("d3") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("c4") } shouldBe true
        knightMoves.any { it.to == Position.fromAlgebraic("c6") } shouldBe true
    }

    // ========== Bishop Move Generation Tests ==========

    "should generate all diagonal moves for bishop from center" {
        // Arrange: White bishop on e4, no obstructions
        val position = "4k3/8/8/8/4B3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the bishop
        val allMoves = rules.legalMovesFor(position)
        val bishopMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Bishop can move diagonally in 4 directions until board edge
        // NE: f5, g6, h7 (3)
        // NW: d5, c6, b7, a8 (4)
        // SE: f3, g2, h1 (3)
        // SW: d3, c2, b1 (3)
        // Total: 13 moves
        bishopMoves.size shouldBe 13
    }

    "should stop bishop at board edge" {
        // Arrange: White bishop on e4
        val position = "4k3/8/8/8/4B3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the bishop
        val allMoves = rules.legalMovesFor(position)
        val bishopMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Check that it reaches the edges
        bishopMoves.any { it.to == Position.fromAlgebraic("a8") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("h7") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("h1") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("b1") } shouldBe true
    }

    "should stop bishop when blocked by own piece" {
        // Arrange: White bishop on e4, white pawn on g6
        val position = "4k3/8/6P1/8/4B3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the bishop
        val allMoves = rules.legalMovesFor(position)
        val bishopMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Should be able to move to f5 but not g6 or h7 (blocked by own pawn)
        bishopMoves.any { it.to == Position.fromAlgebraic("f5") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("g6") } shouldBe false
        bishopMoves.any { it.to == Position.fromAlgebraic("h7") } shouldBe false
    }

    "should stop bishop when capturing enemy piece" {
        // Arrange: White bishop on e4, black pawn on g6
        val position = "4k3/8/6p1/8/4B3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the bishop
        val allMoves = rules.legalMovesFor(position)
        val bishopMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Should be able to capture on g6 but not continue to h7
        bishopMoves.any { it.to == Position.fromAlgebraic("f5") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("g6") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("h7") } shouldBe false
        // Verify position after capturing on g6
        val captureG6 = bishopMoves.first { it.to == Position.fromAlgebraic("g6") }
        val newPosition = position.movePiece(captureG6.from, captureG6.to)
        newPosition.toFen() shouldBe "4k3/8/6B1/8/8/8/8/4K3 b - - 0 1"
    }

    "should generate limited bishop moves from corner" {
        // Arrange: White bishop on a1
        val position = "4k3/8/8/8/8/8/8/B3K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the bishop
        val allMoves = rules.legalMovesFor(position)
        val bishopMoves = allMoves.filter { it.from == Position.fromAlgebraic("a1") }

        // Assert: Can only move along one diagonal: b2, c3, d4, e5, f6, g7, h8
        bishopMoves.size shouldBe 7
        bishopMoves.any { it.to == Position.fromAlgebraic("h8") } shouldBe true
    }

    "should handle bishop blocked by multiple pieces" {
        // Arrange: White bishop on e4, surrounded by pieces on diagonals
        val position = "4k3/8/2p3p1/8/4B3/3P1P2/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the bishop
        val allMoves = rules.legalMovesFor(position)
        val bishopMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Can only move one square in each diagonal direction (capture enemies or stop at own pieces)
        // d5, f5 (can move to), c6 and g6 (can capture), d3 and f3 (blocked by own pieces)
        bishopMoves.size shouldBe 4
        bishopMoves.any { it.to == Position.fromAlgebraic("d5") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("f5") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("c6") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("g6") } shouldBe true
    }

    "should generate bishop moves for black" {
        // Arrange: Black bishop on d5
        val position = "4k3/8/8/3b4/8/8/8/4K3 b - - 0 1".toChessPosition()

        // Act: Generate legal moves for the bishop
        val allMoves = rules.legalMovesFor(position)
        val bishopMoves = allMoves.filter { it.from == Position.fromAlgebraic("d5") }

        // Assert: Should have moves in all 4 diagonal directions
        // Check a few key positions
        bishopMoves.any { it.to == Position.fromAlgebraic("a8") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("h1") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("a2") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("g8") } shouldBe true
    }

    // ========== Rook Move Generation Tests ==========

    "should generate all orthogonal moves for rook from center" {
        // Arrange: White rook on e4, no obstructions
        val position = "4k3/8/8/8/4R3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the rook
        val allMoves = rules.legalMovesFor(position)
        val rookMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Rook can move orthogonally in 4 directions
        // North: e5, e6, e7, e8 (can capture black king) = 4
        // South: e3, e2 (stops at white king e1) = 2
        // East: f4, g4, h4 = 3
        // West: d4, c4, b4, a4 = 4
        // Total: 13 moves
        rookMoves.size shouldBe 13
    }

    "should stop rook at board edge" {
        // Arrange: White rook on d4, kings not in the way
        val position = "4k3/8/8/8/3R4/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the rook
        val allMoves = rules.legalMovesFor(position)
        val rookMoves = allMoves.filter { it.from == Position.fromAlgebraic("d4") }

        // Assert: Check that it reaches the edges
        rookMoves.any { it.to == Position.fromAlgebraic("d8") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("d1") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("a4") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("h4") } shouldBe true
    }

    "should stop rook when blocked by own piece" {
        // Arrange: White rook on e4, white pawn on e6
        val position = "4k3/8/4P3/8/4R3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the rook
        val allMoves = rules.legalMovesFor(position)
        val rookMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Should be able to move to e5 but not e6, e7, or e8 (blocked by own pawn)
        rookMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("e6") } shouldBe false
        rookMoves.any { it.to == Position.fromAlgebraic("e7") } shouldBe false
        rookMoves.any { it.to == Position.fromAlgebraic("e8") } shouldBe false
    }

    "should stop rook when capturing enemy piece" {
        // Arrange: White rook on e4, black pawn on e6
        val position = "4k3/8/4p3/8/4R3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the rook
        val allMoves = rules.legalMovesFor(position)
        val rookMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Should be able to capture on e6 but not continue to e7 or e8
        rookMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("e6") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("e7") } shouldBe false
        rookMoves.any { it.to == Position.fromAlgebraic("e8") } shouldBe false
        // Verify position after capturing on e6
        val captureE6 = rookMoves.first { it.to == Position.fromAlgebraic("e6") }
        val newPosition = position.movePiece(captureE6.from, captureE6.to)
        newPosition.toFen() shouldBe "4k3/8/4R3/8/8/8/8/4K3 b - - 0 1"
    }

    "should generate limited rook moves from corner" {
        // Arrange: White rook on a1
        val position = "4k3/8/8/8/8/8/8/R3K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the rook
        val allMoves = rules.legalMovesFor(position)
        val rookMoves = allMoves.filter { it.from == Position.fromAlgebraic("a1") }

        // Assert: Can move along file (a2-a8, 7 squares) and rank (b1-d1, 3 squares before king)
        // Total: 10 moves
        rookMoves.size shouldBe 10
        rookMoves.any { it.to == Position.fromAlgebraic("a8") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("d1") } shouldBe true
    }

    "should handle rook blocked by multiple pieces" {
        // Arrange: White rook on e4, surrounded by pieces on orthogonals
        val position = "4k3/8/4p3/8/2P1R1p1/8/4P3/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the rook
        val allMoves = rules.legalMovesFor(position)
        val rookMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Can move: e5, e6 (capture), e3, d4, f4, g4 (capture)
        // Total: 6 moves
        rookMoves.size shouldBe 6
        rookMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("e6") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("e3") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("d4") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("f4") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("g4") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("e2") } shouldBe false
        rookMoves.any { it.to == Position.fromAlgebraic("c4") } shouldBe false
    }

    "should generate rook moves for black" {
        // Arrange: Black rook on d5
        val position = "4k3/8/8/3r4/8/8/8/4K3 b - - 0 1".toChessPosition()

        // Act: Generate legal moves for the rook
        val allMoves = rules.legalMovesFor(position)
        val rookMoves = allMoves.filter { it.from == Position.fromAlgebraic("d5") }

        // Assert: Should have moves in all 4 orthogonal directions
        // Check a few key positions
        rookMoves.any { it.to == Position.fromAlgebraic("d1") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("d8") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("a5") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("h5") } shouldBe true
    }

    // ========== Queen Move Generation Tests ==========

    "should generate all moves for queen from center" {
        // Arrange: White queen on e4, no obstructions
        val position = "4k3/8/8/8/4Q3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the queen
        val allMoves = rules.legalMovesFor(position)
        val queenMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Queen combines rook + bishop moves
        // Orthogonal (rook-like): 13 moves (4 north + 2 south + 3 east + 4 west)
        // Diagonal (bishop-like): 13 moves (3 NE + 3 NW + 3 SE + 3 SW + 1 capturing black king)
        // Total: 26 moves
        queenMoves.size shouldBe 26
    }

    "should stop queen at board edge" {
        // Arrange: White queen on d4, kings not in the way
        val position = "4k3/8/8/8/3Q4/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the queen
        val allMoves = rules.legalMovesFor(position)
        val queenMoves = allMoves.filter { it.from == Position.fromAlgebraic("d4") }

        // Assert: Check that it reaches edges in all 8 directions
        // Orthogonal
        queenMoves.any { it.to == Position.fromAlgebraic("d8") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("d1") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("a4") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("h4") } shouldBe true
        // Diagonal
        queenMoves.any { it.to == Position.fromAlgebraic("a7") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("h8") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("a1") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("g1") } shouldBe true
    }

    "should stop queen when blocked by own piece" {
        // Arrange: White queen on e4, white pawn on e6
        val position = "4k3/8/4P3/8/4Q3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the queen
        val allMoves = rules.legalMovesFor(position)
        val queenMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Should be able to move to e5 but not e6, e7, or e8 (blocked by own pawn)
        queenMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("e6") } shouldBe false
        queenMoves.any { it.to == Position.fromAlgebraic("e7") } shouldBe false
        queenMoves.any { it.to == Position.fromAlgebraic("e8") } shouldBe false
    }

    "should stop queen when capturing enemy piece" {
        // Arrange: White queen on e4, black pawn on g6
        val position = "4k3/8/6p1/8/4Q3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the queen
        val allMoves = rules.legalMovesFor(position)
        val queenMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Should be able to capture on g6 but not continue to h7 (diagonal)
        queenMoves.any { it.to == Position.fromAlgebraic("f5") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("g6") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("h7") } shouldBe false
        // Verify position after capturing on g6
        val captureG6 = queenMoves.first { it.to == Position.fromAlgebraic("g6") }
        val newPosition = position.movePiece(captureG6.from, captureG6.to)
        newPosition.toFen() shouldBe "4k3/8/6Q1/8/8/8/8/4K3 b - - 0 1"
    }

    "should generate limited queen moves from corner" {
        // Arrange: White queen on a1
        val position = "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the queen
        val allMoves = rules.legalMovesFor(position)
        val queenMoves = allMoves.filter { it.from == Position.fromAlgebraic("a1") }

        // Assert: Can move orthogonally (file a2-a8 = 7, rank b1-d1 = 3) and diagonally (b2-h8 = 7)
        // Total: 17 moves
        queenMoves.size shouldBe 17
        queenMoves.any { it.to == Position.fromAlgebraic("a8") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("d1") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("h8") } shouldBe true
    }

    "should handle queen blocked by multiple pieces" {
        // Arrange: White queen on e4, directly surrounded by enemy pieces on adjacent squares
        val position = "4k3/8/8/3ppp2/3pQp2/3ppp2/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the queen
        val allMoves = rules.legalMovesFor(position)
        val queenMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Can capture all 8 surrounding pieces (d5, e5, f5, d4, f4, d3, e3, f3)
        // Total: 8 moves (all captures of adjacent enemy pieces)
        queenMoves.size shouldBe 8
        queenMoves.any { it.to == Position.fromAlgebraic("d5") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("f5") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("d4") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("f4") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("d3") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("e3") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("f3") } shouldBe true
    }

    "should generate queen moves for black" {
        // Arrange: Black queen on d5
        val position = "4k3/8/8/3q4/8/8/8/4K3 b - - 0 1".toChessPosition()

        // Act: Generate legal moves for the queen
        val allMoves = rules.legalMovesFor(position)
        val queenMoves = allMoves.filter { it.from == Position.fromAlgebraic("d5") }

        // Assert: Should have moves in all 8 directions
        // Check a few key positions - orthogonal
        queenMoves.any { it.to == Position.fromAlgebraic("d1") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("d8") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("a5") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("h5") } shouldBe true
        // Check diagonal positions
        queenMoves.any { it.to == Position.fromAlgebraic("a8") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("h1") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("a2") } shouldBe true
        queenMoves.any { it.to == Position.fromAlgebraic("g8") } shouldBe true
    }

    // ========== King Move Generation Tests (TDD) ==========

    "should generate all 8 king moves from center" {
        // Arrange: White king on e4, no obstructions (black king far away)
        val position = "k7/8/8/8/4K3/8/8/8 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: King can move one square in all 8 directions
        // d5, e5, f5, d4, f4, d3, e3, f3
        kingMoves.size shouldBe 8
        kingMoves.any { it.to == Position.fromAlgebraic("d5") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("f5") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("d4") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("f4") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("d3") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("e3") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("f3") } shouldBe true
    }

    "should generate limited king moves from corner" {
        // Arrange: White king on a1 (corner), black king far away
        val position = "k7/8/8/8/8/8/8/K7 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("a1") }

        // Assert: King can only move to 3 squares from corner: a2, b1, b2
        kingMoves.size shouldBe 3
        kingMoves.any { it.to == Position.fromAlgebraic("a2") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("b1") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("b2") } shouldBe true
    }

    "should not allow king to capture own pieces" {
        // Arrange: White king on e4, surrounded by white pawns
        val position = "k7/8/8/3PPP2/3PKP2/3PPP2/8/8 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: King cannot move anywhere (all squares occupied by own pieces)
        kingMoves.size shouldBe 0
    }

    "should allow king to capture enemy pieces" {
        // Arrange: White king on e4, surrounded by black pawns
        // Some pawns are protected by other pawns
        val position = "k7/8/8/3ppp2/3pKp2/3ppp2/8/8 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: King can capture only unprotected pieces: d5, e5, f5, d3, f3
        // Cannot capture: d4, f4 (protected by e5), e3 (protected by d4 and f4)
        kingMoves.size shouldBe 5
        kingMoves.any { it.to == Position.fromAlgebraic("d5") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("f5") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("d3") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("f3") } shouldBe true
        // Verify the resulting position after capturing on d5
        val captureMove = kingMoves.first { it.to == Position.fromAlgebraic("d5") }
        val newPosition = position.movePiece(captureMove.from, captureMove.to)
        // Verify complete board state using FEN (king captured pawn on d5, side changed)
        newPosition.toFen() shouldBe "k7/8/8/3Kpp2/3p1p2/3ppp2/8/8 b - - 0 1"
    }

    "should generate king moves for black" {
        // Arrange: Black king on d5, white king far away
        val position = "8/8/8/3k4/8/8/8/K7 b - - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("d5") }

        // Assert: King can move one square in all 8 directions
        kingMoves.size shouldBe 8
        kingMoves.any { it.to == Position.fromAlgebraic("c6") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("d6") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("e6") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("c5") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("c4") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("d4") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("e4") } shouldBe true
    }

    // ========== Check Detection Tests ==========

    "should not allow king to move into check" {
        // Arrange: White king on e4, black rook on e8 (controls e-file)
        val position = "4r2k/8/8/8/4K3/8/8/8 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: King cannot move to e3 or e5 (rook controls e-file)
        kingMoves.any { it.to == Position.fromAlgebraic("e3") } shouldBe false
        kingMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe false
        // But can move to d3, d4, d5, f3, f4, f5
        kingMoves.any { it.to == Position.fromAlgebraic("d3") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("d4") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("d5") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("f3") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("f4") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("f5") } shouldBe true
    }

    "should not allow king to move into check from bishop" {
        // Arrange: White king on e4, black bishop on a8 (controls diagonal)
        val position = "b6k/8/8/8/4K3/8/8/8 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: King cannot move to d5 (bishop diagonal)
        kingMoves.any { it.to == Position.fromAlgebraic("d5") } shouldBe false
        // Can move to other squares
        kingMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
        kingMoves.any { it.to == Position.fromAlgebraic("f5") } shouldBe true
    }

    "should not allow pinned piece to move" {
        // Arrange: White king on e1, white rook on e4, black rook on e8
        // White rook is pinned and cannot move away from e-file
        val position = "4r2k/8/8/8/4R3/8/8/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the rook
        val allMoves = rules.legalMovesFor(position)
        val rookMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: Rook can only move along e-file (e2, e3, e5, e6, e7, e8)
        // Cannot move to d4, f4, etc. as it would expose king to check
        rookMoves.any { it.to == Position.fromAlgebraic("d4") } shouldBe false
        rookMoves.any { it.to == Position.fromAlgebraic("f4") } shouldBe false
        // Can move along e-file
        rookMoves.any { it.to == Position.fromAlgebraic("e2") } shouldBe true
        rookMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
    }

    "should not allow bishop to move if pinned diagonally" {
        // Arrange: White king on a1, white bishop on c3, black bishop on e5
        // White bishop is pinned on diagonal
        val position = "7k/8/8/4b3/8/2B5/8/K7 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the bishop
        val allMoves = rules.legalMovesFor(position)
        val bishopMoves = allMoves.filter { it.from == Position.fromAlgebraic("c3") }

        // Assert: Bishop can only move along the a1-h8 diagonal (b2, d4, e5)
        // Cannot move to b4, d2, etc.
        bishopMoves.any { it.to == Position.fromAlgebraic("b4") } shouldBe false
        bishopMoves.any { it.to == Position.fromAlgebraic("d2") } shouldBe false
        // Can move along pinned diagonal
        bishopMoves.any { it.to == Position.fromAlgebraic("b2") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("d4") } shouldBe true
        bishopMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
    }

    "should allow king to capture attacking piece" {
        // Arrange: White king on e4, black rook on e5 (giving check)
        // King should be able to capture the rook
        val position = "7k/8/8/4r3/4K3/8/8/8 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e4") }

        // Assert: King can capture the rook on e5
        kingMoves.any { it.to == Position.fromAlgebraic("e5") } shouldBe true
    }

    "should not allow pawn to move if pinned" {
        // Arrange: White king on e1, white pawn on e2, black rook on e8
        // Pawn is pinned and CAN move along the pin line (e-file)
        val position = "4r2k/8/8/8/8/8/4P3/4K3 w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the pawn
        val allMoves = rules.legalMovesFor(position)
        val pawnMoves = allMoves.filter { it.from == Position.fromAlgebraic("e2") }

        // Assert: Pawn CAN move to e3 or e4 (along the pin line) but blocks the rook
        pawnMoves.size shouldBe 2
        pawnMoves.any { it.to == Position.fromAlgebraic("e3") } shouldBe true
        pawnMoves.any { it.to == Position.fromAlgebraic("e4") } shouldBe true
    }

    // ========== Castling Tests ==========

    "should allow white kingside castling when conditions are met" {
        // Arrange: White king on e1, rook on h1, no pieces between, no threats
        val position = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e1") }

        // Assert: King can castle kingside (e1 to g1)
        kingMoves.any { it.to == Position.fromAlgebraic("g1") } shouldBe true

        // Verify rook moves after castling
        val castleMove = kingMoves.first { it.to == Position.fromAlgebraic("g1") }
        val afterCastling = position.movePiece(castleMove.from, castleMove.to)

        // King should be on g1, rook should be on f1
        afterCastling.pieceAt(Position.fromAlgebraic("g1"))?.type shouldBe PieceType.KING
        afterCastling.pieceAt(Position.fromAlgebraic("f1"))?.type shouldBe PieceType.ROOK
        afterCastling.pieceAt(Position.fromAlgebraic("e1")) shouldBe null
        afterCastling.pieceAt(Position.fromAlgebraic("h1")) shouldBe null
    }

    "should allow white queenside castling when conditions are met" {
        // Arrange: White king on e1, rook on a1, no pieces between, no threats
        val position = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e1") }

        // Assert: King can castle queenside (e1 to c1)
        kingMoves.any { it.to == Position.fromAlgebraic("c1") } shouldBe true

        // Verify rook moves after castling
        val castleMove = kingMoves.first { it.to == Position.fromAlgebraic("c1") }
        val afterCastling = position.movePiece(castleMove.from, castleMove.to)

        // King should be on c1, rook should be on d1
        afterCastling.pieceAt(Position.fromAlgebraic("c1"))?.type shouldBe PieceType.KING
        afterCastling.pieceAt(Position.fromAlgebraic("d1"))?.type shouldBe PieceType.ROOK
        afterCastling.pieceAt(Position.fromAlgebraic("e1")) shouldBe null
        afterCastling.pieceAt(Position.fromAlgebraic("a1")) shouldBe null
    }

    "should allow black kingside castling when conditions are met" {
        // Arrange: Black king on e8, rook on h8, no pieces between, no threats
        val position = "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e8") }

        // Assert: King can castle kingside (e8 to g8)
        kingMoves.any { it.to == Position.fromAlgebraic("g8") } shouldBe true

        // Verify rook moves after castling
        val castleMove = kingMoves.first { it.to == Position.fromAlgebraic("g8") }
        val afterCastling = position.movePiece(castleMove.from, castleMove.to)

        // King should be on g8, rook should be on f8
        afterCastling.pieceAt(Position.fromAlgebraic("g8"))?.type shouldBe PieceType.KING
        afterCastling.pieceAt(Position.fromAlgebraic("f8"))?.type shouldBe PieceType.ROOK
        afterCastling.pieceAt(Position.fromAlgebraic("e8")) shouldBe null
        afterCastling.pieceAt(Position.fromAlgebraic("h8")) shouldBe null
    }

    "should allow black queenside castling when conditions are met" {
        // Arrange: Black king on e8, rook on a8, no pieces between, no threats
        val position = "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e8") }

        // Assert: King can castle queenside (e8 to c8)
        kingMoves.any { it.to == Position.fromAlgebraic("c8") } shouldBe true

        // Verify rook moves after castling
        val castleMove = kingMoves.first { it.to == Position.fromAlgebraic("c8") }
        val afterCastling = position.movePiece(castleMove.from, castleMove.to)

        // King should be on c8, rook should be on d8
        afterCastling.pieceAt(Position.fromAlgebraic("c8"))?.type shouldBe PieceType.KING
        afterCastling.pieceAt(Position.fromAlgebraic("d8"))?.type shouldBe PieceType.ROOK
        afterCastling.pieceAt(Position.fromAlgebraic("e8")) shouldBe null
        afterCastling.pieceAt(Position.fromAlgebraic("a8")) shouldBe null
    }

    "should not allow castling if king is in check" {
        // Arrange: White king on e1 in check from black rook on e8
        val position = "4r2r/8/8/8/8/8/8/R3K2R w KQ - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e1") }

        // Assert: King cannot castle while in check
        kingMoves.any { it.to == Position.fromAlgebraic("g1") } shouldBe false
        kingMoves.any { it.to == Position.fromAlgebraic("c1") } shouldBe false
    }

    "should not allow castling through attacked square (kingside)" {
        // Arrange: White king on e1, f1 is attacked by black rook on f8
        val position = "5r1r/8/8/8/8/8/8/R3K2R w KQ - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e1") }

        // Assert: King cannot castle kingside (f1 is attacked)
        kingMoves.any { it.to == Position.fromAlgebraic("g1") } shouldBe false
    }

    "should not allow castling through attacked square (queenside)" {
        // Arrange: White king on e1, d1 is attacked by black rook on d8
        val position = "r2r4/8/8/8/8/8/8/R3K2R w KQ - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e1") }

        // Assert: King cannot castle queenside (d1 is attacked)
        kingMoves.any { it.to == Position.fromAlgebraic("c1") } shouldBe false
    }

    "should not allow castling to attacked square" {
        // Arrange: White king on e1, g1 is attacked by black rook on g8
        val position = "r5rr/8/8/8/8/8/8/R3K2R w KQ - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e1") }

        // Assert: King cannot castle kingside (g1 is attacked)
        kingMoves.any { it.to == Position.fromAlgebraic("g1") } shouldBe false
    }

    "should not allow castling if pieces are between king and rook" {
        // Arrange: White king on e1, pieces between king and rooks
        val position = "r3k2r/8/8/8/8/8/8/RN2KB1R w KQkq - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e1") }

        // Assert: King cannot castle in either direction (pieces in the way)
        kingMoves.any { it.to == Position.fromAlgebraic("g1") } shouldBe false
        kingMoves.any { it.to == Position.fromAlgebraic("c1") } shouldBe false
    }

    "should not allow castling if castling rights lost" {
        // Arrange: White king on e1, but no castling rights (indicated by - in FEN)
        val position = "r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1".toChessPosition()

        // Act: Generate legal moves for the king
        val allMoves = rules.legalMovesFor(position)
        val kingMoves = allMoves.filter { it.from == Position.fromAlgebraic("e1") }

        // Assert: King cannot castle (rights lost)
        kingMoves.any { it.to == Position.fromAlgebraic("g1") } shouldBe false
        kingMoves.any { it.to == Position.fromAlgebraic("c1") } shouldBe false
    }

    "should lose castling rights after king moves" {
        // Arrange: Starting position with castling rights
        val position = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1".toChessPosition()

        // Act: Move king one square and back
        val afterKingMove = position.movePiece(
            Position.fromAlgebraic("e1"),
            Position.fromAlgebraic("e2")
        )

        // Assert: Castling rights should be lost
        afterKingMove.castlingRights.whiteKingSide shouldBe false
        afterKingMove.castlingRights.whiteQueenSide shouldBe false
    }

    // ========== Checkmate Detection Tests ==========

    "should detect fool's mate (fastest checkmate)" {
        // Arrange: Fool's mate - Queen on h4 delivers checkmate
        // f2-f3, e7-e5, g2-g4, Qd8-h4#
        val position = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 1".toChessPosition()

        // Act & Assert: White is in checkmate
        rules.isCheckmate(position) shouldBe true
    }

    "should detect back-rank checkmate with queen" {
        // Arrange: Black king trapped on back rank, white queen delivers mate from f8
        val position = "4Q1k1/5ppp/8/8/8/8/8/6K1 b - - 0 1".toChessPosition()

        // Act & Assert: Black is in checkmate
        rules.isCheckmate(position) shouldBe true
    }

    "should detect two-rook checkmate (ladder mate)" {
        // Arrange: Black king on h8, white rooks on g7 and h6
        val position = "7k/8/7R/6R1/8/8/8/4K3 b - - 0 1".toChessPosition()

        // Act & Assert: Black is in checkmate
        rules.isCheckmate(position) shouldBe true
    }

    "should detect queen and king checkmate" {
        // Arrange: Black king on h8, white queen on g7, white king on f6
        // Queen on g7 gives check via diagonal, controls g8 and h7, king on f6 prevents escape
        val position = "7k/6Q1/5K2/8/8/8/8/8 b - - 0 1".toChessPosition()

        // Act & Assert: Black is in checkmate
        rules.isCheckmate(position) shouldBe true
    }

    "should detect Scholar's mate" {
        // Arrange: Classic Scholar's mate position
        // Black king on e8, white queen on f7, white bishop on c4
        val position = "r1bqk2r/pppp1Qpp/2n2n2/2b1p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 1".toChessPosition()

        // Act & Assert: Black is in checkmate
        rules.isCheckmate(position) shouldBe true
    }

    "should detect smothered mate with knight" {
        // Arrange: Black king on h8 surrounded by own pieces, white knight on f7
        val position = "6rk/5Npp/8/8/8/8/8/4K3 b - - 0 1".toChessPosition()

        // Act & Assert: Black is in checkmate
        rules.isCheckmate(position) shouldBe true
    }

    "should not detect checkmate when king can move out of check" {
        // Arrange: Black king on e8 in check from white rook on e1, but can move to d8/f8
        val position = "4k3/8/8/8/8/8/8/4R3 b - - 0 1".toChessPosition()

        // Act & Assert: Not checkmate - king has escape squares
        rules.isCheckmate(position) shouldBe false
    }

    "should not detect checkmate when piece can block the check" {
        // Arrange: Black king on e8, white queen on e1, black bishop on c6 can block
        val position = "4k3/8/2b5/8/8/8/8/4Q3 b - - 0 1".toChessPosition()

        // Act & Assert: Not checkmate - bishop can block on e6
        rules.isCheckmate(position) shouldBe false
    }

    "should not detect checkmate when checking piece can be captured" {
        // Arrange: Black king on e8, white rook on e1, black rook on e7 can capture
        val position = "4k3/4r3/8/8/8/8/8/4R3 b - - 0 1".toChessPosition()

        // Act & Assert: Not checkmate - black rook can capture white rook
        rules.isCheckmate(position) shouldBe false
    }

    "should not detect checkmate when not in check (stalemate position)" {
        // Arrange: Black king on a8, not in check but no legal moves (stalemate, not checkmate)
        val position = "k7/2Q5/1K6/8/8/8/8/8 b - - 0 1".toChessPosition()

        // Act & Assert: Not checkmate - king is not in check (this is stalemate)
        rules.isCheckmate(position) shouldBe false
    }

    "should detect checkmate for white when it's white's turn" {
        // Arrange: White king on h1, black rook on h8 and g2
        val position = "7r/8/8/8/8/6r1/8/7K w - - 0 1".toChessPosition()

        // Act & Assert: White is in checkmate
        rules.isCheckmate(position) shouldBe true
    }

    // ========== Stalemate Detection Tests ==========

    "should detect stalemate with king in corner" {
        // Arrange: Black king on a8, white queen on b6, white king on c6
        // Black king not in check but has no legal moves
        val position = "k7/8/1QK5/8/8/8/8/8 b - - 0 1".toChessPosition()

        // Act & Assert: Black is in stalemate
        rules.isStalemate(position) shouldBe true
    }

    "should detect stalemate with king on edge" {
        // Arrange: Black king on h8, white queen on f7, white king on f6
        // King trapped on edge, not in check but no moves
        val position = "7k/5Q2/5K2/8/8/8/8/8 b - - 0 1".toChessPosition()

        // Act & Assert: Black is in stalemate
        rules.isStalemate(position) shouldBe true
    }

    "should detect stalemate with blocked pawn" {
        // Arrange: White king on a8, white pawn on a7 blocked, black king on c7
        // White has no legal moves (pawn blocked, king has no squares)
        val position = "K7/P1k5/8/8/8/8/8/8 w - - 0 1".toChessPosition()

        // Act & Assert: White is in stalemate
        rules.isStalemate(position) shouldBe true
    }

    "should detect stalemate with multiple pieces blocked" {
        // Arrange: Complex stalemate position with multiple blocked pieces
        val position = "5k2/5P2/5K2/8/8/8/8/8 b - - 0 1".toChessPosition()

        // Act & Assert: Black is in stalemate
        rules.isStalemate(position) shouldBe true
    }

    "should not detect stalemate when king is in check" {
        // Arrange: Black king on e8 in check from white rook on e1
        // This is NOT stalemate (it's check, potentially checkmate)
        val position = "4k3/8/8/8/8/8/8/4R3 b - - 0 1".toChessPosition()

        // Act & Assert: Not stalemate - king is in check
        rules.isStalemate(position) shouldBe false
    }

    "should not detect stalemate when legal moves exist" {
        // Arrange: Normal position with legal moves available
        val position = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1".toChessPosition()

        // Act & Assert: Not stalemate - white has legal moves
        rules.isStalemate(position) shouldBe false
    }

    "should not detect stalemate when it's checkmate" {
        // Arrange: Fool's mate position (checkmate, not stalemate)
        val position = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 1".toChessPosition()

        // Act & Assert: Not stalemate - this is checkmate (king in check with no moves)
        rules.isStalemate(position) shouldBe false
    }

    "should not detect stalemate in starting position" {
        // Arrange: Standard chess starting position
        val position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()

        // Act & Assert: Not stalemate - many moves available
        rules.isStalemate(position) shouldBe false
    }

    // ========== Fifty-Move Rule Tests ==========

    "should detect fifty-move rule when halfmove clock equals 100" {
        // Arrange: Position with exactly 100 halfmoves (50 full moves) without capture/pawn move
        val position = "4k3/8/8/8/8/8/8/4K3 w - - 100 50".toChessPosition()

        // Act & Assert: Fifty-move rule applies
        rules.isFiftyMoveRule(position) shouldBe true
    }

    "should detect fifty-move rule when halfmove clock exceeds 100" {
        // Arrange: Position with 120 halfmoves
        val position = "4k3/8/8/8/8/8/8/4K3 w - - 120 60".toChessPosition()

        // Act & Assert: Fifty-move rule applies
        rules.isFiftyMoveRule(position) shouldBe true
    }

    "should not detect fifty-move rule when halfmove clock is 99" {
        // Arrange: Position with 99 halfmoves (just before the rule applies)
        val position = "4k3/8/8/8/8/8/8/4K3 w - - 99 50".toChessPosition()

        // Act & Assert: Fifty-move rule does NOT apply yet
        rules.isFiftyMoveRule(position) shouldBe false
    }

    "should not detect fifty-move rule at game start" {
        // Arrange: Starting position with halfmove clock at 0
        val position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()

        // Act & Assert: Fifty-move rule does NOT apply
        rules.isFiftyMoveRule(position) shouldBe false
    }

    "should not detect fifty-move rule after recent pawn move" {
        // Arrange: Position after pawn move (halfmove clock reset to 0)
        val position = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1".toChessPosition()

        // Act & Assert: Fifty-move rule does NOT apply
        rules.isFiftyMoveRule(position) shouldBe false
    }

    "should not detect fifty-move rule when capture at 99 halfmoves resets counter" {
        // Arrange: Position with 99 halfmoves where white can capture black pawn
        val positionBefore = "4k3/8/8/8/8/8/3p4/4K3 w - - 99 50".toChessPosition()

        // Verify we're at 99 halfmoves (one away from the rule)
        positionBefore.halfmoveClock shouldBe 99

        // Act: White king captures black pawn (Ke1xd2)
        val positionAfter = positionBefore.movePiece(
            Position.fromAlgebraic("e1"),
            Position.fromAlgebraic("d2")
        )

        // Assert: Halfmove clock reset to 0 due to capture
        positionAfter.halfmoveClock shouldBe 0

        // Assert: Fifty-move rule does NOT apply after the capture
        rules.isFiftyMoveRule(positionAfter) shouldBe false
    }

    // ========== Threefold Repetition Tests ==========

    "should detect threefold repetition when same position occurs 3 times" {
        // Arrange: Position that appears 3 times in history
        val pos1 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()
        val pos2 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1".toChessPosition()
        val pos3 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 2 2".toChessPosition() // Same as pos1
        val pos4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 2 2".toChessPosition()
        val pos5 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 4 3".toChessPosition() // Same as pos1 again

        val history = listOf(pos1, pos2, pos3, pos4, pos5)

        // Act & Assert: Threefold repetition detected
        rules.isThreefoldRepetition(pos5, history) shouldBe true
    }

    "should detect threefold repetition with current position appearing 3 times total" {
        // Arrange: Current position appeared twice before
        val current = "4k3/8/8/8/8/8/8/4K3 w - - 0 1".toChessPosition()
        val other = "4k3/8/8/8/8/8/4P3/4K3 b - - 0 1".toChessPosition()

        val history = listOf(
            current, // 1st occurrence
            other,
            current, // 2nd occurrence
            other,
            current  // 3rd occurrence (current)
        )

        // Act & Assert: Threefold repetition detected
        rules.isThreefoldRepetition(current, history) shouldBe true
    }

    "should not detect threefold repetition when position occurs only twice" {
        // Arrange: Position appears only 2 times
        val pos1 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()
        val pos2 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1".toChessPosition()
        val pos3 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 2 2".toChessPosition() // Same as pos1

        val history = listOf(pos1, pos2, pos3)

        // Act & Assert: Not threefold repetition - only 2 occurrences
        rules.isThreefoldRepetition(pos3, history) shouldBe false
    }

    "should not detect threefold repetition when position never repeated" {
        // Arrange: All unique positions
        val pos1 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()
        val pos2 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1".toChessPosition()
        val pos3 = "rnbqkbnr/pppppppp/8/8/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 1 2".toChessPosition()

        val history = listOf(pos1, pos2, pos3)

        // Act & Assert: Not threefold repetition - all unique
        rules.isThreefoldRepetition(pos3, history) shouldBe false
    }

    "should not detect threefold repetition with different castling rights" {
        // Arrange: Same piece positions but different castling rights
        val pos1 = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1".toChessPosition()
        val pos2 = "r3k2r/8/8/8/8/8/8/R3K2R w Qkq - 0 1".toChessPosition() // Lost kingside castling
        val pos3 = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1".toChessPosition()

        val history = listOf(pos1, pos2, pos3)

        // Act & Assert: Different castling rights = different positions
        rules.isThreefoldRepetition(pos3, history) shouldBe false
    }

    "should not detect threefold repetition with different side to move" {
        // Arrange: Same board position but different side to move
        val pos1 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()
        val pos2 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1".toChessPosition()
        val pos3 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()

        val history = listOf(pos1, pos2, pos3)

        // Act & Assert: Different side to move = different positions
        rules.isThreefoldRepetition(pos3, history) shouldBe false
    }

    // ========== Insufficient Material Tests ==========

    "should detect insufficient material with king vs king" {
        // Arrange: Only two kings on the board
        val position = "4k3/8/8/8/8/8/8/4K3 w - - 0 1".toChessPosition()

        // Act & Assert: Insufficient material - cannot checkmate
        rules.isInsufficientMaterial(position) shouldBe true
    }

    "should detect insufficient material with king and bishop vs king" {
        // Arrange: White has king + bishop, black has only king
        val position = "4k3/8/8/8/8/8/3B4/4K3 w - - 0 1".toChessPosition()

        // Act & Assert: Insufficient material - king + bishop cannot checkmate lone king
        rules.isInsufficientMaterial(position) shouldBe true
    }

    "should detect insufficient material with king vs king and knight" {
        // Arrange: White has only king, black has king + knight
        val position = "4k2n/8/8/8/8/8/8/4K3 w - - 0 1".toChessPosition()

        // Act & Assert: Insufficient material - king + knight cannot checkmate
        rules.isInsufficientMaterial(position) shouldBe true
    }

    "should detect insufficient material with king and bishop vs king and bishop same color" {
        // Arrange: Both sides have king + bishop on light squares (c1 and f8 are light)
        val position = "5b2/8/8/8/8/8/8/2B1K2k w - - 0 1".toChessPosition()

        // Act & Assert: Insufficient material - same color bishops cannot checkmate
        rules.isInsufficientMaterial(position) shouldBe true
    }

    "should not detect insufficient material with king and bishop vs king and bishop opposite colors" {
        // Arrange: Bishops on opposite colored squares (c1 light, a8 dark)
        val position = "b7/8/8/8/8/8/8/2B1K2k w - - 0 1".toChessPosition()

        // Act & Assert: Sufficient material - opposite color bishops can checkmate
        rules.isInsufficientMaterial(position) shouldBe false
    }

    "should not detect insufficient material with king and queen vs king" {
        // Arrange: White has king + queen, black has only king
        val position = "4k3/8/8/8/8/8/3Q4/4K3 w - - 0 1".toChessPosition()

        // Act & Assert: Sufficient material - queen can checkmate
        rules.isInsufficientMaterial(position) shouldBe false
    }

    "should not detect insufficient material with king and rook vs king" {
        // Arrange: White has king + rook, black has only king
        val position = "4k3/8/8/8/8/8/3R4/4K3 w - - 0 1".toChessPosition()

        // Act & Assert: Sufficient material - rook can checkmate
        rules.isInsufficientMaterial(position) shouldBe false
    }

    "should not detect insufficient material with king and pawn vs king" {
        // Arrange: White has king + pawn, black has only king
        val position = "4k3/8/8/8/8/8/3P4/4K3 w - - 0 1".toChessPosition()

        // Act & Assert: Sufficient material - pawn can promote and checkmate
        rules.isInsufficientMaterial(position) shouldBe false
    }

    "should not detect insufficient material in starting position" {
        // Arrange: Standard chess starting position
        val position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1".toChessPosition()

        // Act & Assert: Sufficient material - many pieces
        rules.isInsufficientMaterial(position) shouldBe false
    }

    "should not detect insufficient material with multiple minor pieces" {
        // Arrange: King + two knights vs king (can checkmate with 2 knights)
        val position = "4k3/8/8/8/8/8/3NN3/4K3 w - - 0 1".toChessPosition()

        // Act & Assert: Sufficient material - two knights can potentially checkmate
        rules.isInsufficientMaterial(position) shouldBe false
    }
})
