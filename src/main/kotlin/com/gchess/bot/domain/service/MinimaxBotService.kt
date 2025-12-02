package com.gchess.bot.domain.service

import com.gchess.bot.domain.model.BotDifficulty
import com.gchess.bot.domain.model.MoveEvaluation
import com.gchess.chess.domain.model.ChessPosition
import com.gchess.chess.domain.model.Move
import com.gchess.chess.domain.model.PieceSquareTables
import com.gchess.chess.domain.model.PieceType
import com.gchess.shared.domain.model.PlayerSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Minimax-based chess engine implementation with alpha-beta pruning.
 *
 * Phase 4: Alpha-beta pruning for efficient deep search (6-8 plies).
 * Uses negamax variant with alpha-beta cutoffs and move ordering.
 *
 * Alpha-beta pruning reduces search complexity from O(b^d) to ~O(b^(d/2))
 * by eliminating branches that cannot influence the final decision.
 *
 * Search depths by difficulty (Phase 4):
 * - BEGINNER: 3 plies (~5,000 positions with alpha-beta)
 * - INTERMEDIATE: 5 plies (~243,000 positions)
 * - ADVANCED: 6 plies (~7M positions)
 * - MASTER: 8 plies (deep tactical search)
 */
class MinimaxBotService : BotService {

    private var nodesSearched: Long = 0L
    private val logger = LoggerFactory.getLogger(MinimaxBotService::class.java)
    private var legalMoveTime: Duration = 0.seconds
    private var evaluationTime: Duration = 0.seconds

    override suspend fun calculateBestMove(
        position: ChessPosition,
        difficulty: BotDifficulty
    ): Result<MoveEvaluation> = withContext(Dispatchers.Default) {
        var bestEvaluation : MoveEvaluation
        val totalTime = measureTime {
            nodesSearched = 0L
            legalMoveTime = 0.seconds
            evaluationTime = 0.seconds

            var legalMoves: List<Move> = emptyList()
            legalMoveTime += measureTime {
                legalMoves = position.getLegalMoves()
                nodesSearched += legalMoves.size
            }

            if (legalMoves.isEmpty()) {
                return@withContext Result.failure(Exception("No legal moves available"))
            }

            // Order moves for better alpha-beta efficiency (captures and promotions first)
            val orderedMoves = orderMoves(position, legalMoves)

            // Search each move to the configured depth with alpha-beta pruning
            val searchDepth = difficulty.searchDepth
            val evaluations = mutableListOf<MoveEvaluation>()

            // Alpha-beta window: alpha is the best score we can guarantee, beta is the opponent's best
            var alpha = Int.MIN_VALUE
            val beta = Int.MAX_VALUE

            for (move in orderedMoves) {
                val newPosition = position.movePiece(move.from, move.to, move.promotion)

                // Alpha-beta search from opponent's perspective (negamax variant)
                val score = -alphaBeta(newPosition, searchDepth - 1, -beta, -alpha, searchDepth)

                evaluations.add(MoveEvaluation(move, score, searchDepth))

                // Update alpha (our best guaranteed score)
                if (score > alpha) {
                    alpha = score
                }
            }

            // Ensure we have at least one evaluation
            if (evaluations.isEmpty()) {
                // Timeout before evaluating any move, use first legal move
                val firstMove = legalMoves.first()
                val newPosition = position.movePiece(firstMove.from, firstMove.to, firstMove.promotion)
                var score = 0
                evaluationTime += measureTime {
                    score = -newPosition.value
                }
                return@withContext Result.success(MoveEvaluation(firstMove, score, 1))
            }

            // Select the move with the best score
            bestEvaluation = evaluations.maxByOrNull { it.score }!!
        }
        val nps = if (totalTime < 1.seconds) 0 else nodesSearched / totalTime.inWholeSeconds
        logger.info("looking for moves : $legalMoveTime | evaluating positions : $evaluationTime | positions reviewed : $nodesSearched | total time : $totalTime | nps : $nps")

        Result.success(bestEvaluation)
    }

