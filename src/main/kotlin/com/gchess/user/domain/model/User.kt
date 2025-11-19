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
