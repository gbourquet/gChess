package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.chess.application.usecase.CreateGameUseCase
import com.gchess.matchmaking.domain.port.GameCreator
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId

/**
 * Anti-Corruption Layer adapter that allows the Matchmaking context
 * to create games by communicating with the Chess context.
 *
 * This adapter:
 * - Implements the GameCreator port (defined in Matchmaking domain)
 * - Calls CreateGameUseCase from the Chess context
 * - Transforms Result<Game> to Result<GameId>
 * - Maintains bounded context isolation (Matchmaking doesn't depend on Chess domain models)
 */
class ChessContextGameCreator(
    private val createGameUseCase: CreateGameUseCase
) : GameCreator {

    override suspend fun createGame(whitePlayerId: PlayerId, blackPlayerId: PlayerId): Result<GameId> {
        // Call Chess context use case
        val gameResult = createGameUseCase.execute(whitePlayerId, blackPlayerId)

        // Transform Result<Game> to Result<GameId>
        return gameResult.map { game ->
            game.id // Extract GameId from Game entity
        }
    }
}
