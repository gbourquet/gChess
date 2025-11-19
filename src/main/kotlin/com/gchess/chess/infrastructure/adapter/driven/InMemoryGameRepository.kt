package com.gchess.chess.infrastructure.adapter.driven

import com.gchess.chess.domain.model.Game
import com.gchess.chess.domain.port.GameRepository
import com.gchess.shared.domain.model.GameId
import java.util.concurrent.ConcurrentHashMap

class InMemoryGameRepository : GameRepository {
    private val games = ConcurrentHashMap<String, Game>()

    override suspend fun save(game: Game): Game {
        games[game.id.value] = game
        return game
    }

    override suspend fun findById(id: GameId): Game? {
        return games[id.value]
    }

    override suspend fun delete(id: GameId) {
        games.remove(id.value)
    }

    override suspend fun findAll(): List<Game> {
        return games.values.toList()
    }
}
