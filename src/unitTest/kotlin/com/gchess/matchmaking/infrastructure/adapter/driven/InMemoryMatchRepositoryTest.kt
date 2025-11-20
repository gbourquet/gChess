package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.matchmaking.domain.model.Match
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

class InMemoryMatchRepositoryTest : FunSpec({

    test("save should store match for both players") {
        // Given
        val repository = InMemoryMatchRepository()
        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()
        val gameId = GameId.generate()
        val now = Instant.parse("2024-01-01T12:00:00Z")

        val match = Match(
            whitePlayerId = player1,
            blackPlayerId = player2,
            gameId = gameId,
            matchedAt = now,
            expiresAt = now + 5.minutes
        )

        // When
        repository.save(match)

        // Then - both players can retrieve the match
        val matchForPlayer1 = repository.findByPlayer(player1)
        val matchForPlayer2 = repository.findByPlayer(player2)

        matchForPlayer1 shouldBe match
        matchForPlayer2 shouldBe match
    }

    test("findByPlayer should return null when no match exists") {
        // Given
        val repository = InMemoryMatchRepository()
        val playerId = PlayerId.generate()

        // When
        val match = repository.findByPlayer(playerId)

        // Then
        match shouldBe null
    }

    test("findByPlayer should return match for white player") {
        // Given
        val repository = InMemoryMatchRepository()
        val whitePlayer = PlayerId.generate()
        val blackPlayer = PlayerId.generate()
        val gameId = GameId.generate()
        val now = Instant.parse("2024-01-01T12:00:00Z")

        val match = Match(
            whitePlayerId = whitePlayer,
            blackPlayerId = blackPlayer,
            gameId = gameId,
            matchedAt = now,
            expiresAt = now + 5.minutes
        )

        // When
        repository.save(match)
        val foundMatch = repository.findByPlayer(whitePlayer)

        // Then
        foundMatch shouldNotBe null
        foundMatch!!.whitePlayerId shouldBe whitePlayer
        foundMatch.blackPlayerId shouldBe blackPlayer
        foundMatch.gameId shouldBe gameId
    }

    test("findByPlayer should return match for black player") {
        // Given
        val repository = InMemoryMatchRepository()
        val whitePlayer = PlayerId.generate()
        val blackPlayer = PlayerId.generate()
        val gameId = GameId.generate()
        val now = Instant.parse("2024-01-01T12:00:00Z")

        val match = Match(
            whitePlayerId = whitePlayer,
            blackPlayerId = blackPlayer,
            gameId = gameId,
            matchedAt = now,
            expiresAt = now + 5.minutes
        )

        // When
        repository.save(match)
        val foundMatch = repository.findByPlayer(blackPlayer)

        // Then
        foundMatch shouldNotBe null
        foundMatch!!.whitePlayerId shouldBe whitePlayer
        foundMatch.blackPlayerId shouldBe blackPlayer
    }

    test("delete should remove match for both players") {
        // Given
        val repository = InMemoryMatchRepository()
        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()
        val gameId = GameId.generate()
        val now = Instant.parse("2024-01-01T12:00:00Z")

        val match = Match(
            whitePlayerId = player1,
            blackPlayerId = player2,
            gameId = gameId,
            matchedAt = now,
            expiresAt = now + 5.minutes
        )

        repository.save(match)

        // When - delete using player1's ID
        repository.delete(player1)

        // Then - neither player can find the match
        repository.findByPlayer(player1) shouldBe null
        repository.findByPlayer(player2) shouldBe null
    }

    test("delete should work when deleting by black player ID") {
        // Given
        val repository = InMemoryMatchRepository()
        val whitePlayer = PlayerId.generate()
        val blackPlayer = PlayerId.generate()
        val gameId = GameId.generate()
        val now = Instant.parse("2024-01-01T12:00:00Z")

        val match = Match(
            whitePlayerId = whitePlayer,
            blackPlayerId = blackPlayer,
            gameId = gameId,
            matchedAt = now,
            expiresAt = now + 5.minutes
        )

        repository.save(match)

        // When - delete using blackPlayer's ID
        repository.delete(blackPlayer)

        // Then - neither player can find the match
        repository.findByPlayer(whitePlayer) shouldBe null
        repository.findByPlayer(blackPlayer) shouldBe null
    }

    test("delete should do nothing when match does not exist") {
        // Given
        val repository = InMemoryMatchRepository()
        val playerId = PlayerId.generate()

        // When/Then - should not throw
        repository.delete(playerId)
    }

    test("deleteExpiredMatches should remove only expired matches") {
        // Given
        val repository = InMemoryMatchRepository()
        val now = kotlinx.datetime.Clock.System.now()

        // Match 1: Expired (created 10 minutes ago, TTL 5 minutes)
        val expiredMatch = Match(
            whitePlayerId = PlayerId.generate(),
            blackPlayerId = PlayerId.generate(),
            gameId = GameId.generate(),
            matchedAt = now - 10.minutes,
            expiresAt = now - 5.minutes
        )

        // Match 2: Valid (created 2 minutes ago, TTL 5 minutes)
        val validMatch = Match(
            whitePlayerId = PlayerId.generate(),
            blackPlayerId = PlayerId.generate(),
            gameId = GameId.generate(),
            matchedAt = now - 2.minutes,
            expiresAt = now + 3.minutes
        )

        repository.save(expiredMatch)
        repository.save(validMatch)

        // When - delete expired matches
        repository.deleteExpiredMatches()

        // Then - expired match is removed, valid match remains
        repository.findByPlayer(expiredMatch.whitePlayerId) shouldBe null
        repository.findByPlayer(expiredMatch.blackPlayerId) shouldBe null

        repository.findByPlayer(validMatch.whitePlayerId) shouldNotBe null
        repository.findByPlayer(validMatch.blackPlayerId) shouldNotBe null
    }

    test("deleteExpiredMatches should handle multiple expired matches") {
        // Given
        val repository = InMemoryMatchRepository()
        val now = kotlinx.datetime.Clock.System.now()

        // Create 3 expired matches
        val expiredMatches = (1..3).map {
            Match(
                whitePlayerId = PlayerId.generate(),
                blackPlayerId = PlayerId.generate(),
                gameId = GameId.generate(),
                matchedAt = now - 10.minutes,
                expiresAt = now - 5.minutes
            )
        }

        // Create 2 valid matches
        val validMatches = (1..2).map {
            Match(
                whitePlayerId = PlayerId.generate(),
                blackPlayerId = PlayerId.generate(),
                gameId = GameId.generate(),
                matchedAt = now - 2.minutes,
                expiresAt = now + 3.minutes
            )
        }

        (expiredMatches + validMatches).forEach { repository.save(it) }

        // When
        repository.deleteExpiredMatches()

        // Then - all expired matches removed, valid matches remain
        expiredMatches.forEach { match ->
            repository.findByPlayer(match.whitePlayerId) shouldBe null
            repository.findByPlayer(match.blackPlayerId) shouldBe null
        }

        validMatches.forEach { match ->
            repository.findByPlayer(match.whitePlayerId) shouldNotBe null
            repository.findByPlayer(match.blackPlayerId) shouldNotBe null
        }
    }

    test("deleteExpiredMatches should do nothing when no matches exist") {
        // Given
        val repository = InMemoryMatchRepository()

        // When/Then - should not throw
        repository.deleteExpiredMatches()
    }

    test("save should overwrite existing match for players") {
        // Given
        val repository = InMemoryMatchRepository()
        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()
        val now = Instant.parse("2024-01-01T12:00:00Z")

        val match1 = Match(
            whitePlayerId = player1,
            blackPlayerId = player2,
            gameId = GameId.generate(),
            matchedAt = now,
            expiresAt = now + 5.minutes
        )

        val match2 = Match(
            whitePlayerId = player1,
            blackPlayerId = player2,
            gameId = GameId.generate(),
            matchedAt = now + 1.minutes,
            expiresAt = now + 6.minutes
        )

        // When
        repository.save(match1)
        repository.save(match2)

        // Then - should return the second match
        val foundMatch = repository.findByPlayer(player1)
        foundMatch shouldBe match2
    }
})
