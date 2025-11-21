package com.gchess.chess.infrastructure.adapter.driven

import com.gchess.chess.domain.model.*
import com.gchess.chess.domain.port.GameRepository
import com.gchess.infrastructure.persistence.jooq.tables.references.GAMES
import com.gchess.infrastructure.persistence.jooq.tables.references.GAME_MOVES
import com.gchess.shared.domain.model.GameId
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
            ctx.insertInto(GAMES)
                .set(GAMES.ID, game.id.value)
                .set(GAMES.WHITE_PLAYER_ID, game.whitePlayer.value)
                .set(GAMES.BLACK_PLAYER_ID, game.blackPlayer.value)
                .set(GAMES.BOARD_FEN, game.board.toFen())
                .set(GAMES.CURRENT_SIDE, game.currentSide.name)
                .set(GAMES.STATUS, game.status.name)
                .set(GAMES.CREATED_AT, now)
                .set(GAMES.UPDATED_AT, now)
                .onConflict(GAMES.ID)
                .doUpdate()
                .set(GAMES.BOARD_FEN, game.board.toFen())
                .set(GAMES.CURRENT_SIDE, game.currentSide.name)
                .set(GAMES.STATUS, game.status.name)
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
                GAMES.BLACK_PLAYER_ID,
                GAMES.BOARD_FEN,
                GAMES.CURRENT_SIDE,
                GAMES.STATUS
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
        Game(
            id = GameId(gameRecord.value1()!!),
            whitePlayer = com.gchess.shared.domain.model.PlayerId(gameRecord.value2()!!),
            blackPlayer = com.gchess.shared.domain.model.PlayerId(gameRecord.value3()!!),
            board = gameRecord.value4()!!.toChessPosition(),
            currentSide = PlayerSide.valueOf(gameRecord.value5()!!),
            status = GameStatus.valueOf(gameRecord.value6()!!),
            moveHistory = moves
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
                GAMES.BLACK_PLAYER_ID,
                GAMES.BOARD_FEN,
                GAMES.CURRENT_SIDE,
                GAMES.STATUS
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

            Game(
                id = GameId(gameId),
                whitePlayer = com.gchess.shared.domain.model.PlayerId(gameRecord.value2()!!),
                blackPlayer = com.gchess.shared.domain.model.PlayerId(gameRecord.value3()!!),
                board = gameRecord.value4()!!.toChessPosition(),
                currentSide = PlayerSide.valueOf(gameRecord.value5()!!),
                status = GameStatus.valueOf(gameRecord.value6()!!),
                moveHistory = moves
            )
        }
    }
}
