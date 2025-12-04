package com.gchess.bot.domain.model

/**
 * Bot difficulty levels with search depths optimized for alpha-beta pruning.
 *
 * Phase 4: Increased depths thanks to alpha-beta pruning efficiency.
 * Alpha-beta reduces search complexity from O(b^d) to ~O(b^(d/2)) with good move ordering.
 */
enum class BotDifficulty(val searchDepth: Int) {
    BEGINNER(2),
    INTERMEDIATE(4),
    ADVANCED(5),
    MASTER(7)
}
