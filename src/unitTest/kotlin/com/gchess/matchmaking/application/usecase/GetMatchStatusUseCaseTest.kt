package com.gchess.matchmaking.application.usecase

import com.gchess.matchmaking.domain.model.Match
import com.gchess.matchmaking.infrastructure.adapter.driven.InMemoryMatchRepository
import com.gchess.matchmaking.infrastructure.adapter.driven.InMemoryMatchmakingQueue
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

class GetMatchStatusUseCaseTest : FunSpec({

    test("execute should return NotFound when player is not in queue or matched") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val cleanupUseCase = CleanupExpiredMatchesUseCase(repository)
        val useCase = GetMatchStatusUseCase(queue, repository, cleanupUseCase)
        val playerId = PlayerId.generate()

        // When
        val result = useCase.execute(playerId)

        // Then
        result.shouldBeInstanceOf<MatchmakingResult.NotFound>()
    }

    test("execute should return Waiting when player is in queue") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val cleanupUseCase = CleanupExpiredMatchesUseCase(repository)
        val useCase = GetMatchStatusUseCase(queue, repository, cleanupUseCase)
        val playerId = PlayerId.generate()

        queue.addPlayer(playerId)

        // When
        val result = useCase.execute(playerId)

        // Then
        result.shouldBeInstanceOf<MatchmakingResult.Waiting>()
        result.queuePosition shouldBe 1
    }

    test("execute should return queue position based on queue size") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val cleanupUseCase = CleanupExpiredMatchesUseCase(repository)
        val useCase = GetMatchStatusUseCase(queue, repository, cleanupUseCase)

        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()
        val player3 = PlayerId.generate()

        queue.addPlayer(player1)
        delay(10)
        queue.addPlayer(player2)
        delay(10)
        queue.addPlayer(player3)

        // When
        val result1 = useCase.execute(player1)
        val result2 = useCase.execute(player2)
        val result3 = useCase.execute(player3)

        // Then - all should be Waiting with queue size (MVP implementation)
        (result1 as MatchmakingResult.Waiting).queuePosition shouldBe 3
        (result2 as MatchmakingResult.Waiting).queuePosition shouldBe 3
        (result3 as MatchmakingResult.Waiting).queuePosition shouldBe 3
    }

    test("execute should return Matched when player has a match as white") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val cleanupUseCase = CleanupExpiredMatchesUseCase(repository)
        val useCase = GetMatchStatusUseCase(queue, repository, cleanupUseCase)

        val whitePlayer = PlayerId.generate()
        val blackPlayer = PlayerId.generate()
        val gameId = GameId.generate()
        val now = Clock.System.now()

        val match = Match(
            whitePlayerId = whitePlayer,
            blackPlayerId = blackPlayer,
            gameId = gameId,
            matchedAt = now,
            expiresAt = now + 5.minutes
        )

        repository.save(match)

        // When
        val result = useCase.execute(whitePlayer)

        // Then
        result.shouldBeInstanceOf<MatchmakingResult.Matched>()
        result.gameId shouldBe gameId
        result.yourColor shouldBe MatchmakingResult.PlayerSide.WHITE
    }

    test("execute should return Matched when player has a match as black") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val cleanupUseCase = CleanupExpiredMatchesUseCase(repository)
        val useCase = GetMatchStatusUseCase(queue, repository, cleanupUseCase)

        val whitePlayer = PlayerId.generate()
        val blackPlayer = PlayerId.generate()
        val gameId = GameId.generate()
        val now = Clock.System.now()

        val match = Match(
            whitePlayerId = whitePlayer,
            blackPlayerId = blackPlayer,
            gameId = gameId,
            matchedAt = now,
            expiresAt = now + 5.minutes
        )

        repository.save(match)

        // When
        val result = useCase.execute(blackPlayer)

        // Then
        result.shouldBeInstanceOf<MatchmakingResult.Matched>()
        result.gameId shouldBe gameId
        result.yourColor shouldBe MatchmakingResult.PlayerSide.BLACK
    }

    test("execute should cleanup expired matches before checking") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val cleanupUseCase = CleanupExpiredMatchesUseCase(repository)
        val useCase = GetMatchStatusUseCase(queue, repository, cleanupUseCase)

        val playerId = PlayerId.generate()
        val otherPlayer = PlayerId.generate()
        val now = Clock.System.now()

        // Add expired match
        val expiredMatch = Match(
            whitePlayerId = playerId,
            blackPlayerId = otherPlayer,
            gameId = GameId.generate(),
            matchedAt = now - 10.minutes,
            expiresAt = now - 5.minutes
        )

        repository.save(expiredMatch)

        // When
        val result = useCase.execute(playerId)

        // Then - expired match should be cleaned up and player not found
        result.shouldBeInstanceOf<MatchmakingResult.NotFound>()
    }

    test("execute should prioritize match over queue status") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val cleanupUseCase = CleanupExpiredMatchesUseCase(repository)
        val useCase = GetMatchStatusUseCase(queue, repository, cleanupUseCase)

        val playerId = PlayerId.generate()
        val otherPlayer = PlayerId.generate()
        val gameId = GameId.generate()
        val now = Clock.System.now()

        // Player in both queue and matched (edge case during race condition)
        queue.addPlayer(playerId)

        val match = Match(
            whitePlayerId = playerId,
            blackPlayerId = otherPlayer,
            gameId = gameId,
            matchedAt = now,
            expiresAt = now + 5.minutes
        )
        repository.save(match)

        // When
        val result = useCase.execute(playerId)

        // Then - should return Matched (takes priority)
        result.shouldBeInstanceOf<MatchmakingResult.Matched>()
    }
})
