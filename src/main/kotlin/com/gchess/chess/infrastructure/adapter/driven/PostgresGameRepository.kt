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
import java.time.LocalDateTime

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

    override suspend fun save(game: Game): Game = withContext(Dispatchers.IO) {
        dsl.transactionResult { config ->
            val ctx = config.dsl()
            val now = LocalDateTime.now()

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
                .set(GAMES.CREATED_AT, now)
                .set(GAMES.UPDATED_AT, now)
                .onConflict(GAMES.ID)
                .doUpdate()
                .set(GAMES.BOARD_FEN, game.board.toFen())
                .set(GAMES.CURRENT_SIDE, game.currentSide.name)
                .set(GAMES.STATUS, game.status.name)
                .set(GAMES.DRAW_OFFERED_BY, game.drawOfferedBy?.name)
                .set(GAMES.UPDATED_AT, now)
                .execute()

            // Delete old moves and insert new ones (simpler than diffing)
            ctx.deleteFrom(GAME_MOVES)
                .where(GAME_MOVES.GAME_ID.eq(game.id.value))
                .execute()

            // Insert all moves with their order
            game.moveHistory.forEachIndexed { index, move ->
                ctx.insertInto(GAME_MOVES)
                    .set(GAME_MOVES.GAME_ID, game.id.value)
                    .set(GAME_MOVES.MOVE_NUMBER, index)
                    .set(GAME_MOVES.FROM_SQUARE, move.from.toAlgebraic())
                    .set(GAME_MOVES.TO_SQUARE, move.to.toAlgebraic())
                    .set(GAME_MOVES.PROMOTION, move.promotion?.name)
                    .set(GAME_MOVES.CREATED_AT, now)
                    .execute()
            }

            game
        }
    }

    override suspend fun findById(id: GameId): Game? = withContext(Dispatchers.IO) {
        // Fetch game record
        val gameRecord = dsl.select(
                GAMES.ID,
                GAMES.WHITE_PLAYER_ID,
                GAMES.WHITE_USER_ID,
                GAMES.BLACK_PLAYER_ID,
                GAMES.BLACK_USER_ID,
                GAMES.BOARD_FEN,
                GAMES.CURRENT_SIDE,
                GAMES.STATUS,
                GAMES.DRAW_OFFERED_BY
            )
            .from(GAMES)
            .where(GAMES.ID.eq(id.value))
            .fetchOne()
            ?: return@withContext null

        // Fetch moves ordered by move_number
        val moves = dsl.select(GAME_MOVES.FROM_SQUARE, GAME_MOVES.TO_SQUARE, GAME_MOVES.PROMOTION)
            .from(GAME_MOVES)
            .where(GAME_MOVES.GAME_ID.eq(id.value))
            .orderBy(GAME_MOVES.MOVE_NUMBER.asc())
            .fetch()
            .map { record ->
                val from = Position.fromAlgebraic(record.value1()!!)
                val to = Position.fromAlgebraic(record.value2()!!)
                val promotion = record.value3()?.let { PieceType.valueOf(it) }
                Move(from, to, promotion)
            }

        // Reconstruct Game domain model
        // Note: Both PlayerIds and UserIds are now persisted and loaded
        val whitePlayerId = PlayerId.fromString(gameRecord.value2()!!)
        val whiteUserId = UserId.fromString(gameRecord.value3()!!)
        val blackPlayerId = PlayerId.fromString(gameRecord.value4()!!)
        val blackUserId = UserId.fromString(gameRecord.value5()!!)

        val whitePlayer = Player(whitePlayerId, whiteUserId, PlayerSide.WHITE)
        val blackPlayer = Player(blackPlayerId, blackUserId, PlayerSide.BLACK)

        Game(
            id = GameId(gameRecord.value1()!!),
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = gameRecord.value6()!!.toChessPosition(),
            currentSide = PlayerSide.valueOf(gameRecord.value7()!!),
            status = GameStatus.valueOf(gameRecord.value8()!!),
            moveHistory = moves,
            drawOfferedBy = gameRecord.value9()?.let { PlayerSide.valueOf(it) }
        )
    }

    override suspend fun delete(id: GameId): Unit = withContext(Dispatchers.IO) {
        dsl.deleteFrom(GAMES)
            .where(GAMES.ID.eq(id.value))
            .execute()
        // game_moves are deleted automatically via CASCADE
    }

    override suspend fun findAll(): List<Game> = withContext(Dispatchers.IO) {
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
                GAMES.DRAW_OFFERED_BY
            )
            .from(GAMES)
            .fetch()

        // For each game, fetch its moves and reconstruct
        gameRecords.mapNotNull { gameRecord ->
            val gameId = gameRecord.value1() ?: return@mapNotNull null

            // Fetch moves for this game
            val moves = dsl.select(GAME_MOVES.FROM_SQUARE, GAME_MOVES.TO_SQUARE, GAME_MOVES.PROMOTION)
                .from(GAME_MOVES)
                .where(GAME_MOVES.GAME_ID.eq(gameId))
                .orderBy(GAME_MOVES.MOVE_NUMBER.asc())
                .fetch()
                .map { record ->
                    val from = Position.fromAlgebraic(record.value1()!!)
                    val to = Position.fromAlgebraic(record.value2()!!)
                    val promotion = record.value3()?.let { PieceType.valueOf(it) }
                    Move(from, to, promotion)
                }

            // Reconstruct Player objects with persisted PlayerIds
            val whitePlayerId = PlayerId.fromString(gameRecord.value2()!!)
            val whiteUserId = UserId.fromString(gameRecord.value3()!!)
            val blackPlayerId = PlayerId.fromString(gameRecord.value4()!!)
            val blackUserId = UserId.fromString(gameRecord.value5()!!)

            val whitePlayer = Player(whitePlayerId, whiteUserId, PlayerSide.WHITE)
            val blackPlayer = Player(blackPlayerId, blackUserId, PlayerSide.BLACK)

            Game(
                id = GameId(gameId),
                whitePlayer = whitePlayer,
                blackPlayer = blackPlayer,
                board = gameRecord.value6()!!.toChessPosition(),
                currentSide = PlayerSide.valueOf(gameRecord.value7()!!),
                status = GameStatus.valueOf(gameRecord.value8()!!),
                moveHistory = moves,
                drawOfferedBy = gameRecord.value9()?.let { PlayerSide.valueOf(it) }
            )
        }
    }
}
