package com.gchess.infrastructure.config

import com.gchess.chess.application.usecase.CreateGameUseCase
import com.gchess.chess.application.usecase.GetGameUseCase
import com.gchess.chess.application.usecase.MakeMoveUseCase
import com.gchess.chess.domain.port.GameRepository
import com.gchess.chess.domain.port.PlayerExistenceChecker
import com.gchess.chess.domain.service.ChessRules
import com.gchess.chess.domain.service.StandardChessRules
import com.gchess.chess.infrastructure.adapter.driven.InMemoryGameRepository
import com.gchess.chess.infrastructure.adapter.driven.UserContextPlayerChecker
import com.gchess.user.application.usecase.GetUserUseCase
import com.gchess.user.application.usecase.LoginUseCase
import com.gchess.user.application.usecase.RegisterUserUseCase
import com.gchess.user.domain.port.PasswordHasher
import com.gchess.user.domain.port.UserRepository
import com.gchess.user.infrastructure.adapter.driven.BcryptPasswordHasher
import com.gchess.user.infrastructure.adapter.driven.InMemoryUserRepository
import org.koin.dsl.module

val appModule = module {
    // ========== Chess Context ==========

    // Repositories (Output Adapters)
    single<GameRepository> { InMemoryGameRepository() }

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
    single<UserRepository> { InMemoryUserRepository() }

    // Domain Services
    single<PasswordHasher> { BcryptPasswordHasher() }

    // Use Cases (Application Layer)
    single { RegisterUserUseCase(get(), get()) }
    single { LoginUseCase(get(), get()) }
    single { GetUserUseCase(get()) }
}

