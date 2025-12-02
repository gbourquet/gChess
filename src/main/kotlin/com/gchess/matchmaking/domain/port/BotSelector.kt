package com.gchess.matchmaking.domain.port

import com.gchess.bot.domain.model.BotId
import com.gchess.shared.domain.model.UserId

/**
 * Anti-Corruption Layer port for selecting bots.
 *
 * This port is defined in the Matchmaking context but implemented
 * in the Bot context infrastructure layer, protecting the Matchmaking
 * domain from depending on Bot domain details.
 */
interface BotSelector {
    /**
     * Selects a bot for matchmaking.
     *
     * @param botId Specific bot to select, or null for default/random selection
     * @return BotInfo if found, or failure if no bot available
     */
    suspend fun selectBot(botId: BotId? = null): Result<BotInfo>
}

/**
 * Bot information exposed to Matchmaking context (minimal coupling).
 */
data class BotInfo(
    val id: BotId,
    val systemUserId: UserId,
    val name: String,
    val difficulty: String
)
