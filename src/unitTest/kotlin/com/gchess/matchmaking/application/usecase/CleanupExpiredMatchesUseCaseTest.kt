package com.gchess.matchmaking.application.usecase

import com.gchess.matchmaking.infrastructure.adapter.driven.InMemoryMatchRepository
import com.gchess.matchmaking.domain.model.Match
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

class CleanupExpiredMatchesUseCaseTest : FunSpec({

    test("execute should remove expired matches") {
        // Given
        val repository = InMemoryMatchRepository()
        val useCase = CleanupExpiredMatchesUseCase(repository)
        val now = Clock.System.now()

        // Add expired match
        val expiredMatch = Match(
            whitePlayerId = PlayerId.generate(),
            blackPlayerId = PlayerId.generate(),
            gameId = GameId.generate(),
            matchedAt = now - 10.minutes,
            expiresAt = now - 5.minutes
        )

        repository.save(expiredMatch)

        // When
        useCase.execute()

        // Then - expired match should be removed
        repository.findByPlayer(expiredMatch.whitePlayerId) shouldBe null
        repository.findByPlayer(expiredMatch.blackPlayerId) shouldBe null
    }

    test("execute should keep valid matches") {
        // Given
        val repository = InMemoryMatchRepository()
        val useCase = CleanupExpiredMatchesUseCase(repository)
        val now = Clock.System.now()

        // Add valid match
        val validMatch = Match(
            whitePlayerId = PlayerId.generate(),
            blackPlayerId = PlayerId.generate(),
            gameId = GameId.generate(),
            matchedAt = now - 2.minutes,
            expiresAt = now + 3.minutes
        )

        repository.save(validMatch)

        // When
        useCase.execute()

        // Then - valid match should remain
        repository.findByPlayer(validMatch.whitePlayerId) shouldBe validMatch
        repository.findByPlayer(validMatch.blackPlayerId) shouldBe validMatch
    }

    test("execute should handle mix of expired and valid matches") {
        // Given
        val repository = InMemoryMatchRepository()
        val useCase = CleanupExpiredMatchesUseCase(repository)
        val now = Clock.System.now()

        // Add 2 expired matches
        val expired1 = Match(
            whitePlayerId = PlayerId.generate(),
            blackPlayerId = PlayerId.generate(),
            gameId = GameId.generate(),
            matchedAt = now - 10.minutes,
            expiresAt = now - 5.minutes
        )

        val expired2 = Match(
            whitePlayerId = PlayerId.generate(),
            blackPlayerId = PlayerId.generate(),
            gameId = GameId.generate(),
            matchedAt = now - 8.minutes,
            expiresAt = now - 3.minutes
        )

        // Add 2 valid matches
        val valid1 = Match(
            whitePlayerId = PlayerId.generate(),
            blackPlayerId = PlayerId.generate(),
            gameId = GameId.generate(),
            matchedAt = now - 1.minutes,
            expiresAt = now + 4.minutes
        )

        val valid2 = Match(
            whitePlayerId = PlayerId.generate(),
            blackPlayerId = PlayerId.generate(),
            gameId = GameId.generate(),
            matchedAt = now,
            expiresAt = now + 5.minutes
        )

        repository.save(expired1)
        repository.save(expired2)
        repository.save(valid1)
        repository.save(valid2)

        // When
        useCase.execute()

        // Then - expired matches removed, valid matches kept
        repository.findByPlayer(expired1.whitePlayerId) shouldBe null
        repository.findByPlayer(expired2.whitePlayerId) shouldBe null
        repository.findByPlayer(valid1.whitePlayerId) shouldBe valid1
        repository.findByPlayer(valid2.whitePlayerId) shouldBe valid2
    }

    test("execute should do nothing when repository is empty") {
        // Given
        val repository = InMemoryMatchRepository()
        val useCase = CleanupExpiredMatchesUseCase(repository)

        // When/Then - should not throw
        useCase.execute()
    }
})
