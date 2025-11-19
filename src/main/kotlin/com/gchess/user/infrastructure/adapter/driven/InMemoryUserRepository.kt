package com.gchess.user.infrastructure.adapter.driven

import com.gchess.shared.domain.model.PlayerId
import com.gchess.user.domain.model.User
import com.gchess.user.domain.port.UserRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of UserRepository.
 *
 * This is a simple implementation for development and testing.
 * In production, this would be replaced with a database-backed implementation.
 *
 * Thread-safe using ConcurrentHashMap.
 */
class InMemoryUserRepository : UserRepository {
    private val users = ConcurrentHashMap<String, User>()
    private val usernameIndex = ConcurrentHashMap<String, String>() // username -> userId
    private val emailIndex = ConcurrentHashMap<String, String>() // email -> userId

    override suspend fun save(user: User): User {
        users[user.id.value] = user
        usernameIndex[user.username.lowercase()] = user.id.value
        emailIndex[user.email.lowercase()] = user.id.value
        return user
    }

    override suspend fun findById(id: PlayerId): User? {
        return users[id.value]
    }

    override suspend fun findByUsername(username: String): User? {
        val userId = usernameIndex[username.lowercase()] ?: return null
        return users[userId]
    }

    override suspend fun findByEmail(email: String): User? {
        val userId = emailIndex[email.lowercase()] ?: return null
        return users[userId]
    }

    override suspend fun existsByUsername(username: String): Boolean {
        return usernameIndex.containsKey(username.lowercase())
    }

    override suspend fun existsByEmail(email: String): Boolean {
        return emailIndex.containsKey(email.lowercase())
    }

    override suspend fun delete(id: PlayerId) {
        val user = users.remove(id.value)
        if (user != null) {
            usernameIndex.remove(user.username.lowercase())
            emailIndex.remove(user.email.lowercase())
        }
    }

    override suspend fun findAll(): List<User> {
        return users.values.toList()
    }
}
