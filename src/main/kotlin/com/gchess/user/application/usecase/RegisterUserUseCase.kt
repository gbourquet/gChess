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
import com.gchess.user.domain.port.PasswordHasher
import com.gchess.user.domain.port.UserRepository

/**
 * Use case for registering a new user in the system.
 *
 * Business rules:
 * - Username must be unique
 * - Email must be unique
 * - Password is hashed before storage
 */
class RegisterUserUseCase(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher
) {
    /**
     * Registers a new user.
     *
     * @param username The desired username
     * @param email The user's email address
     * @param plainPassword The password in plain text (will be hashed)
     * @return Result containing the created User or an error
     */
    suspend fun execute(username: String, email: String, plainPassword: String): Result<User> {
        // Validate username uniqueness
        if (userRepository.existsByUsername(username)) {
            return Result.failure(Exception("Username '$username' is already taken"))
        }

        // Validate email uniqueness
        if (userRepository.existsByEmail(email)) {
            return Result.failure(Exception("Email '$email' is already registered"))
        }

        // Validate password strength (basic validation)
        if (plainPassword.length < 8) {
            return Result.failure(Exception("Password must be at least 8 characters long"))
        }

        // Hash the password
        val passwordHash = passwordHasher.hash(plainPassword)

        // Create the user
        val user = User(
            id = PlayerId.generate(),
            username = username,
            email = email,
            passwordHash = passwordHash
        )

        // Save the user
        val savedUser = userRepository.save(user)

        return Result.success(savedUser)
    }
}
