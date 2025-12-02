package com.gchess.matchmaking.application.usecase

import com.gchess.shared.domain.model.PlayerSide
import com.gchess.matchmaking.domain.model.Match
import com.gchess.matchmaking.domain.model.QueueEntry
import com.gchess.matchmaking.domain.port.BotSelector
import com.gchess.matchmaking.domain.port.GameCreator
import com.gchess.matchmaking.domain.port.MatchmakingNotifier
import com.gchess.matchmaking.domain.port.MatchmakingQueue
import com.gchess.matchmaking.domain.port.UserExistenceChecker
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.PlayerId
import com.gchess.shared.domain.model.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class JoinMatchmakingUseCaseTest : FunSpec({

    test("execute should add user to queue and return WAITING when no match found") {
        // Given
        val queue = mockk<MatchmakingQueue>()
        val gameCreator = mockk<GameCreator>()
        val userChecker = mockk<UserExistenceChecker>()
        val botSelector = mockk<BotSelector>()
        val notifier = mockk<MatchmakingNotifier>()

        val useCase = JoinMatchmakingUseCase(queue, gameCreator, userChecker, botSelector, notifier)
        val userId = UserId.generate()
        val queueEntry = QueueEntry(userId, Clock.System.now())

        // Mock behavior
        coEvery { userChecker.exists(userId) } returns true
        coEvery { queue.isPlayerInQueue(userId) } returns false
        coEvery { queue.addPlayer(userId) } returns queueEntry
        coEvery { queue.getQueueSize() } returns 10
        coEvery { queue.findMatch() } returns null
        coEvery { notifier.notifyQueuePosition(userId, 10) } just Runs

        // When
        val result = useCase.execute(userId)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull().shouldBeInstanceOf<MatchmakingResult.Waiting>()

        // Verify interactions
        coVerify { userChecker.exists(userId) }
        coVerify { queue.isPlayerInQueue(userId) }
        coVerify { queue.addPlayer(userId) }
        coVerify { queue.findMatch() }
        coVerify { queue.getQueueSize() }
        coVerify { notifier.notifyQueuePosition(userId, 10) }
        confirmVerified(userChecker, queue, notifier)
    }

    test("execute should fail when user does not exist") {
        // Given
        val queue = mockk<MatchmakingQueue>()
        val gameCreator = mockk<GameCreator>()
        val userChecker = mockk<UserExistenceChecker>()
        val botSelector = mockk<BotSelector>()
        val notifier = mockk<MatchmakingNotifier>()

        val useCase = JoinMatchmakingUseCase(queue, gameCreator, userChecker, botSelector, notifier)
        val userId = UserId.generate()

        // Mock behavior
        coEvery { userChecker.exists(userId) } returns false

        // When
        val result = useCase.execute(userId)

        // Then
        result.isFailure shouldBe true

        // Verify - only userChecker should be called, notifier should NOT be called
        coVerify { userChecker.exists(userId) }
        coVerify(exactly = 0) { queue.addPlayer(any()) }
        coVerify(exactly = 0) { notifier.notifyQueuePosition(any(), any()) }
        confirmVerified(userChecker, queue, notifier)
    }

    test("execute should fail when user is already in queue") {
        // Given
        val queue = mockk<MatchmakingQueue>()
        val gameCreator = mockk<GameCreator>()
        val userChecker = mockk<UserExistenceChecker>()
        val botSelector = mockk<BotSelector>()
        val notifier = mockk<MatchmakingNotifier>()

        val useCase = JoinMatchmakingUseCase(queue, gameCreator, userChecker, botSelector, notifier)
        val userId = UserId.generate()

        // Mock behavior
        coEvery { userChecker.exists(userId) } returns true
        coEvery { queue.isPlayerInQueue(userId) } returns true

        // When
        val result = useCase.execute(userId)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "User is already in the matchmaking queue"

        // Verify - notifier should NOT be called on failure
        coVerify { userChecker.exists(userId) }
        coVerify { queue.isPlayerInQueue(userId) }
        coVerify(exactly = 0) { queue.addPlayer(any()) }
        coVerify(exactly = 0) { notifier.notifyQueuePosition(any(), any()) }
    }

    test("execute should fail when user already has a match") {
        // Given
        val queue = mockk<MatchmakingQueue>()
        val gameCreator = mockk<GameCreator>()
        val userChecker = mockk<UserExistenceChecker>()
        val botSelector = mockk<BotSelector>()
        val notifier = mockk<MatchmakingNotifier>()

        val useCase = JoinMatchmakingUseCase(queue, gameCreator, userChecker, botSelector, notifier)
        val userId = UserId.generate()

        // Mock behavior
        coEvery { userChecker.exists(userId) } returns true
        coEvery { queue.isPlayerInQueue(userId) } returns true

        // When
        val result = useCase.execute(userId)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "User is already in the matchmaking queue"

        // Verify - notifier should NOT be called on failure
        coVerify { userChecker.exists(userId) }
        coVerify(exactly = 0) { queue.addPlayer(any()) }
        coVerify(exactly = 0) { notifier.notifyQueuePosition(any(), any()) }
        coVerify(exactly = 0) { notifier.notifyMatchFound(any()) }
    }

    test("execute should create match and game when match is found") {
        // Given
        val queue = mockk<MatchmakingQueue>()
        val gameCreator = mockk<GameCreator>()
        val userChecker = mockk<UserExistenceChecker>()
        val botSelector = mockk<BotSelector>()
        val notifier = mockk<MatchmakingNotifier>()

        val useCase = JoinMatchmakingUseCase(queue, gameCreator, userChecker, botSelector, notifier)

        val user1 = UserId.generate()
        val user2 = UserId.generate()
        val gameId = GameId.generate()
        val now = Clock.System.now()

        val queueEntry1 = QueueEntry(user1, now)
        val queueEntry2 = QueueEntry(user2, now)

        // Mock behavior for user1
        coEvery { userChecker.exists(user1) } returns true
        coEvery { queue.isPlayerInQueue(user1) } returns false
        coEvery { queue.addPlayer(user1) } returns queueEntry1
        coEvery { queue.findMatch() } returns Pair(queueEntry1,queueEntry2)

        // Mock gameCreator - accepts any two Players and returns gameId
        coEvery { gameCreator.createGame(any(), any()) } returns Result.success(gameId)

        // Mock notifier - capture the match that's created
        val matchSlot = slot<Match>()
        coEvery { notifier.notifyMatchFound(capture(matchSlot)) } just Runs

        // When
        val result = useCase.execute(user1)

        // Then
        result.isSuccess shouldBe true
        result.getOrNull().shouldBeInstanceOf<MatchmakingResult.Matched>()
        val matched = result.getOrNull() as MatchmakingResult.Matched
        matched.gameId shouldBe gameId

        // Verify interactions
        coVerify { queue.addPlayer(user1) }
        coVerify { queue.findMatch() }
        coVerify { gameCreator.createGame(any(), any()) }
        coVerify { notifier.notifyMatchFound(any()) } // Verify notification was sent
        coVerify(exactly = 0) { notifier.notifyQueuePosition(any(), any()) } // No queue position notification when matched
    }

    test("execute should propagate game creation failure") {
        // Given
        val queue = mockk<MatchmakingQueue>()
        val gameCreator = mockk<GameCreator>()
        val userChecker = mockk<UserExistenceChecker>()
        val botSelector = mockk<BotSelector>()
        val notifier = mockk<MatchmakingNotifier>()

        val useCase = JoinMatchmakingUseCase(queue, gameCreator, userChecker, botSelector, notifier)

        val user1 = UserId.generate()
        val user2 = UserId.generate()
        val now = Clock.System.now()
        val errorMessage = "Failed to create game in Chess context"
        val queueEntry1 = QueueEntry(user1, now)
        val queueEntry2 = QueueEntry(user2, now)

        // Mock behavior
        coEvery { userChecker.exists(user1) } returns true
        coEvery { queue.isPlayerInQueue(user1) } returns false
        coEvery { queue.addPlayer(user1) } returns QueueEntry(user1, now)
        coEvery { queue.findMatch() } returns Pair(queueEntry1, queueEntry2)
        coEvery { gameCreator.createGame(any(), any()) } returns Result.failure(Exception(errorMessage))
        coEvery { queue.getQueueSize() } returns 10

        // When
        val result = useCase.execute(user1)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe errorMessage

        // Verify - no notifications should be sent on game creation failure
        coVerify { gameCreator.createGame(any(), any()) }
        coVerify(exactly = 0) { notifier.notifyMatchFound(any()) }
        coVerify(exactly = 0) { notifier.notifyQueuePosition(any(), any()) }
    }
})
