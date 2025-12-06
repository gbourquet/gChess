package com.gchess.bot.domain.service

import com.gchess.bot.domain.model.BotDifficulty
import com.gchess.bot.domain.model.MoveEvaluation
import com.gchess.chess.domain.model.ChessPosition
import com.gchess.chess.domain.model.Move
import com.gchess.chess.domain.model.PieceSquareTables
import com.gchess.chess.domain.model.PieceType
import com.gchess.shared.domain.model.PlayerSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong
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

    // Transposition Table for caching evaluated positions (256 MB)
    private val transpositionTable = TranspositionTable(256)

    override suspend fun calculateBestMove(
        position: ChessPosition,
        difficulty: BotDifficulty
    ): Result<MoveEvaluation> = withContext(Dispatchers.Default) {
        // Lazy SMP: Launch multiple parallel search coroutines sharing the same Transposition Table
        // Coroutines are lightweight (~KB each), so we can launch many more than CPU cores
        val numWorkers = difficulty.numWorkers
        logger.info("Launching Lazy SMP with $numWorkers worker coroutines")

        // Shared atomic counters for statistics (thread-safe)
        val sharedNodesSearched = AtomicLong(0)
        val sharedLegalMoveTimeNs = AtomicLong(0)
        val sharedEvaluationTimeNs = AtomicLong(0)

        lateinit var bestEvaluation: MoveEvaluation

        val totalTime = measureTime {
            // Reset counters
            nodesSearched = 0L
            legalMoveTime = 0.seconds
            evaluationTime = 0.seconds

            // Clear TT for new search
            transpositionTable.clear()

            // Check for legal moves first
            val legalMoves = position.getLegalMoves()
            if (legalMoves.isEmpty()) {
                return@withContext Result.failure(Exception("No legal moves available"))
            }

            // Launch parallel search workers
            val workerResults = (1..numWorkers).map { workerID ->
                async(Dispatchers.Default) {
                    searchWorker(
                        workerID = workerID,
                        position = position,
                        legalMoves = legalMoves,
                        searchDepth = difficulty.searchDepth,
                        sharedNodesSearched = sharedNodesSearched,
                        sharedLegalMoveTimeNs = sharedLegalMoveTimeNs,
                        sharedEvaluationTimeNs = sharedEvaluationTimeNs
                    )
                }
            }.awaitAll()

            // Select best result from all workers
            bestEvaluation = workerResults.maxByOrNull { it.score }!!

            // Aggregate statistics
            nodesSearched = sharedNodesSearched.get()
            legalMoveTime = Duration.parse("${sharedLegalMoveTimeNs.get()}ns")
            evaluationTime = Duration.parse("${sharedEvaluationTimeNs.get()}ns")
        }

        val nps = if (totalTime < 1.seconds) 0 else nodesSearched / totalTime.inWholeSeconds
        logger.info("Lazy SMP Results: $numWorkers workers | TT size: ${transpositionTable.size()} | Fill ratio: ${"%.2f".format(transpositionTable.fillRatio() * 100)}%")
        logger.info("looking for moves : $legalMoveTime | evaluating positions : $evaluationTime | positions reviewed : $nodesSearched | total time : $totalTime | nps : $nps")
        logger.info("best position : ${bestEvaluation.bestPosition?.toFen() ?: "Non trouvée"} | score : ${bestEvaluation.score}")

        Result.success(bestEvaluation)
    }

    /**
     * Search worker for Lazy SMP parallel search.
     * Each worker performs independent iterative deepening search,
     * sharing results via the Transposition Table.
     */
    private fun searchWorker(
        workerID: Int,
        position: ChessPosition,
        legalMoves: List<Move>,
        searchDepth: Int,
        sharedNodesSearched: AtomicLong,
        sharedLegalMoveTimeNs: AtomicLong,
        sharedEvaluationTimeNs: AtomicLong
    ): MoveEvaluation {
        var bestEvaluation: MoveEvaluation? = null
        var bestMoveFromPreviousIteration: Move? = null

        // Local counters for this worker
        var localNodesSearched = 0L
        var localLegalMoveTimeNs = 0L
        var localEvaluationTimeNs = 0L

        // Iterative deepening: search at increasing depths
        for (currentDepth in 1..searchDepth) {
            val evaluations = mutableListOf<MoveEvaluation>()

            // Order moves, putting the best move from previous iteration first (PV move)
            // Also use TT best move for move ordering
            val ttBestMove = transpositionTable.getBestMove(position.zobristHash)
            val pvMove = bestMoveFromPreviousIteration ?: ttBestMove
            val orderedMoves = if (pvMove != null) {
                orderMovesWithPV(position, legalMoves, pvMove)
            } else {
                orderMoves(position, legalMoves)
            }

            // Alpha-beta window
            var alpha = -1_000_000
            val beta = 1_000_000

            for (move in orderedMoves) {
                val newPosition = position.movePiece(move.from, move.to, move.promotion)

                // Track legal move time
                val moveStartTime = System.nanoTime()
                localNodesSearched += 1

                // Alpha-beta search
                val evalStartTime = System.nanoTime()
                val (scoreNeg, positionResult) = alphaBeta(newPosition, currentDepth - 1, -beta, -alpha, currentDepth)
                val score = -scoreNeg
                localEvaluationTimeNs += System.nanoTime() - evalStartTime

                localLegalMoveTimeNs += System.nanoTime() - moveStartTime

                evaluations.add(MoveEvaluation(move, score, currentDepth, positionResult))

                // Update alpha
                if (score > alpha) {
                    alpha = score
                }
            }

            // Ensure we have at least one evaluation
            if (evaluations.isNotEmpty()) {
                bestEvaluation = evaluations.maxByOrNull { it.score }!!
                bestMoveFromPreviousIteration = bestEvaluation.move
            }
        }

        // Update shared counters
        sharedNodesSearched.addAndGet(localNodesSearched)
        sharedLegalMoveTimeNs.addAndGet(localLegalMoveTimeNs)
        sharedEvaluationTimeNs.addAndGet(localEvaluationTimeNs)

        return bestEvaluation ?: MoveEvaluation(legalMoves.first(), 0, 1, position)
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
    private fun alphaBeta(position: ChessPosition, depth: Int, alpha: Int, beta: Int, initialDepth: Int): Pair<Int, ChessPosition?> {
        // Probe Transposition Table
        val ttResult = transpositionTable.probe(position.zobristHash, depth, alpha, beta)
        if (ttResult != null) {
            // TT hit with usable score
            val (score, bestMove) = ttResult
            val resultPosition = if (bestMove != null) {
                position.movePiece(bestMove.from, bestMove.to, bestMove.promotion)
            } else {
                null
            }
            return score to resultPosition
        }

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
            return -100000 - depth to null
        }

        // Base case: leaf node (max depth reached)
        if (depth == 0) {
            var result = 0
            evaluationTime += measureTime {
                result = position.value
            }
            return result to position
        }

        // Order moves for maximum alpha-beta efficiency
        // Get best move from TT for move ordering
        val ttBestMove = transpositionTable.getBestMove(position.zobristHash)
        val orderedMoves = orderMoves(position, legalMoves, ttBestMove)

        var currentAlpha = alpha
        var bestScore = -1_000_000  // Use safe value instead of Int.MIN_VALUE
        var bestPosition : ChessPosition? = null
        var bestMove: Move? = null

        for (move in orderedMoves) {
            val newPosition = position.movePiece(move.from, move.to, move.promotion)

            // Recursive alpha-beta search with negated window (negamax pattern)

            val (scoreNeg, positionResult) = alphaBeta(newPosition, depth - 1, -beta, -currentAlpha, initialDepth)
            val score = - scoreNeg

            if (score > bestScore) {
                bestScore = score
                bestPosition = positionResult
                bestMove = move
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

                // Store in TT as LOWERBOUND (beta cutoff)
                transpositionTable.store(position.zobristHash, depth, bestScore, bestMove, TTNodeType.LOWERBOUND)
                return bestScore to bestPosition
            }
        }

        // Determine node type for TT
        val nodeType = when {
            bestScore <= alpha -> TTNodeType.UPPERBOUND  // Failed low (alpha cutoff)
            else -> TTNodeType.EXACT  // Exact score (PV node)
        }

        // Store in Transposition Table
        transpositionTable.store(position.zobristHash, depth, bestScore, bestMove, nodeType)

        return bestScore to bestPosition
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
     * @param ttBestMove Best move from Transposition Table (highest priority)
     * @return Ordered list (best moves first)
     */
    private fun orderMoves(position: ChessPosition, moves: List<Move>, ttBestMove: Move? = null): List<Move> {
        val sorted = moves.sortedByDescending { move ->
            // Highest priority: TT best move
            if (ttBestMove != null && move == ttBestMove) {
                return@sortedByDescending 1000000
            }

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
        return sorted
    }

    /**
     * Orders moves with PV (Principal Variation) move first for better alpha-beta efficiency.
     *
     * The PV move is the best move from the previous iteration of iterative deepening.
     * Placing it first dramatically improves alpha-beta cutoffs.
     *
     * @param position Current position
     * @param moves List of legal moves to order
     * @param pvMove Best move from previous iteration
     * @return Ordered list with PV move first, followed by other moves in order
     */
    private fun orderMovesWithPV(position: ChessPosition, moves: List<Move>, pvMove: Move): List<Move> {
        // First, order moves normally
        val orderedMoves = orderMoves(position, moves).toMutableList()

        // Find the PV move in the list
        val pvIndex = orderedMoves.indexOfFirst { it == pvMove }

        // If found and not already first, move it to the front
        if (pvIndex > 0) {
            val pv = orderedMoves.removeAt(pvIndex)
            orderedMoves.add(0, pv)
        }

        return orderedMoves
    }
}