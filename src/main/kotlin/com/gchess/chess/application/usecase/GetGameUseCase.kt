package com.gchess.chess.application.usecase

import com.gchess.chess.domain.model.Game
import com.gchess.chess.domain.port.GameRepository
import com.gchess.shared.domain.model.GameId

class GetGameUseCase(
    private val gameRepository: GameRepository
) {
    suspend fun execute(gameId: GameId): Game? {
        return gameRepository.findById(gameId)
    }
}
