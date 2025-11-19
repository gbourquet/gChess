package com.gchess.chess.infrastructure.adapter.driven

import com.gchess.chess.domain.port.PlayerExistenceChecker
import com.gchess.shared.domain.model.PlayerId
import com.gchess.user.application.usecase.GetUserUseCase

/**
 * Anti-Corruption Layer adapter that allows the Chess context to verify
 * player existence by communicating with the User context.
 *
 * This implementation uses a fail-fast strategy:
 * - If the User context fails, the exception is propagated immediately
 * - No fallback or pessimistic behavior
 *
 * This ensures consistency - if we can't verify player existence,
 * we don't allow game creation/moves.
 */
class UserContextPlayerChecker(
    private val getUserUseCase: GetUserUseCase
) : PlayerExistenceChecker {

    override suspend fun exists(playerId: PlayerId): Boolean {
        return try {
            val user = getUserUseCase.execute(playerId)
            user != null
        } catch (e: Exception) {
            // Fail-fast: propagate errors from User context
            throw Exception("Failed to check player existence for ${playerId.value}: ${e.message}", e)
        }
    }
}
