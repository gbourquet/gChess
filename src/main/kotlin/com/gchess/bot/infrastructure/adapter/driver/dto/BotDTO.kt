package com.gchess.bot.infrastructure.adapter.driver.dto

import com.gchess.bot.domain.model.Bot
import kotlinx.serialization.Serializable

@Serializable
data class BotDTO(
    val id: String,
    val name: String,
    val difficulty: String,
    val description: String
)

fun Bot.toDTO() = BotDTO(
    id = id.value,
    name = name,
    difficulty = difficulty.name,
    description = description
)
