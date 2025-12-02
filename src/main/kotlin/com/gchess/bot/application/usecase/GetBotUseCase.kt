package com.gchess.bot.application.usecase

import com.gchess.bot.domain.model.Bot
import com.gchess.bot.domain.model.BotId
import com.gchess.bot.domain.port.BotRepository

class GetBotUseCase(
    private val botRepository: BotRepository
) {
    suspend fun execute(botId: BotId): Result<Bot> {
        val bot = botRepository.findById(botId)
            ?: return Result.failure(Exception("Bot not found with id: ${botId.value}"))

        return Result.success(bot)
    }
}
