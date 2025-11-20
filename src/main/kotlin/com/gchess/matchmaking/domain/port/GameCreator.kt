package com.gchess.matchmaking.domain.port

import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId

/**
 * Port (interface) for creating chess games.
 *
 * This is an Anti-Corruption Layer (ACL) port that allows the Matchmaking context
 * to request game creation from the Chess context without direct dependency.
 *
 * The implementation will adapt calls to the Chess context's CreateGameUseCase.
 */
interface GameCreator {
    /**
     * Creates a new chess game between two players.
     *
     * @param whitePlayerId The player who will control white pieces
     * @param blackPlayerId The player who will control black pieces
     * @return Result containing the created GameId on success, or an error on failure
     */
    suspend fun createGame(whitePlayerId: PlayerId, blackPlayerId: PlayerId): Result<GameId>
}
