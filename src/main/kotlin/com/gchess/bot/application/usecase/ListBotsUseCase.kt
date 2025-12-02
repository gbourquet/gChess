package com.gchess.bot.application.usecase

import com.gchess.bot.domain.model.Bot
import com.gchess.bot.domain.port.BotRepository

class ListBotsUseCase(
    private val botRepository: BotRepository
) {
    suspend fun execute(): Result<List<Bot>> {
        val bots = botRepository.findAll()
        return Result.success(bots)
    }
}
