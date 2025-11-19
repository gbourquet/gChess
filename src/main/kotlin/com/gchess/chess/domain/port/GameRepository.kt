package com.gchess.chess.domain.port

import com.gchess.chess.domain.model.Game
import com.gchess.shared.domain.model.GameId

interface GameRepository {
    suspend fun save(game: Game): Game
    suspend fun findById(id: GameId): Game?
    suspend fun delete(id: GameId)
    suspend fun findAll(): List<Game>
}
