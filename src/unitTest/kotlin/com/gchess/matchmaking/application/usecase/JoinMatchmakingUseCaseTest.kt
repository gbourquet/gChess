package com.gchess.matchmaking.application.usecase

import com.gchess.matchmaking.domain.model.Match
import com.gchess.matchmaking.infrastructure.adapter.driven.InMemoryMatchRepository
import com.gchess.matchmaking.infrastructure.adapter.driven.InMemoryMatchmakingQueue
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

class JoinMatchmakingUseCaseTest : FunSpec({

    test("execute should add player to queue and return WAITING when no match found") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val playerChecker = FakePlayerExistenceChecker(exists = true)
        val gameCreator = FakeGameCreator()
        val createGameUseCase = CreateGameFromMatchUseCase(gameCreator)

        val useCase = JoinMatchmakingUseCase(queue, repository, playerChecker, createGameUseCase)
        val playerId = PlayerId.generate()

        // When
        val result = useCase.execute(playerId)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull().shouldBeInstanceOf<MatchmakingResult.Waiting>()
        queue.isPlayerInQueue(playerId) shouldBe true
    }

    test("execute should fail when player does not exist") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val playerChecker = FakePlayerExistenceChecker(exists = false)
        val gameCreator = FakeGameCreator()
        val createGameUseCase = CreateGameFromMatchUseCase(gameCreator)

        val useCase = JoinMatchmakingUseCase(queue, repository, playerChecker, createGameUseCase)
        val playerId = PlayerId.generate()

        // When
        val result = useCase.execute(playerId)

        // Then
        result.isFailure shouldBe true
        queue.isPlayerInQueue(playerId) shouldBe false
    }

    test("execute should fail when player is already in queue") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val playerChecker = FakePlayerExistenceChecker(exists = true)
        val gameCreator = FakeGameCreator()
        val createGameUseCase = CreateGameFromMatchUseCase(gameCreator)

        val useCase = JoinMatchmakingUseCase(queue, repository, playerChecker, createGameUseCase)
        val playerId = PlayerId.generate()

        queue.addPlayer(playerId)

        // When
        val result = useCase.execute(playerId)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "Player is already in the matchmaking queue"
    }

    test("execute should fail when player already has a match") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val playerChecker = FakePlayerExistenceChecker(exists = true)
        val gameCreator = FakeGameCreator()
        val createGameUseCase = CreateGameFromMatchUseCase(gameCreator)

        val useCase = JoinMatchmakingUseCase(queue, repository, playerChecker, createGameUseCase)
        val playerId = PlayerId.generate()
        val otherPlayer = PlayerId.generate()
        val now = Clock.System.now()

        val existingMatch = Match(
            whitePlayerId = playerId,
            blackPlayerId = otherPlayer,
            gameId = GameId.generate(),
            matchedAt = now,
            expiresAt = now + 5.minutes
        )
        repository.save(existingMatch)

        // When
        val result = useCase.execute(playerId)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "Player already has an active match"
    }

    test("execute should create match and game when two players join") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val playerChecker = FakePlayerExistenceChecker(exists = true)
        val gameId = GameId.generate()
        val gameCreator = FakeGameCreator(gameId)
        val createGameUseCase = CreateGameFromMatchUseCase(gameCreator)

        val useCase = JoinMatchmakingUseCase(queue, repository, playerChecker, createGameUseCase)

        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()

        // When - player 1 joins
        val result1 = useCase.execute(player1)

        // Then - player 1 is waiting
        result1.getOrNull().shouldBeInstanceOf<MatchmakingResult.Waiting>()

        // When - player 2 joins
        val result2 = useCase.execute(player2)

        // Then - player 2 is matched
        result2.getOrNull().shouldBeInstanceOf<MatchmakingResult.Matched>()
        val matched = result2.getOrNull() as MatchmakingResult.Matched
        matched.gameId shouldBe gameId

        // Both players should be removed from queue
        queue.isPlayerInQueue(player1) shouldBe false
        queue.isPlayerInQueue(player2) shouldBe false

        // Both players should have the match in repository
        val match1 = repository.findByPlayer(player1)
        val match2 = repository.findByPlayer(player2)
        match1 shouldNotBe null
        match2 shouldNotBe null
        match1 shouldBe match2
    }

    test("execute should assign colors randomly when creating match") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val playerChecker = FakePlayerExistenceChecker(exists = true)
        val gameId = GameId.generate()
        val gameCreator = FakeGameCreator(gameId)
        val createGameUseCase = CreateGameFromMatchUseCase(gameCreator)

        val useCase = JoinMatchmakingUseCase(queue, repository, playerChecker, createGameUseCase)

        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()

        // When
        useCase.execute(player1)
        useCase.execute(player2)

        // Then
        val match = repository.findByPlayer(player1)!!
        val whitePlayer = match.whitePlayerId
        val blackPlayer = match.blackPlayerId

        // Colors should be assigned to both players
        (whitePlayer == player1 && blackPlayer == player2) ||
            (whitePlayer == player2 && blackPlayer == player1) shouldBe true
    }

    test("execute should propagate game creation failure") {
        // Given
        val queue = InMemoryMatchmakingQueue()
        val repository = InMemoryMatchRepository()
        val playerChecker = FakePlayerExistenceChecker(exists = true)
        val errorMessage = "Failed to create game in Chess context"
        val gameCreator = FakeGameCreator(failure = Exception(errorMessage))
        val createGameUseCase = CreateGameFromMatchUseCase(gameCreator)

        val useCase = JoinMatchmakingUseCase(queue, repository, playerChecker, createGameUseCase)

        val player1 = PlayerId.generate()
        val player2 = PlayerId.generate()

        // When
        useCase.execute(player1)
        val result2 = useCase.execute(player2)

        // Then - should fail with error from game creation
        result2.isFailure shouldBe true
        result2.exceptionOrNull()!!.message shouldBe errorMessage
    }
})

