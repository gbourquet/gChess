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
package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.matchmaking.domain.port.UserExistenceChecker
import com.gchess.shared.domain.model.UserId
import com.gchess.user.application.usecase.GetUserUseCase

/**
 * Anti-Corruption Layer adapter that allows the Matchmaking context
 * to validate user existence by communicating with the User context.
 *
 * This adapter:
 * - Implements the UserExistenceChecker port (defined in Matchmaking domain)
 * - Calls GetUserUseCase from the User context
 * - Translates the result to a boolean (exists/doesn't exist)
 * - Maintains bounded context isolation (Matchmaking doesn't depend on User domain models)
 *
 * Error handling strategy: Fail-fast
 * - If GetUserUseCase throws an exception, we propagate it
 * - The caller (Matchmaking use case) can decide how to handle the error
 */
class UserContextUserChecker(
    private val getUserUseCase: GetUserUseCase
) : UserExistenceChecker {

    override suspend fun exists(userId: UserId): Boolean {
        // Call User context use case
        val user = getUserUseCase.execute(userId)

        // User exists if GetUserUseCase returns a non-null user
        return user != null
    }
}
