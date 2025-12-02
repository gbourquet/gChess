package com.gchess.bot.domain.port

import com.gchess.bot.domain.model.Bot
import com.gchess.bot.domain.model.BotDifficulty
import com.gchess.bot.domain.model.BotId
import com.gchess.shared.domain.model.UserId

interface BotRepository {
    suspend fun findById(id: BotId): Bot?
    suspend fun findBySystemUserId(userId: UserId): Bot?
    suspend fun findByDifficulty(difficulty: BotDifficulty): List<Bot>
    suspend fun findAll(): List<Bot>
    suspend fun save(bot: Bot)
    suspend fun count(): Int
}
