package com.gchess.bot.infrastructure.adapter.driven

import com.gchess.bot.domain.model.Bot
import com.gchess.bot.domain.model.BotDifficulty
import com.gchess.bot.domain.model.BotId
import com.gchess.bot.domain.port.BotRepository
import com.gchess.infrastructure.persistence.jooq.tables.references.BOTS
import com.gchess.shared.domain.model.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext

/**
 * PostgreSQL implementation of BotRepository using jOOQ.
 *
 * Default bots are initialized via Liquibase migration (20251128-insert-default-bots.xml).
 * Bot system users have username pattern "bot_*" and cannot authenticate (password_hash = "N/A").
 */
class PostgresBotRepository(
    private val dsl: DSLContext
) : BotRepository {

    override suspend fun save(bot: Bot): Unit = withContext(Dispatchers.IO) {
        dsl.insertInto(BOTS)
            .set(BOTS.ID, bot.id.value)
            .set(BOTS.NAME, bot.name)
            .set(BOTS.SYSTEM_USER_ID, bot.systemUserId.value)
            .set(BOTS.DIFFICULTY, bot.difficulty.name)
            .set(BOTS.DESCRIPTION, bot.description)
            .set(BOTS.ENGINE_TYPE, bot.engineType)
            .onConflict(BOTS.ID)
            .doUpdate()
            .set(BOTS.NAME, bot.name)
            .set(BOTS.SYSTEM_USER_ID, bot.systemUserId.value)
            .set(BOTS.DIFFICULTY, bot.difficulty.name)
            .set(BOTS.DESCRIPTION, bot.description)
            .set(BOTS.ENGINE_TYPE, bot.engineType)
            .execute()
        Unit
    }

    override suspend fun findById(id: BotId): Bot? = withContext(Dispatchers.IO) {
        dsl.select(BOTS.ID, BOTS.NAME, BOTS.SYSTEM_USER_ID, BOTS.DIFFICULTY, BOTS.DESCRIPTION, BOTS.ENGINE_TYPE)
            .from(BOTS)
            .where(BOTS.ID.eq(id.value))
            .fetchOne()
            ?.let { record ->
                Bot(
                    id = BotId.fromString(record.value1()!!),
                    name = record.value2()!!,
                    systemUserId = UserId(record.value3()!!),
                    difficulty = BotDifficulty.valueOf(record.value4()!!),
                    description = record.value5() ?: "",
                    engineType = record.value6() ?: "MINIMAX"
                )
            }
    }

    override suspend fun findBySystemUserId(userId: UserId): Bot? = withContext(Dispatchers.IO) {
        dsl.select(BOTS.ID, BOTS.NAME, BOTS.SYSTEM_USER_ID, BOTS.DIFFICULTY, BOTS.DESCRIPTION, BOTS.ENGINE_TYPE)
            .from(BOTS)
            .where(BOTS.SYSTEM_USER_ID.eq(userId.value))
            .fetchOne()
            ?.let { record ->
                Bot(
                    id = BotId.fromString(record.value1()!!),
                    name = record.value2()!!,
                    systemUserId = UserId(record.value3()!!),
                    difficulty = BotDifficulty.valueOf(record.value4()!!),
                    description = record.value5() ?: "",
                    engineType = record.value6() ?: "MINIMAX"
                )
            }
    }

    override suspend fun findByDifficulty(difficulty: BotDifficulty): List<Bot> = withContext(Dispatchers.IO) {
        dsl.select(BOTS.ID, BOTS.NAME, BOTS.SYSTEM_USER_ID, BOTS.DIFFICULTY, BOTS.DESCRIPTION, BOTS.ENGINE_TYPE)
            .from(BOTS)
            .where(BOTS.DIFFICULTY.eq(difficulty.name))
            .fetch()
            .map { record ->
                Bot(
                    id = BotId.fromString(record.value1()!!),
                    name = record.value2()!!,
                    systemUserId = UserId(record.value3()!!),
                    difficulty = BotDifficulty.valueOf(record.value4()!!),
                    description = record.value5() ?: "",
                    engineType = record.value6() ?: "MINIMAX"
                )
            }
    }

    override suspend fun findAll(): List<Bot> = withContext(Dispatchers.IO) {
        dsl.select(BOTS.ID, BOTS.NAME, BOTS.SYSTEM_USER_ID, BOTS.DIFFICULTY, BOTS.DESCRIPTION, BOTS.ENGINE_TYPE)
            .from(BOTS)
            .fetch()
            .map { record ->
                Bot(
                    id = BotId.fromString(record.value1()!!),
                    name = record.value2()!!,
                    systemUserId = UserId(record.value3()!!),
                    difficulty = BotDifficulty.valueOf(record.value4()!!),
                    description = record.value5() ?: "",
                    engineType = record.value6() ?: "MINIMAX"
                )
            }
    }

    override suspend fun count(): Int = withContext(Dispatchers.IO) {
        dsl.selectCount()
            .from(BOTS)
            .fetchOne(0, Int::class.java) ?: 0
    }
}
