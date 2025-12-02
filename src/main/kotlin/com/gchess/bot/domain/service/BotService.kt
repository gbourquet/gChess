package com.gchess.bot.domain.service

import com.gchess.bot.domain.model.BotDifficulty
import com.gchess.bot.domain.model.MoveEvaluation
import com.gchess.chess.domain.model.ChessPosition

/**
 * Port for chess engine that calculates the best move for a given position.
 *
 * Implementations will use various search algorithms (Minimax, Alpha-Beta, etc.)
 * combined with position evaluation heuristics.
 */
interface BotService {
    /**
     * Calculates the best move for the given position.
     *
     * @param position The current chess position
     * @param difficulty The difficulty level determining search depth
     * @return Result with MoveEvaluation containing the best move and its score, or failure if no legal moves
     */
    suspend fun calculateBestMove(
        position: ChessPosition,
        difficulty: BotDifficulty
    ): Result<MoveEvaluation>
}