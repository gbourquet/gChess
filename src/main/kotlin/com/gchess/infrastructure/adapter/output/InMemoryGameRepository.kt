package com.gchess.infrastructure.adapter.output

import com.gchess.domain.model.Game
import com.gchess.domain.port.GameRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryGameRepository : GameRepository {
    private val games = ConcurrentHashMap<String, Game>()

    override suspend fun save(game: Game): Game {
        games[game.id] = game
        return game
    }

    override suspend fun findById(id: String): Game? {
        return games[id]
    }

    override suspend fun delete(id: String) {
        games.remove(id)
    }

    override suspend fun findAll(): List<Game> {
        return games.values.toList()
    }
}
