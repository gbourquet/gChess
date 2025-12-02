package com.gchess.chess.infrastructure.adapter.driver

import com.gchess.chess.infrastructure.adapter.driver.dto.GameWebSocketMessage
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.UserId
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages WebSocket connections for spectators observing games.
 *
 * Architecture:
 * - Map<GameId, MutableSet<Pair<UserId, WebSocketSession>>> - Multiple spectators per game
 * - Each spectator identified by UserId (not PlayerId, as they're not participants)
 * - Thread-safe using ConcurrentHashMap with synchronized set operations
 *
 * Responsibilities:
 * - Register/unregister spectators for games
 * - Broadcast messages to all spectators of a game
 * - Get spectator count per game
 * - Clean up dead connections
 */
class SpectatorConnectionManager {
    private val logger = LoggerFactory.getLogger(SpectatorConnectionManager::class.java)
    // GameId -> Set of (UserId, WebSocketSession) pairs
    private val spectators = ConcurrentHashMap<GameId, MutableSet<Pair<UserId, WebSocketSession>>>()
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    /**
     * Register a spectator for a game.
     * If the user is already spectating the game, their session will be replaced.
     */
    fun register(gameId: GameId, userId: UserId, session: WebSocketSession) {
        spectators.compute(gameId) { _, existingSet ->
            val set = existingSet ?: mutableSetOf()
            // Remove old session for this user (if exists)
            set.removeIf { (id, _) -> id == userId }
            // Add new session
            set.add(Pair(userId, session))
            logger.info("User $userId joined as spectator for game $gameId (spectators: ${set.size})")
            set
        }
    }

    /**
     * Unregister a spectator from a game.
     * @return true if the spectator was removed, false if not found
     */
    fun unregister(gameId: GameId, userId: UserId): Boolean {
        var removed = false
        spectators.computeIfPresent(gameId) { _, set ->
            removed = set.removeIf { (id, _) -> id == userId }
            if (removed) {
                logger.info("User $userId left as spectator from game $gameId (spectators: ${set.size})")
            }
            // Remove the game entry if no more spectators
            if (set.isEmpty()) null else set
        }
        return removed
    }

    /**
     * Broadcast a message to all spectators of a game.
     * @return number of spectators who received the message
     */
    suspend fun broadcastToSpectators(gameId: GameId, message: GameWebSocketMessage): Int {
        val gameSpectators = spectators[gameId] ?: return 0

        var sentCount = 0
        val deadSpectators = mutableListOf<UserId>()

        for ((userId, session) in gameSpectators) {
            try {
                val json = json.encodeToString(GameWebSocketMessage.serializer(), message)
                session.send(Frame.Text(json))
                sentCount++
            } catch (e: Exception) {
                logger.error("Failed to send message to spectator $userId for game $gameId", e)
                deadSpectators.add(userId)
            }
        }

        // Clean up dead connections
        if (deadSpectators.isNotEmpty()) {
            deadSpectators.forEach { userId -> unregister(gameId, userId) }
        }

        logger.debug("Broadcast ${message.type} to $sentCount spectators of game $gameId")
        return sentCount
    }

    /**
     * Get the number of spectators for a game.
     */
    fun getSpectatorCount(gameId: GameId): Int {
        return spectators[gameId]?.size ?: 0
    }

    /**
     * Get all gameIds that have spectators.
     */
    fun getGamesWithSpectators(): Set<GameId> {
        return spectators.keys.toSet()
    }

    /**
     * Close all spectator WebSocket connections for a game.
     * Should be called when a game ends (CHECKMATE, STALEMATE, DRAW).
     *
     * @param gameId The game whose spectator connections should be closed
     * @return Number of spectator connections that were closed
     */
    suspend fun closeGameSpectators(gameId: GameId): Int {
        val gameSpectators = spectators.remove(gameId) ?: return 0

        var closedCount = 0

        for ((userId, session) in gameSpectators) {
            try {
                session.close(CloseReason(
                    CloseReason.Codes.NORMAL,
                    "Game finished"
                ))
                closedCount++
                logger.info("Closed WebSocket for spectator $userId (game $gameId ended)")
            } catch (e: Exception) {
                logger.error("Error closing WebSocket for spectator $userId", e)
            }
        }

        logger.info("Closed $closedCount spectator connections for game $gameId")
        return closedCount
    }
}