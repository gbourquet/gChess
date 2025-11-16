package com.gchess.infrastructure.config

import com.gchess.application.usecase.CreateGameUseCase
import com.gchess.application.usecase.GetGameUseCase
import com.gchess.application.usecase.MakeMoveUseCase
import com.gchess.domain.port.GameRepository
import com.gchess.domain.port.MoveValidator
import com.gchess.infrastructure.adapter.output.BasicMoveValidator
import com.gchess.infrastructure.adapter.output.InMemoryGameRepository
import org.koin.dsl.module

val appModule = module {
    // Repositories (Output Adapters)
    single<GameRepository> { InMemoryGameRepository() }
    single<MoveValidator> { BasicMoveValidator() }

    // Use Cases (Application Layer)
    single { CreateGameUseCase(get()) }
    single { GetGameUseCase(get()) }
    single { MakeMoveUseCase(get(), get()) }
}
