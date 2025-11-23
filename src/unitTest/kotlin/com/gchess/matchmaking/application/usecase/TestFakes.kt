package com.gchess.matchmaking.application.usecase

import com.gchess.shared.domain.model.Player
import com.gchess.matchmaking.domain.port.GameCreator
import com.gchess.matchmaking.domain.port.UserExistenceChecker
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.UserId

/**
 * Fake UserExistenceChecker for testing use cases.
 */
class FakeUserExistenceChecker(
    private val exists: Boolean
) : UserExistenceChecker {
    override suspend fun exists(userId: UserId): Boolean = exists
}

/**
 * Fake GameCreator for testing use cases.
 */
class FakeGameCreator(
    private val gameId: GameId? = null,
    private val failure: Exception? = null
) : GameCreator {
    override suspend fun createGame(whitePlayer: Player, blackPlayer: Player): Result<GameId> {
        return if (failure != null) {
            Result.failure(failure)
        } else {
            Result.success(gameId ?: GameId.generate())
        }
    }
}
