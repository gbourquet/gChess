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
package com.gchess.chess.infrastructure.adapter.driven

import com.gchess.chess.domain.model.*
import com.gchess.chess.domain.port.GameRepository
import com.gchess.infrastructure.persistence.jooq.tables.references.GAMES
import com.gchess.infrastructure.persistence.jooq.tables.references.GAME_MOVES
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.PlayerId
import com.gchess.shared.domain.model.PlayerSide
import com.gchess.shared.domain.model.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * PostgreSQL implementation of GameRepository using jOOQ.
 *
 * This repository manages both the games table and game_moves table,
 * ensuring transactional consistency when saving games with their move history.
 *
 * Key features:
 * - Transactional save() using jOOQ transactions
 * - ChessPosition stored as FEN (Forsyth-Edwards Notation)
 * - Moves stored separately with proper ordering (move_number)
 * - CASCADE delete for game_moves when game is deleted
 */
class PostgresGameRepository(
    private val dsl: DSLContext
) : GameRepository {

    private fun loadMoves(gameId: String): List<Move> {
        val records = dsl.select(
                GAME_MOVES.FROM_SQUARE,
                GAME_MOVES.TO_SQUARE,
                GAME_MOVES.PROMOTION,
                GAME_MOVES.RECEIVED_AT
            )
            .from(GAME_MOVES)
            .where(GAME_MOVES.GAME_ID.eq(gameId))
            .orderBy(GAME_MOVES.MOVE_NUMBER.asc())
            .fetch()

        return records.mapIndexed { index, record ->
            val from = Position.fromAlgebraic(record.value1()!!)
            val to = Position.fromAlgebraic(record.value2()!!)
            val promotion = record.value3()?.let { PieceType.valueOf(it) }
            val receivedAt = record.value4()
            val timeSpentMs = if (index > 0) {
                val prev = records[index - 1].value4()
                if (prev != null && receivedAt != null)
                    java.time.Duration.between(prev, receivedAt).toMillis()
                else null
            } else null
            Move(from, to, promotion, timeSpentMs)
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun save(game: Game): Game = withContext(Dispatchers.IO) {
        dsl.transactionResult { config ->
            val ctx = config.dsl()
            val now = LocalDateTime.now()
            val lastMoveAtDb = game.lastMoveAt?.let { instant ->
                java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
                    .atOffset(ZoneOffset.UTC).toLocalDateTime()
            }

            // Upsert game record
            // Note: We now persist both PlayerId and UserId for each player
            ctx.insertInto(GAMES)
                .set(GAMES.ID, game.id.value)
                .set(GAMES.WHITE_PLAYER_ID, game.whitePlayer.id.value)
                .set(GAMES.WHITE_USER_ID, game.whitePlayer.userId.value)
                .set(GAMES.BLACK_PLAYER_ID, game.blackPlayer.id.value)
                .set(GAMES.BLACK_USER_ID, game.blackPlayer.userId.value)
                .set(GAMES.BOARD_FEN, game.board.toFen())
                .set(GAMES.CURRENT_SIDE, game.currentSide.name)
                .set(GAMES.STATUS, game.status.name)
                .set(GAMES.DRAW_OFFERED_BY, game.drawOfferedBy?.name)
                .set(GAMES.TIME_CONTROL_TOTAL_SECONDS, game.timeControl?.totalTimeSeconds)
                .set(GAMES.TIME_CONTROL_INCREMENT_SECONDS, game.timeControl?.incrementSeconds)
                .set(GAMES.WHITE_TIME_REMAINING_MS, game.whiteTimeRemainingMs)
                .set(GAMES.BLACK_TIME_REMAINING_MS, game.blackTimeRemainingMs)
                .set(GAMES.LAST_MOVE_AT, lastMoveAtDb)
                .set(DSL.field("winner_side", String::class.java), game.winnerSide?.name)
                .set(GAMES.CREATED_AT, now)
                .set(GAMES.UPDATED_AT, now)
                .onConflict(GAMES.ID)
                .doUpdate()
                .set(GAMES.BOARD_FEN, game.board.toFen())
                .set(GAMES.CURRENT_SIDE, game.currentSide.name)
                .set(GAMES.STATUS, game.status.name)
                .set(GAMES.DRAW_OFFERED_BY, game.drawOfferedBy?.name)
                .set(GAMES.WHITE_TIME_REMAINING_MS, game.whiteTimeRemainingMs)
                .set(GAMES.BLACK_TIME_REMAINING_MS, game.blackTimeRemainingMs)
                .set(GAMES.LAST_MOVE_AT, lastMoveAtDb)
                .set(DSL.field("winner_side", String::class.java), game.winnerSide?.name)
                .set(GAMES.UPDATED_AT, now)
                .execute()

            // Insert only new moves (ON CONFLICT DO NOTHING preserves original received_at)
            game.moveHistory.forEachIndexed { index, move ->
                ctx.insertInto(GAME_MOVES)
                    .set(GAME_MOVES.GAME_ID, game.id.value)
                    .set(GAME_MOVES.MOVE_NUMBER, index)
                    .set(GAME_MOVES.FROM_SQUARE, move.from.toAlgebraic())
                    .set(GAME_MOVES.TO_SQUARE, move.to.toAlgebraic())
                    .set(GAME_MOVES.PROMOTION, move.promotion?.name)
                    .set(GAME_MOVES.CREATED_AT, now)
                    .set(GAME_MOVES.RECEIVED_AT, now)
                    .onConflict(GAME_MOVES.GAME_ID, GAME_MOVES.MOVE_NUMBER)
                    .doNothing()
                    .execute()
            }

            game
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun findById(id: GameId): Game? = withContext(Dispatchers.IO) {
        // Fetch game record
        val winnerSideField = DSL.field("winner_side", String::class.java)
        val gameRecord = dsl.select(
                GAMES.ID,
                GAMES.WHITE_PLAYER_ID,
                GAMES.WHITE_USER_ID,
                GAMES.BLACK_PLAYER_ID,
                GAMES.BLACK_USER_ID,
                GAMES.BOARD_FEN,
                GAMES.CURRENT_SIDE,
                GAMES.STATUS,
                GAMES.DRAW_OFFERED_BY,
                GAMES.TIME_CONTROL_TOTAL_SECONDS,
                GAMES.TIME_CONTROL_INCREMENT_SECONDS,
                GAMES.WHITE_TIME_REMAINING_MS,
                GAMES.BLACK_TIME_REMAINING_MS,
                GAMES.LAST_MOVE_AT,
                winnerSideField
            )
            .from(GAMES)
            .where(GAMES.ID.eq(id.value))
            .fetchOne()
            ?: return@withContext null

        val moves = loadMoves(id.value)

        // Reconstruct Game domain model
        // Note: Both PlayerIds and UserIds are now persisted and loaded
        val whitePlayerId = PlayerId.fromString(gameRecord.value2()!!)
        val whiteUserId = UserId.fromString(gameRecord.value3()!!)
        val blackPlayerId = PlayerId.fromString(gameRecord.value4()!!)
        val blackUserId = UserId.fromString(gameRecord.value5()!!)

        val whitePlayer = Player(whitePlayerId, whiteUserId, PlayerSide.WHITE)
        val blackPlayer = Player(blackPlayerId, blackUserId, PlayerSide.BLACK)

        val totalSeconds = gameRecord.value10()
        val incrementSeconds = gameRecord.value11()
        val timeControl = if (totalSeconds != null && incrementSeconds != null) {
            TimeControl(totalSeconds, incrementSeconds)
        } else null

        val lastMoveAt = gameRecord.value14()?.let { ldt ->
                Instant.fromEpochMilliseconds(ldt.toInstant(ZoneOffset.UTC).toEpochMilli())
            }

        Game(
            id = GameId(gameRecord.value1()!!),
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = gameRecord.value6()!!.toChessPosition(),
            currentSide = PlayerSide.valueOf(gameRecord.value7()!!),
            status = GameStatus.valueOf(gameRecord.value8()!!),
            moveHistory = moves,
            drawOfferedBy = gameRecord.value9()?.let { PlayerSide.valueOf(it) },
            timeControl = timeControl,
            whiteTimeRemainingMs = gameRecord.value12(),
            blackTimeRemainingMs = gameRecord.value13(),
            lastMoveAt = lastMoveAt,
            winnerSide = gameRecord.get(winnerSideField)?.let { PlayerSide.valueOf(it) }
        )
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun findByUserId(userId: UserId): List<Game> = withContext(Dispatchers.IO) {
        val winnerSideField = DSL.field("winner_side", String::class.java)
        val gameRecords = dsl.select(
                GAMES.ID,
                GAMES.WHITE_PLAYER_ID,
                GAMES.WHITE_USER_ID,
                GAMES.BLACK_PLAYER_ID,
                GAMES.BLACK_USER_ID,
                GAMES.BOARD_FEN,
                GAMES.CURRENT_SIDE,
                GAMES.STATUS,
                GAMES.DRAW_OFFERED_BY,
                GAMES.TIME_CONTROL_TOTAL_SECONDS,
                GAMES.TIME_CONTROL_INCREMENT_SECONDS,
                GAMES.WHITE_TIME_REMAINING_MS,
                GAMES.BLACK_TIME_REMAINING_MS,
                GAMES.LAST_MOVE_AT,
                winnerSideField
            )
            .from(GAMES)
            .where(GAMES.WHITE_USER_ID.eq(userId.value).or(GAMES.BLACK_USER_ID.eq(userId.value)))
            .fetch()

        gameRecords.mapNotNull { gameRecord ->
            val gameId = gameRecord.value1() ?: return@mapNotNull null

            val moves = loadMoves(gameId)

            val whitePlayerId = PlayerId.fromString(gameRecord.value2()!!)
            val whiteUserId = UserId.fromString(gameRecord.value3()!!)
            val blackPlayerId = PlayerId.fromString(gameRecord.value4()!!)
            val blackUserId = UserId.fromString(gameRecord.value5()!!)

            val whitePlayer = Player(whitePlayerId, whiteUserId, PlayerSide.WHITE)
            val blackPlayer = Player(blackPlayerId, blackUserId, PlayerSide.BLACK)

            val totalSeconds = gameRecord.value10()
            val incrementSeconds = gameRecord.value11()
            val timeControl = if (totalSeconds != null && incrementSeconds != null) {
                TimeControl(totalSeconds, incrementSeconds)
            } else null

            val lastMoveAt = gameRecord.value14()?.let { ldt ->
                Instant.fromEpochMilliseconds(ldt.toInstant(ZoneOffset.UTC).toEpochMilli())
            }

            Game(
                id = GameId(gameId),
                whitePlayer = whitePlayer,
                blackPlayer = blackPlayer,
                board = gameRecord.value6()!!.toChessPosition(),
                currentSide = PlayerSide.valueOf(gameRecord.value7()!!),
                status = GameStatus.valueOf(gameRecord.value8()!!),
                moveHistory = moves,
                drawOfferedBy = gameRecord.value9()?.let { PlayerSide.valueOf(it) },
                timeControl = timeControl,
                whiteTimeRemainingMs = gameRecord.value12(),
                blackTimeRemainingMs = gameRecord.value13(),
                lastMoveAt = lastMoveAt,
                winnerSide = gameRecord.get(winnerSideField)?.let { PlayerSide.valueOf(it) }
            )
        }
    }

    override suspend fun delete(id: GameId): Unit = withContext(Dispatchers.IO) {
        dsl.deleteFrom(GAMES)
            .where(GAMES.ID.eq(id.value))
            .execute()
        // game_moves are deleted automatically via CASCADE
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun findAll(): List<Game> = withContext(Dispatchers.IO) {
        val winnerSideField = DSL.field("winner_side", String::class.java)
        // Fetch all games
        val gameRecords = dsl.select(
                GAMES.ID,
                GAMES.WHITE_PLAYER_ID,
                GAMES.WHITE_USER_ID,
                GAMES.BLACK_PLAYER_ID,
                GAMES.BLACK_USER_ID,
                GAMES.BOARD_FEN,
                GAMES.CURRENT_SIDE,
                GAMES.STATUS,
                GAMES.DRAW_OFFERED_BY,
                GAMES.TIME_CONTROL_TOTAL_SECONDS,
                GAMES.TIME_CONTROL_INCREMENT_SECONDS,
                GAMES.WHITE_TIME_REMAINING_MS,
                GAMES.BLACK_TIME_REMAINING_MS,
                GAMES.LAST_MOVE_AT,
                winnerSideField
            )
            .from(GAMES)
            .fetch()

        // For each game, fetch its moves and reconstruct
        gameRecords.mapNotNull { gameRecord ->
            val gameId = gameRecord.value1() ?: return@mapNotNull null

            val moves = loadMoves(gameId)

            // Reconstruct Player objects with persisted PlayerIds
            val whitePlayerId = PlayerId.fromString(gameRecord.value2()!!)
            val whiteUserId = UserId.fromString(gameRecord.value3()!!)
            val blackPlayerId = PlayerId.fromString(gameRecord.value4()!!)
            val blackUserId = UserId.fromString(gameRecord.value5()!!)

            val whitePlayer = Player(whitePlayerId, whiteUserId, PlayerSide.WHITE)
            val blackPlayer = Player(blackPlayerId, blackUserId, PlayerSide.BLACK)

            val totalSeconds = gameRecord.value10()
            val incrementSeconds = gameRecord.value11()
            val timeControl = if (totalSeconds != null && incrementSeconds != null) {
                TimeControl(totalSeconds, incrementSeconds)
            } else null

            val lastMoveAt = gameRecord.value14()?.let { ldt ->
                Instant.fromEpochMilliseconds(ldt.toInstant(ZoneOffset.UTC).toEpochMilli())
            }

            Game(
                id = GameId(gameId),
                whitePlayer = whitePlayer,
                blackPlayer = blackPlayer,
                board = gameRecord.value6()!!.toChessPosition(),
                currentSide = PlayerSide.valueOf(gameRecord.value7()!!),
                status = GameStatus.valueOf(gameRecord.value8()!!),
                moveHistory = moves,
                drawOfferedBy = gameRecord.value9()?.let { PlayerSide.valueOf(it) },
                timeControl = timeControl,
                whiteTimeRemainingMs = gameRecord.value12(),
                blackTimeRemainingMs = gameRecord.value13(),
                lastMoveAt = lastMoveAt,
                winnerSide = gameRecord.get(winnerSideField)?.let { PlayerSide.valueOf(it) }
            )
        }
    }
}
