/*
 * Copyright (c) 2024-2025 Guillaume Bourquet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.gchess.matchmaking.domain.port

import com.gchess.matchmaking.domain.model.QueueEntry
import com.gchess.shared.domain.model.UserId

/**
 * Port (interface) for managing the matchmaking queue.
 *
 * This interface defines operations for adding/removing users from the queue
 * and finding matches between waiting users.
 *
 * Implementations should ensure thread-safety and FIFO (First-In-First-Out) ordering.
 */
interface MatchmakingQueue {
    /**
     * Adds a user to the matchmaking queue.
     *
     * @param userId The unique identifier of the user to add
     * @return The created QueueEntry
     * @throws IllegalStateException if the user is already in the queue
     */
    suspend fun addPlayer(userId: UserId): QueueEntry

    /**
     * Removes a user from the matchmaking queue.
     *
     * @param userId The unique identifier of the user to remove
     * @return true if the user was removed, false if the user was not in the queue
     */
    suspend fun removePlayer(userId: UserId): Boolean

    /**
     * Attempts to find a match between two users in the queue.
     *
     * This method should:
     * - Return null if there are fewer than 2 users in the queue
     * - Remove both matched users from the queue atomically
     * - Follow FIFO ordering (match the two oldest entries)
     *
     * @return A Pair of the two matched QueueEntries, or null if no match is possible
     */
    suspend fun findMatch(): Pair<QueueEntry, QueueEntry>?

    /**
     * Checks if a specific user is currently in the queue.
     *
     * @param userId The unique identifier of the user to check
     * @return true if the user is in the queue, false otherwise
     */
    suspend fun isPlayerInQueue(userId: UserId): Boolean

    /**
     * Gets the current number of users in the queue.
     *
     * @return The queue size
     */
    suspend fun getQueueSize(): Int
}
