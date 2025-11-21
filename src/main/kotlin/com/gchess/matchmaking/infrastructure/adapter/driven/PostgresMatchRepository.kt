/*
 * Copyright (c) 2024-2025 Guillaume Bourquet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.infrastructure.persistence.jooq.tables.references.MATCHES
import com.gchess.matchmaking.domain.model.Match
import com.gchess.matchmaking.domain.port.MatchRepository
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jooq.DSLContext
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * PostgreSQL implementation of MatchRepository using jOOQ.
 *
 * This repository manages matches with TTL (Time To Live) support.
 *
 * Key features:
 * - Upsert support (INSERT ... ON CONFLICT DO NOTHING)
 * - Find match by either player ID (white or black)
 * - Delete match by player ID (removes for both players)
 * - Cleanup expired matches based on expires_at timestamp
 * - Timestamp conversion: kotlinx.datetime.Instant ↔ java.time.LocalDateTime (UTC)
 */
class PostgresMatchRepository(
    private val dsl: DSLContext
) : MatchRepository {

    override suspend fun save(match: Match): Unit = withContext(Dispatchers.IO) {
        dsl.insertInto(MATCHES)
            .set(MATCHES.GAME_ID, match.gameId.value)
            .set(MATCHES.WHITE_PLAYER_ID, match.whitePlayerId.value)
            .set(MATCHES.BLACK_PLAYER_ID, match.blackPlayerId.value)
            .set(MATCHES.MATCHED_AT, match.matchedAt.toLocalDateTime())
            .set(MATCHES.EXPIRES_AT, match.expiresAt.toLocalDateTime())
            .onConflict(MATCHES.GAME_ID)
            .doNothing()
            .execute()
    }

    override suspend fun findByPlayer(playerId: PlayerId): Match? = withContext(Dispatchers.IO) {
        dsl.select(
                MATCHES.GAME_ID,
                MATCHES.WHITE_PLAYER_ID,
                MATCHES.BLACK_PLAYER_ID,
                MATCHES.MATCHED_AT,
                MATCHES.EXPIRES_AT
            )
            .from(MATCHES)
            .where(
                MATCHES.WHITE_PLAYER_ID.eq(playerId.value)
                    .or(MATCHES.BLACK_PLAYER_ID.eq(playerId.value))
            )
            .fetchOne()
            ?.let { record ->
                Match(
                    whitePlayerId = PlayerId(record.value2()!!),
                    blackPlayerId = PlayerId(record.value3()!!),
                    gameId = GameId(record.value1()!!),
                    matchedAt = record.value4()!!.toInstant(),
                    expiresAt = record.value5()!!.toInstant()
                )
            }
    }

    override suspend fun delete(playerId: PlayerId): Unit = withContext(Dispatchers.IO) {
        dsl.deleteFrom(MATCHES)
            .where(
                MATCHES.WHITE_PLAYER_ID.eq(playerId.value)
                    .or(MATCHES.BLACK_PLAYER_ID.eq(playerId.value))
            )
            .execute()
    }

    override suspend fun deleteExpiredMatches(): Unit = withContext(Dispatchers.IO) {
        val now = kotlinx.datetime.Clock.System.now().toLocalDateTime()
        dsl.deleteFrom(MATCHES)
            .where(MATCHES.EXPIRES_AT.lessThan(now))
            .execute()
    }

    // Extension functions for timestamp conversion (UTC timezone)

    /**
     * Converts kotlinx.datetime.Instant to java.time.LocalDateTime (UTC).
     */
    private fun Instant.toLocalDateTime(): LocalDateTime {
        return this.toJavaInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
    }

    /**
     * Converts java.time.LocalDateTime to kotlinx.datetime.Instant (assumes UTC).
     */
    private fun LocalDateTime.toInstant(): Instant {
        return this.atZone(ZoneOffset.UTC).toInstant().toKotlinInstant()
    }
}
