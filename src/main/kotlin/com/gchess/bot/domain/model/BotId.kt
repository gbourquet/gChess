package com.gchess.bot.domain.model

import de.huxhorn.sulky.ulid.ULID

@JvmInline
value class BotId(val value: String) {
    companion object {
        fun generate() = BotId(ULID().nextULID())
        fun fromString(value: String) = BotId(value)
    }

    override fun toString(): String = value
}
