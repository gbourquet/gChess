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
