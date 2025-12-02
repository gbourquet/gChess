package com.gchess.bot.domain.model

import com.gchess.shared.domain.model.UserId

data class Bot(
    val id: BotId,
    val name: String,
    val systemUserId: UserId,
    val difficulty: BotDifficulty,
    val description: String,
    val engineType: String = "MINIMAX"
)
