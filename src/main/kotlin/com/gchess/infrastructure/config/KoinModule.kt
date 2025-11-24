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

import com.gchess.chess.application.usecase.CreateGameUseCase
import com.gchess.chess.application.usecase.GetGameUseCase
import com.gchess.chess.application.usecase.MakeMoveUseCase
import com.gchess.chess.domain.port.GameEventNotifier
import com.gchess.chess.domain.port.GameRepository
import com.gchess.chess.domain.service.ChessRules
import com.gchess.chess.domain.service.StandardChessRules
import com.gchess.chess.infrastructure.adapter.driven.PostgresGameRepository
import com.gchess.chess.infrastructure.adapter.driven.WebSocketGameEventNotifier
import com.gchess.user.application.usecase.GetUserUseCase
import com.gchess.user.application.usecase.LoginUseCase
import com.gchess.user.application.usecase.RegisterUserUseCase
import com.gchess.user.domain.port.PasswordHasher
import com.gchess.user.domain.port.UserRepository
import com.gchess.user.infrastructure.adapter.driven.BcryptPasswordHasher
import com.gchess.user.infrastructure.adapter.driven.PostgresUserRepository
import com.gchess.matchmaking.application.usecase.CleanupExpiredMatchesUseCase
import com.gchess.matchmaking.application.usecase.CreateGameFromMatchUseCase
import com.gchess.matchmaking.application.usecase.GetMatchStatusUseCase
import com.gchess.matchmaking.application.usecase.JoinMatchmakingUseCase
import com.gchess.matchmaking.application.usecase.LeaveMatchmakingUseCase
import com.gchess.matchmaking.domain.port.GameCreator
import com.gchess.matchmaking.domain.port.MatchRepository
import com.gchess.matchmaking.domain.port.MatchmakingNotifier
import com.gchess.matchmaking.domain.port.MatchmakingQueue
import com.gchess.matchmaking.domain.port.UserExistenceChecker
import com.gchess.matchmaking.infrastructure.adapter.driven.ChessContextGameCreator
import com.gchess.matchmaking.infrastructure.adapter.driven.InMemoryMatchmakingQueue
import com.gchess.matchmaking.infrastructure.adapter.driven.PostgresMatchRepository
import com.gchess.matchmaking.infrastructure.adapter.driven.UserContextUserChecker
import com.gchess.matchmaking.infrastructure.adapter.driven.WebSocketMatchmakingNotifier
import com.gchess.infrastructure.websocket.manager.GameConnectionManager
import com.gchess.infrastructure.websocket.manager.MatchmakingConnectionManager
import com.gchess.infrastructure.websocket.manager.SpectatorConnectionManager
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
    single<GameEventNotifier> { WebSocketGameEventNotifier(get(), get()) }

    // Use Cases (Application Layer)
    // Note: CreateGameUseCase is only called from Matchmaking context (via ACL)
    single { CreateGameUseCase(get()) }
    single { GetGameUseCase(get()) }
    single { MakeMoveUseCase(get(), get(), get()) }  // Added gameEventNotifier

    // ========== User Context ==========

    // Repositories (Output Adapters)
    single<UserRepository> { PostgresUserRepository(get()) }

    // Domain Services
    single<PasswordHasher> { BcryptPasswordHasher() }

    // Use Cases (Application Layer)
    single { RegisterUserUseCase(get(), get()) }
    single { LoginUseCase(get(), get()) }
    single { GetUserUseCase(get()) }

    // ========== Matchmaking Context ==========

    // Repositories (Output Adapters)
    // Note: MatchmakingQueue stays in-memory for performance (FIFO queue with race condition protection)
    single<MatchmakingQueue> { InMemoryMatchmakingQueue() }
    single<MatchRepository> { PostgresMatchRepository(get()) }

    // Anti-Corruption Layer (ACL)
    // Connects Matchmaking → Chess for game creation
    single<GameCreator> { ChessContextGameCreator(get()) }
    // Connects Matchmaking → User for user validation
    single<UserExistenceChecker> { UserContextUserChecker(get()) }
    // Matchmaking notifier for WebSocket real-time updates
    single<MatchmakingNotifier> { WebSocketMatchmakingNotifier(get()) }

    // Use Cases (Application Layer)
    single { CreateGameFromMatchUseCase(get(), get()) }  // gameCreator, userExistenceChecker
    single { CleanupExpiredMatchesUseCase(get()) }
    single { LeaveMatchmakingUseCase(get()) }
    single { GetMatchStatusUseCase(get(), get(), get()) }
    single { JoinMatchmakingUseCase(get(), get(), get(), get(), get()) }  // Added matchmakingNotifier

    // ========== Infrastructure: WebSocket Connection Managers ==========

    // Connection managers for WebSocket real-time communication
    single { MatchmakingConnectionManager() }  // Manages connections for users in matchmaking queue
    single { GameConnectionManager() }         // Manages connections for players in games (indexed by PlayerId)
    single { SpectatorConnectionManager() }    // Manages connections for spectators observing games
}

