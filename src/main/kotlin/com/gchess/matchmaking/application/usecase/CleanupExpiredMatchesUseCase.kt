package com.gchess.matchmaking.application.usecase

import com.gchess.matchmaking.domain.port.MatchRepository

/**
 * Use case for cleaning up expired matches from the repository.
 *
 * This use case is typically called:
 * - Periodically by a scheduler/cron job
 * - Before checking match status (in GetMatchStatusUseCase)
 * - Via an admin endpoint
 *
 * @property matchRepository Repository for managing matches
 */
class CleanupExpiredMatchesUseCase(
    private val matchRepository: MatchRepository
) {
    /**
     * Removes all expired matches from the repository.
     *
     * A match is considered expired when Clock.System.now() > match.expiresAt
     */
    suspend fun execute() {
        matchRepository.deleteExpiredMatches()
    }
}