    /**
     * Alpha-beta pruning search (negamax variant).
     *
     * Alpha-beta is an optimization of minimax that eliminates branches
     * that cannot influence the final decision. This dramatically reduces
     * the number of positions evaluated.
     *
     * Key concepts:
     * - Alpha: Best score we can guarantee for the current player (lower bound)
     * - Beta: Best score the opponent can guarantee (upper bound)
     * - If alpha >= beta, we can prune (stop searching this branch)
     *
     * With good move ordering, alpha-beta reduces complexity from O(b^d) to ~O(b^(d/2)).
     *
     * @param position Current position to evaluate
     * @param depth Remaining search depth (0 = leaf node)
     * @param alpha Best score we can guarantee (lower bound)
     * @param beta Best score opponent can guarantee (upper bound)
     * @param initialDepth Initial search depth (for tracking max depth reached)
     * @return Best evaluation score from this position
     */
    private fun alphaBeta(position: ChessPosition, depth: Int, alpha: Int, beta: Int, initialDepth: Int): Int {
        // IMPORTANT: Check for terminal positions (mate/stalemate) BEFORE depth check
        // This ensures mate detection even at depth=0 (critical for shallow searches)
        var legalMoves = listOf<Move>()
        legalMoveTime += measureTime {
            legalMoves = position.getLegalMoves()
            nodesSearched += legalMoves.size
        }

        // Terminal position (checkmate or stalemate)
        if (legalMoves.isEmpty()) {
            // TODO: detect checkmate vs stalemate for accurate scoring
            // For now, return a very negative score (losing position)
            // Multiply by depth to prefer shorter mates
            return -100000 - depth
        }

        // Base case: leaf node (max depth reached)
        if (depth == 0) {
            var result = 0
            evaluationTime += measureTime {
                result = position.value
            }
            return result
        }

        // Order moves for maximum alpha-beta efficiency
        val orderedMoves = orderMoves(position, legalMoves)

        var currentAlpha = alpha
        var bestScore = Int.MIN_VALUE

        for (move in orderedMoves) {
            val newPosition = position.movePiece(move.from, move.to, move.promotion)

            // Recursive alpha-beta search with negated window (negamax pattern)
            val score = -alphaBeta(newPosition, depth - 1, -beta, -currentAlpha, initialDepth)

            if (score > bestScore) {
                bestScore = score
            }

            // Update alpha (our best guaranteed score)
            if (score > currentAlpha) {
                currentAlpha = score
            }

            // Beta cutoff: opponent won't allow this position
            // This branch can be pruned
            if (currentAlpha >= beta) {
                // This is called a "beta cutoff" or "fail-high"
                // The opponent has a better option elsewhere, so they won't let us reach this position
                break
            }
        }

        return bestScore
    }

    /**
     * Orders moves for better search efficiency.
     * Prioritizes captures (MVV-LVA heuristic), promotions, and positional improvements.
     *
     * Move ordering improves search performance by examining
     * likely good moves first, which is critical for alpha-beta pruning efficiency.
     *
     * Uses piece-square tables to order quiet moves, ensuring central moves
     * (e4, d5, Nf3) are explored before wing moves (a6, h6).
     *
     * @param position Current position
     * @param moves List of legal moves to order
     * @return Ordered list (best moves first)
     */
    private fun orderMoves(position: ChessPosition, moves: List<Move>): List<Move> {
        return moves.sortedByDescending { move ->
            var score = 0

            // Prioritize promotions (highest priority)
            if (move.promotion != null) {
                score += 10000
                // Prefer queen promotions
                if (move.promotion == PieceType.QUEEN) {
                    score += 1000
                }
            }

            // Prioritize captures (MVV-LVA: Most Valuable Victim - Least Valuable Attacker)
            val capturedPiece = position.pieceAt(move.to)
            if (capturedPiece != null) {
                val victimValue = capturedPiece.type.value
                val attackerValue = position.pieceAt(move.from)?.type?.value ?: 0
                // Higher score for capturing valuable pieces with less valuable attackers
                score += victimValue * 10 - attackerValue
            } else {
                // For quiet moves (non-captures), use piece-square table delta
                // This ensures central moves (e4, d5, Nf3) are explored before wing moves (a6, h6)
                val piece = position.pieceAt(move.from)
                if (piece != null) {
                    val isWhite = piece.side == PlayerSide.WHITE
                    val fromIndex = ChessPosition.Companion.positionToIndex(move.from)
                    val toIndex = ChessPosition.Companion.positionToIndex(move.to)

                    val fromBonus = PieceSquareTables.getBonus(
                        piece.type, fromIndex, isWhite
                    )
                    val toBonus = PieceSquareTables.getBonus(
                        piece.type, toIndex, isWhite
                    )

                    // Add the positional improvement (can be negative for bad moves)
                    score += (toBonus - fromBonus)
                }
            }

            score
        }
    }
}