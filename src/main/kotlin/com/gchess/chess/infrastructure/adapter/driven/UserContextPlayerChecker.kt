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
package com.gchess.chess.infrastructure.adapter.driven

import com.gchess.chess.domain.port.PlayerExistenceChecker
import com.gchess.shared.domain.model.PlayerId
import com.gchess.user.application.usecase.GetUserUseCase

/**
 * Anti-Corruption Layer adapter that allows the Chess context to verify
 * player existence by communicating with the User context.
 *
 * This implementation uses a fail-fast strategy:
 * - If the User context fails, the exception is propagated immediately
 * - No fallback or pessimistic behavior
 *
 * This ensures consistency - if we can't verify player existence,
 * we don't allow game creation/moves.
 */
class UserContextPlayerChecker(
    private val getUserUseCase: GetUserUseCase
) : PlayerExistenceChecker {

    override suspend fun exists(playerId: PlayerId): Boolean {
        return try {
            val user = getUserUseCase.execute(playerId)
            user != null
        } catch (e: Exception) {
            // Fail-fast: propagate errors from User context
            throw Exception("Failed to check player existence for ${playerId.value}: ${e.message}", e)
        }
    }
}
