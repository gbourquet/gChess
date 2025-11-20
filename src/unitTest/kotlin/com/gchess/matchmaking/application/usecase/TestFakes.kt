package com.gchess.matchmaking.application.usecase

import com.gchess.chess.domain.port.PlayerExistenceChecker
import com.gchess.matchmaking.domain.port.GameCreator
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId

/**
 * Fake PlayerExistenceChecker for testing use cases.
 */
class FakePlayerExistenceChecker(
    private val exists: Boolean
) : PlayerExistenceChecker {
    override suspend fun exists(playerId: PlayerId): Boolean = exists
}

/**
 * Fake GameCreator for testing use cases.
 */
class FakeGameCreator(
    private val gameId: GameId? = null,
    private val failure: Exception? = null
) : GameCreator {
    override suspend fun createGame(whitePlayerId: PlayerId, blackPlayerId: PlayerId): Result<GameId> {
        return if (failure != null) {
            Result.failure(failure)
        } else {
            Result.success(gameId ?: GameId.generate())
        }
    }
}
