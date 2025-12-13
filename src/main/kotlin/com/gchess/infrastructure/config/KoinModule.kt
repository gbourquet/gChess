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
package com.gchess.infrastructure.config

import com.gchess.bot.domain.service.BotService
import com.gchess.bot.domain.service.MinimaxBotService
import com.gchess.chess.application.usecase.CreateGameUseCase
import com.gchess.chess.application.usecase.GetGameUseCase
import com.gchess.chess.application.usecase.MakeMoveUseCase
import com.gchess.chess.application.usecase.ResignGameUseCase
import com.gchess.chess.application.usecase.OfferDrawUseCase
import com.gchess.chess.application.usecase.AcceptDrawUseCase
import com.gchess.chess.application.usecase.RejectDrawUseCase
import com.gchess.chess.domain.port.GameEventNotifier
import com.gchess.chess.domain.port.GameRepository
import com.gchess.chess.domain.service.ChessRules
import com.gchess.chess.domain.service.StandardChessRules
import com.gchess.chess.infrastructure.adapter.driven.PostgresGameRepository
import com.gchess.chess.infrastructure.adapter.driven.WebSocketGameEventNotifier
import com.gchess.user.application.usecase.LoginUseCase
import com.gchess.user.application.usecase.RegisterUserUseCase
import com.gchess.user.domain.port.PasswordHasher
import com.gchess.user.domain.port.UserRepository
import com.gchess.user.infrastructure.adapter.driven.BcryptPasswordHasher
import com.gchess.user.infrastructure.adapter.driven.PostgresUserRepository
import com.gchess.matchmaking.application.usecase.CreateGameFromMatchUseCase
import com.gchess.matchmaking.application.usecase.JoinMatchmakingUseCase
import com.gchess.matchmaking.application.usecase.LeaveMatchmakingUseCase
import com.gchess.matchmaking.domain.port.GameCreator
import com.gchess.matchmaking.domain.port.MatchmakingNotifier
import com.gchess.matchmaking.domain.port.MatchmakingQueue
import com.gchess.matchmaking.domain.port.UserExistenceChecker
import com.gchess.matchmaking.infrastructure.adapter.driven.ChessContextGameCreator
import com.gchess.matchmaking.infrastructure.adapter.driven.InMemoryMatchmakingQueue
import com.gchess.matchmaking.infrastructure.adapter.driven.UserContextUserChecker
import com.gchess.matchmaking.infrastructure.adapter.driven.WebSocketMatchmakingNotifier
import com.gchess.chess.infrastructure.adapter.driver.GameConnectionManager
import com.gchess.matchmaking.infrastructure.adapter.driver.MatchmakingConnectionManager
import com.gchess.chess.infrastructure.adapter.driver.SpectatorConnectionManager
import com.gchess.user.application.usecase.GetUserUseCase
import org.jooq.DSLContext
import org.koin.dsl.module
import javax.sql.DataSource

val appModule = module {
    // ========== Infrastructure: Database ==========

    // DataSource (HikariCP connection pool)
    single<DataSource> { DatabaseConfig.createDataSource() }

    // DSLContext (jOOQ) - migrations are run before creating the context
    single<DSLContext> {
        val dataSource = get<DataSource>()
        // Run migrations on first access (ensures schema is up to date)
        DatabaseConfig.runMigrations(dataSource)
        DatabaseConfig.createDslContext(dataSource)
    }

    // ========== Chess Context ==========

    // Repositories (Output Adapters)
    single<GameRepository> { PostgresGameRepository(get()) }

    // Domain Services
    single<ChessRules> { StandardChessRules() }

    // Game event notifier for WebSocket real-time updates
    single<GameEventNotifier> { WebSocketGameEventNotifier(get(), get(), get()) }

    // Use Cases (Application Layer)
    // Note: CreateGameUseCase is only called from Matchmaking context (via ACL)
    single { CreateGameUseCase(get()) }
    single { GetGameUseCase(get()) }
    single { MakeMoveUseCase(get(), get(), get()) }  // gameRepository, chessRules, gameEventNotifier
    single { ResignGameUseCase(get(), get()) }  // gameRepository, gameEventNotifier
    single { OfferDrawUseCase(get(), get()) }  // gameRepository, gameEventNotifier
    single { AcceptDrawUseCase(get(), get()) }  // gameRepository, gameEventNotifier
    single { RejectDrawUseCase(get(), get()) }  // gameRepository, gameEventNotifier

    // ========== User Context ==========

    // Repositories (Output Adapters)
    single<UserRepository> { PostgresUserRepository(get()) }

    // Domain Services
    single<PasswordHasher> { BcryptPasswordHasher() }

    // Use Cases (Application Layer)
    single { RegisterUserUseCase(get(), get()) }
    single { LoginUseCase(get(), get()) }
    single { GetUserUseCase(get()) }

    // ========== Bot Context ==========

    // Repositories (Output Adapters)
    single<com.gchess.bot.domain.port.BotRepository> {
        com.gchess.bot.infrastructure.adapter.driven.PostgresBotRepository(get())
    }

    // Domain Services / Engines
    single<BotService> {
        MinimaxBotService()
    }

    // Anti-Corruption Layer (ACL)
    // Connects Matchmaking → Bot for bot selection
    single<com.gchess.matchmaking.domain.port.BotSelector> {
        com.gchess.bot.infrastructure.adapter.driven.MatchmakingContextBotSelector(get())
    }
    // Connects Bot → Chess for move execution
    single<com.gchess.bot.domain.port.MoveExecutor> {
        com.gchess.bot.infrastructure.adapter.driven.ChessContextMoveExecutor(get())
    }

    // Use Cases (Application Layer)
    single { com.gchess.bot.application.usecase.ExecuteBotMoveUseCase(get(), get(), get(), get()) }  // botEngine, gameRepository, moveExecutor, botRepository
    single { com.gchess.bot.application.usecase.GetBotUseCase(get()) }
    single { com.gchess.bot.application.usecase.ListBotsUseCase(get()) }

    // ========== Matchmaking Context ==========

    // Repositories (Output Adapters)
    // Note: MatchmakingQueue stays in-memory for performance (FIFO queue with race condition protection)
    single<MatchmakingQueue> { InMemoryMatchmakingQueue() }

    // Anti-Corruption Layer (ACL)
    // Connects Matchmaking → Chess for game creation
    single<GameCreator> { ChessContextGameCreator(get()) }
    // Connects Matchmaking → User for user validation
    single<UserExistenceChecker> { UserContextUserChecker(get()) }
    // Matchmaking notifier for WebSocket real-time updates
    single<MatchmakingNotifier> { WebSocketMatchmakingNotifier(get()) }

    // Use Cases (Application Layer)
    single { CreateGameFromMatchUseCase(get(), get()) }  // gameCreator, userExistenceChecker
    single { LeaveMatchmakingUseCase(get()) }
    single { JoinMatchmakingUseCase(get(), get(), get(), get(), get()) }  // queue, gameCreator, userChecker, botSelector, notifier

    // ========== Infrastructure: Health Checks ==========

    // Health check service for monitoring application health
    single { com.gchess.infrastructure.health.HealthCheckService(get(), get()) }  // dslContext, matchmakingQueue

    // ========== Infrastructure: WebSocket Connection Managers ==========

    // Connection managers for WebSocket real-time communication
    single { MatchmakingConnectionManager() }  // Manages connections for users in matchmaking queue
    single { GameConnectionManager() }         // Manages connections for players in games (indexed by PlayerId)
    single { SpectatorConnectionManager() }    // Manages connections for spectators observing games
}

