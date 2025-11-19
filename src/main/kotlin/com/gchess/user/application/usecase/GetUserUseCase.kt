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
