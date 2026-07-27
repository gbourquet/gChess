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

import com.gchess.chess.domain.port.UsernameResolver
import com.gchess.shared.domain.model.UserId
import com.gchess.user.application.usecase.GetUserUseCase

/**
 * Anti-Corruption Layer adapter that allows the Chess context to display
 * usernames by communicating with the User context.
 *
 * This adapter:
 * - Implements the UsernameResolver port (defined in Chess domain)
 * - Calls GetUserUseCase from the User context
 * - Extracts the username only, so the User aggregate never leaks into Chess
 *
 * Being in the infrastructure layer, it is the only place allowed to cross
 * the bounded context boundary (enforced by BoundedContextTest).
 */
class UserContextUsernameResolver(
    private val getUserUseCase: GetUserUseCase
) : UsernameResolver {
    override suspend fun resolve(userId: UserId): String? {
        return getUserUseCase.execute(userId)?.username
    }
}
