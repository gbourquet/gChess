package com.gchess.application.usecase

import com.gchess.domain.model.Game
import com.gchess.domain.port.GameRepository
import java.util.UUID

class CreateGameUseCase(
    private val gameRepository: GameRepository
) {
    suspend fun execute(): Game {
        val game = Game(id = UUID.randomUUID().toString())
        return gameRepository.save(game)
    }
}
