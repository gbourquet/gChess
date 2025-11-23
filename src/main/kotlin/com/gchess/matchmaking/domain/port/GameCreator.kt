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

import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.GameId

/**
 * Port (interface) for creating chess games.
 *
 * This is an Anti-Corruption Layer (ACL) port that allows the Matchmaking context
 * to request game creation from the Chess context without direct dependency.
 *
 * The Matchmaking context is responsible for:
 * - Validating users exist
 * - Creating Player objects with random color assignment
 * - Passing these Players to the Chess context via this port
 *
 * The implementation will adapt calls to the Chess context's CreateGameUseCase.
 */
interface GameCreator {
    /**
     * Creates a new chess game with the provided players.
     *
     * The Matchmaking context creates the Player objects (including PlayerId generation
     * and color assignment) before calling this method.
     *
     * @param whitePlayer The player who will control white pieces (must have PlayerSide.WHITE)
     * @param blackPlayer The player who will control black pieces (must have PlayerSide.BLACK)
     * @return Result containing the created GameId on success, or an error on failure
     */
    suspend fun createGame(whitePlayer: Player, blackPlayer: Player): Result<GameId>
}
