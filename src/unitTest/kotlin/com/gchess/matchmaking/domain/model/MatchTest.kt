package com.gchess.matchmaking.domain.model

import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

class MatchTest : FunSpec({
    val user1 = UserId.generate()
    val user2 = UserId.generate()
    val gameId = GameId.generate()

    test("isExpired should return false when match is within TTL") {
        // Given: A match created 2 minutes ago with 5 minute TTL
        val now = Instant.parse("2024-01-01T12:00:00Z")
        val matchedAt = now - 2.minutes
        val expiresAt = matchedAt + 5.minutes

        val match = Match(
            whiteUserId = user1,
            blackUserId = user2,
            gameId = gameId,
            matchedAt = matchedAt,
            expiresAt = expiresAt
        )

        // When: Checking if expired at current time
        val result = match.isExpired(now)

        // Then: Should not be expired (2 minutes < 5 minutes)
        result shouldBe false
    }

    test("isExpired should return true when match has exceeded TTL") {
        // Given: A match created 6 minutes ago with 5 minute TTL
        val now = Instant.parse("2024-01-01T12:00:00Z")
        val matchedAt = now - 6.minutes
        val expiresAt = matchedAt + 5.minutes

        val match = Match(
            whiteUserId = user1,
            blackUserId = user2,
            gameId = gameId,
            matchedAt = matchedAt,
            expiresAt = expiresAt
        )

        // When: Checking if expired at current time
        val result = match.isExpired(now)

        // Then: Should be expired (6 minutes > 5 minutes)
        result shouldBe true
    }

    test("isExpired should return false when exactly at expiration time") {
        // Given: A match that expires exactly now
        val now = Instant.parse("2024-01-01T12:00:00Z")
        val matchedAt = now - 5.minutes
        val expiresAt = now

        val match = Match(
            whiteUserId = user1,
            blackUserId = user2,
            gameId = gameId,
            matchedAt = matchedAt,
            expiresAt = expiresAt
        )

        // When: Checking if expired at expiration time
        val result = match.isExpired(now)

        // Then: Should not be expired (now == expiresAt, not >)
        result shouldBe false
    }

    test("isExpired should return true one millisecond after expiration") {
        // Given: A match that expired 1ms ago
        val expiresAt = Instant.parse("2024-01-01T12:00:00Z")
        val now = Instant.parse("2024-01-01T12:00:00.001Z")
        val matchedAt = expiresAt - 5.minutes

        val match = Match(
            whiteUserId = user1,
            blackUserId = user2,
            gameId = gameId,
            matchedAt = matchedAt,
            expiresAt = expiresAt
        )

        // When: Checking if expired 1ms after expiration
        val result = match.isExpired(now)

        // Then: Should be expired
        result shouldBe true
    }

    test("create should generate match with correct TTL") {
        // Given: Current time and TTL of 5 minutes
        val now = Instant.parse("2024-01-01T12:00:00Z")
        val ttlMinutes = 5

        // When: Creating a match with factory method
        val match = Match.create(
            whiteUserId = user1,
            blackUserId = user2,
            gameId = gameId,
            ttlMinutes = ttlMinutes,
            now = now
        )

        // Then: Match should have correct timestamps
        match.matchedAt shouldBe now
        match.expiresAt shouldBe now + ttlMinutes.minutes
    }

    test("create should use default TTL when not specified") {
        // Given: Current time
        val now = Instant.parse("2024-01-01T12:00:00Z")

        // When: Creating a match without specifying TTL
        val match = Match.create(
            whiteUserId = user1,
            blackUserId = user2,
            gameId = gameId,
            now = now
        )

        // Then: Should use DEFAULT_TTL_MINUTES (5 minutes)
        match.expiresAt shouldBe now + Match.DEFAULT_TTL_MINUTES.minutes
    }
})
