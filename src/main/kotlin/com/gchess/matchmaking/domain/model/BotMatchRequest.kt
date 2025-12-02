package com.gchess.matchmaking.domain.model

import com.gchess.bot.domain.model.BotId
import com.gchess.shared.domain.model.PlayerSide

/**
 * Request to match with a bot instead of a human player.
 *
 * @param botId Specific bot to play against, or null for random/default bot
 * @param playerColor Desired color for the human player, or null for random
 */
data class BotMatchRequest(
    val botId: BotId? = null,
    val playerColor: PlayerSide? = null
)
