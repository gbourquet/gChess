/*
 * Copyright (c) 2024-2025 Guillaume Bourquet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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
