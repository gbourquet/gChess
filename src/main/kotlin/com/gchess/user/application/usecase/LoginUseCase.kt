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

import com.gchess.user.domain.model.Credentials
import com.gchess.user.domain.model.User
import com.gchess.user.domain.port.PasswordHasher
import com.gchess.user.domain.port.UserRepository

/**
 * Use case for authenticating a user.
 *
 * Business rules:
 * - User must exist
 * - Password must match the stored hash
 */
class LoginUseCase(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher
) {
    /**
     * Authenticates a user with their credentials.
     *
     * @param credentials The user's login credentials
     * @return Result containing the authenticated User or an error
     */
    suspend fun execute(credentials: Credentials): Result<User> {
        // Find user by username
        val user = userRepository.findByUsername(credentials.username)
            ?: return Result.failure(Exception("Invalid username or password"))

        // Verify password
        val isPasswordValid = passwordHasher.verify(credentials.password, user.passwordHash)
        if (!isPasswordValid) {
            return Result.failure(Exception("Invalid username or password"))
        }

        return Result.success(user)
    }
}
