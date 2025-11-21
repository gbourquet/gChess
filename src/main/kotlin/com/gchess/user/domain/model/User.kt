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
package com.gchess.user.domain.model

import com.gchess.shared.domain.model.PlayerId

/**
 * Domain model representing a user in the system.
 *
 * Invariants:
 * - Username must be unique across all users
 * - Email must be unique and valid
 * - Password is stored hashed, never in plain text
 */
data class User(
    val id: PlayerId,
    val username: String,
    val email: String,
    val passwordHash: String
) {
    init {
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(username.length >= 3) { "Username must be at least 3 characters" }
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(email.contains("@")) { "Email must be valid" }
        require(passwordHash.isNotBlank()) { "Password hash cannot be blank" }
    }

    /**
     * Returns a copy of this user with the password hash masked for security.
     * Useful for logging or displaying user information.
     */
    fun withMaskedPassword(): User = copy(passwordHash = "***MASKED***")
}
