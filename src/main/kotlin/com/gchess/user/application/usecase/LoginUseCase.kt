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
