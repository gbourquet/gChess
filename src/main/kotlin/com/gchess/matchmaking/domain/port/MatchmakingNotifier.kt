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

import com.gchess.matchmaking.domain.model.Match
import com.gchess.shared.domain.model.UserId

/**
 * Port for sending real-time notifications during matchmaking.
 *
 * This abstraction allows the domain to send notifications without depending
 * on specific infrastructure (WebSocket, SSE, etc.).
 *
 * Implementation note:
 * - The infrastructure layer provides the concrete implementation (e.g., WebSocket)
 * - Notifications are best-effort: failures should be logged but not stop the process
 */
interface MatchmakingNotifier {
    /**
     * Notify a user of their current queue position.
     *
     * @param userId The user to notify
     * @param position Current position in the queue (1-indexed)
     */
    suspend fun notifyQueuePosition(userId: UserId, position: Int)

    /**
     * Notify two users that a match has been found.
     * This should be sent to both users simultaneously.
     *
     * @param match The match that was created
     */
    suspend fun notifyMatchFound(match: Match)

    /**
     * Notify a user of a matchmaking error.
     *
     * @param userId The user to notify
     * @param errorCode Error code (e.g., "ALREADY_IN_QUEUE")
     * @param message Human-readable error message
     */
    suspend fun notifyError(userId: UserId, errorCode: String, message: String)
}
