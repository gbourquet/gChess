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
package com.gchess.user.application.usecase

import com.gchess.shared.domain.model.PlayerId
import com.gchess.user.domain.model.User
import com.gchess.user.domain.port.UserRepository

/**
 * Use case for retrieving a user by their ID.
 *
 * This will be used by the Anti-Corruption Layer in Phase 4
 * to validate player existence when creating games.
 */
class GetUserUseCase(
    private val userRepository: UserRepository
) {
    /**
     * Retrieves a user by their ID.
     *
     * @param userId The user's unique identifier
     * @return The User if found, null otherwise
     */
    suspend fun execute(userId: PlayerId): User? {
        return userRepository.findById(userId)
    }

    /**
     * Checks if a user exists.
     *
     * @param userId The user's unique identifier
     * @return true if the user exists, false otherwise
     */
    suspend fun exists(userId: PlayerId): Boolean {
        return userRepository.findById(userId) != null
    }
}
