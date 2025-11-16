package com.gchess.application.usecase

import com.gchess.domain.model.Game
import com.gchess.domain.port.GameRepository

class GetGameUseCase(
    private val gameRepository: GameRepository
) {
    suspend fun execute(gameId: String): Game? {
        return gameRepository.findById(gameId)
    }
}
