package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.chess.domain.port.PlayerExistenceChecker
import com.gchess.shared.domain.model.PlayerId

/**
 * Fake PlayerExistenceChecker for testing purposes.
 * Allows controlling whether players exist or not.
 */
class FakePlayerExistenceChecker(
    private val alwaysExists: Boolean = true
) : PlayerExistenceChecker {
    override suspend fun exists(playerId: PlayerId): Boolean {
        return alwaysExists
    }
}
