package com.gchess.domain.port

import com.gchess.domain.model.Game

interface GameRepository {
    suspend fun save(game: Game): Game
    suspend fun findById(id: String): Game?
    suspend fun delete(id: String)
    suspend fun findAll(): List<Game>
}
