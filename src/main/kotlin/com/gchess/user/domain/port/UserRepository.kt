package com.gchess.user.domain.port

import com.gchess.shared.domain.model.PlayerId
import com.gchess.user.domain.model.User

/**
 * Repository port for User aggregate.
 * Defines the contract for persisting and retrieving users.
 */
interface UserRepository {
    /**
     * Saves a user to the repository.
     * If a user with the same ID already exists, it will be updated.
     */
    suspend fun save(user: User): User

    /**
     * Finds a user by their unique ID.
     * Returns null if no user is found.
     */
    suspend fun findById(id: PlayerId): User?

    /**
     * Finds a user by their username.
     * Returns null if no user is found.
     */
    suspend fun findByUsername(username: String): User?

    /**
     * Finds a user by their email.
     * Returns null if no user is found.
     */
    suspend fun findByEmail(email: String): User?

    /**
     * Checks if a username is already taken.
     */
    suspend fun existsByUsername(username: String): Boolean

    /**
     * Checks if an email is already registered.
     */
    suspend fun existsByEmail(email: String): Boolean

    /**
     * Deletes a user by their ID.
     */
    suspend fun delete(id: PlayerId)

    /**
     * Returns all users in the system.
     * Note: This should be paginated in production.
     */
    suspend fun findAll(): List<User>
}
