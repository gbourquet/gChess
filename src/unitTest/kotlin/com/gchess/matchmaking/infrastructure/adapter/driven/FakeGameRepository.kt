package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.chess.domain.model.Game
import com.gchess.chess.domain.port.GameRepository
import com.gchess.shared.domain.model.GameId

/**
 * Fake GameRepository for testing purposes.
 * Simulates game storage without actual persistence.
 */
class FakeGameRepository : GameRepository {
    private val games = mutableMapOf<GameId, Game>()

    override suspend fun save(game: Game): Game {
        games[game.id] = game
        return game
    }

    override suspend fun findById(id: GameId): Game? {
        return games[id]
    }

    override suspend fun delete(id: GameId) {
        games.remove(id)
    }

    override suspend fun findAll(): List<Game> {
        return games.values.toList()
    }
}
