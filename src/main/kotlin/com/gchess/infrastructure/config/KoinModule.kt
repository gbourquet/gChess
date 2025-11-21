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
import com.gchess.chess.domain.port.GameRepository
import com.gchess.chess.domain.port.PlayerExistenceChecker
import com.gchess.chess.domain.service.ChessRules
import com.gchess.chess.domain.service.StandardChessRules
import com.gchess.chess.infrastructure.adapter.driven.PostgresGameRepository
import com.gchess.chess.infrastructure.adapter.driven.UserContextPlayerChecker
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
import com.gchess.matchmaking.domain.port.MatchmakingQueue
import com.gchess.matchmaking.infrastructure.adapter.driven.ChessContextGameCreator
import com.gchess.matchmaking.infrastructure.adapter.driven.InMemoryMatchmakingQueue
import com.gchess.matchmaking.infrastructure.adapter.driven.PostgresMatchRepository
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

    // Anti-Corruption Layer (ACL)
    // Connects Chess context to User context without direct coupling
    single<PlayerExistenceChecker> { UserContextPlayerChecker(get()) }

    // Use Cases (Application Layer)
    single { CreateGameUseCase(get(), get()) }
    single { GetGameUseCase(get()) }
    single { MakeMoveUseCase(get(), get(), get()) }

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
    // Connects Matchmaking context to Chess context for game creation
    single<GameCreator> { ChessContextGameCreator(get()) }

    // Use Cases (Application Layer)
    single { CreateGameFromMatchUseCase(get()) }
    single { CleanupExpiredMatchesUseCase(get()) }
    single { LeaveMatchmakingUseCase(get()) }
    single { GetMatchStatusUseCase(get(), get(), get()) }
    single { JoinMatchmakingUseCase(get(), get(), get(), get()) }
}

