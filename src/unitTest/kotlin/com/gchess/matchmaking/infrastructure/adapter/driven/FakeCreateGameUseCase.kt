package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.chess.application.usecase.CreateGameUseCase
import com.gchess.chess.domain.model.Game
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId

/**
 * Fake CreateGameUseCase for testing purposes.
 * Allows controlling the result of game creation without actual business logic.
 */
class FakeCreateGameUseCase(
    var result: Result<Game> = Result.success(
        Game(
            id = GameId.generate(),
            whitePlayer = PlayerId.generate(),
            blackPlayer = PlayerId.generate()
        )
    )
) : CreateGameUseCase(
    gameRepository = FakeGameRepository(),
    playerExistenceChecker = FakePlayerExistenceChecker()
) {
    override suspend fun execute(whitePlayerId: PlayerId, blackPlayerId: PlayerId): Result<Game> {
        return result
    }
}
