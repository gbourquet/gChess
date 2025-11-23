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
package com.gchess.user.domain.port

import com.gchess.shared.domain.model.UserId
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
    suspend fun findById(id: UserId): User?

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
    suspend fun delete(id: UserId)

    /**
     * Returns all users in the system.
     * Note: This should be paginated in production.
     */
    suspend fun findAll(): List<User>
}
