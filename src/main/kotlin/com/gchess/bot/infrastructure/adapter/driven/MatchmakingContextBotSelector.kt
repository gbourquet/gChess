package com.gchess.bot.infrastructure.adapter.driven

import com.gchess.bot.domain.model.BotDifficulty
import com.gchess.bot.domain.model.BotId
import com.gchess.bot.domain.port.BotRepository
import com.gchess.matchmaking.domain.port.BotInfo
import com.gchess.matchmaking.domain.port.BotSelector

/**
 * Anti-Corruption Layer adapter from Matchmaking to Bot context.
 *
 * Implements Matchmaking's BotSelector port by translating to Bot repository calls.
 * This isolates Matchmaking from Bot domain knowledge.
 */
class MatchmakingContextBotSelector(
    private val botRepository: BotRepository
) : BotSelector {

    override suspend fun selectBot(botId: BotId?): Result<BotInfo> {
        val bot = if (botId != null) {
            // Select specific bot by ID
            botRepository.findById(botId)
        } else {
            // Select default bot (BEGINNER difficulty)
            botRepository.findByDifficulty(BotDifficulty.BEGINNER).firstOrNull()
        }

        return if (bot != null) {
            Result.success(
                BotInfo(
                    id = bot.id,
                    systemUserId = bot.systemUserId,
                    name = bot.name,
                    difficulty = bot.difficulty.name
                )
            )
        } else {
            Result.failure(Exception("No bot available"))
        }
    }
}
