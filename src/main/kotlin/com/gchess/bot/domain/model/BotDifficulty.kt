package com.gchess.bot.domain.model

/**
 * Bot difficulty levels with search depths and parallel workers for Lazy SMP.
 *
 * Lazy SMP with coroutines:
 * - Coroutines are lightweight (few KB each) vs native threads (MB each)
 * - Can launch many more workers than physical CPU cores
 * - More workers = better tree exploration diversity + less TT contention
 * - Optimal count depends on search depth and position complexity
 *
 * @property searchDepth Maximum search depth (plies)
 * @property numWorkers Number of parallel coroutine workers for Lazy SMP
 */
enum class BotDifficulty(
    val searchDepth: Int,
    val numWorkers: Int
) {
    /** Shallow search, few workers */
    BEGINNER(2, 4),

    /** Medium search, moderate parallelism */
    INTERMEDIATE(4, 8),

    /** Deep search, high parallelism */
    ADVANCED(5, 16),

    /** Very deep search, maximum parallelism */
    MASTER(7, 128)
}
