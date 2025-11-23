package com.gchess.matchmaking.application.usecase

import com.gchess.matchmaking.infrastructure.adapter.driven.InMemoryMatchRepository
import com.gchess.matchmaking.domain.model.Match
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.UserId
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
            whiteUserId = UserId.generate(),
            blackUserId = UserId.generate(),
            gameId = GameId.generate(),
            matchedAt = now - 10.minutes,
            expiresAt = now - 5.minutes
        )

        repository.save(expiredMatch)

        // When
        useCase.execute()

        // Then - expired match should be removed
        repository.findByPlayer(expiredMatch.whiteUserId) shouldBe null
        repository.findByPlayer(expiredMatch.blackUserId) shouldBe null
    }

    test("execute should keep valid matches") {
        // Given
        val repository = InMemoryMatchRepository()
        val useCase = CleanupExpiredMatchesUseCase(repository)
        val now = Clock.System.now()

        // Add valid match
        val validMatch = Match(
            whiteUserId = UserId.generate(),
            blackUserId = UserId.generate(),
            gameId = GameId.generate(),
            matchedAt = now - 2.minutes,
            expiresAt = now + 3.minutes
        )

        repository.save(validMatch)

        // When
        useCase.execute()

        // Then - valid match should remain
        repository.findByPlayer(validMatch.whiteUserId) shouldBe validMatch
        repository.findByPlayer(validMatch.blackUserId) shouldBe validMatch
    }

    test("execute should handle mix of expired and valid matches") {
        // Given
        val repository = InMemoryMatchRepository()
        val useCase = CleanupExpiredMatchesUseCase(repository)
        val now = Clock.System.now()

        // Add 2 expired matches
        val expired1 = Match(
            whiteUserId = UserId.generate(),
            blackUserId = UserId.generate(),
            gameId = GameId.generate(),
            matchedAt = now - 10.minutes,
            expiresAt = now - 5.minutes
        )

        val expired2 = Match(
            whiteUserId = UserId.generate(),
            blackUserId = UserId.generate(),
            gameId = GameId.generate(),
            matchedAt = now - 8.minutes,
            expiresAt = now - 3.minutes
        )

        // Add 2 valid matches
        val valid1 = Match(
            whiteUserId = UserId.generate(),
            blackUserId = UserId.generate(),
            gameId = GameId.generate(),
            matchedAt = now - 1.minutes,
            expiresAt = now + 4.minutes
        )

        val valid2 = Match(
            whiteUserId = UserId.generate(),
            blackUserId = UserId.generate(),
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
        repository.findByPlayer(expired1.whiteUserId) shouldBe null
        repository.findByPlayer(expired2.whiteUserId) shouldBe null
        repository.findByPlayer(valid1.whiteUserId) shouldBe valid1
        repository.findByPlayer(valid2.whiteUserId) shouldBe valid2
    }

    test("execute should do nothing when repository is empty") {
        // Given
        val repository = InMemoryMatchRepository()
        val useCase = CleanupExpiredMatchesUseCase(repository)

        // When/Then - should not throw
        useCase.execute()
    }
})
