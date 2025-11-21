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
package com.gchess.user.infrastructure.adapter.driven

import com.gchess.infrastructure.persistence.jooq.tables.references.USERS
import com.gchess.shared.domain.model.PlayerId
import com.gchess.user.domain.model.User
import com.gchess.user.domain.port.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.LocalDateTime

/**
 * PostgreSQL implementation of UserRepository using jOOQ.
 *
 * All database operations are wrapped in `withContext(Dispatchers.IO)` for proper
 * coroutine support with blocking JDBC calls.
 *
 * Key features:
 * - Upsert support (INSERT ... ON CONFLICT DO UPDATE) for save()
 * - Case-insensitive username/email lookups using DSL.lower()
 * - Automatic timestamp management (created_at, updated_at)
 */
class PostgresUserRepository(
    private val dsl: DSLContext
) : UserRepository {

    override suspend fun save(user: User): User = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()

        dsl.insertInto(USERS)
            .set(USERS.ID, user.id.value)
            .set(USERS.USERNAME, user.username)
            .set(USERS.EMAIL, user.email)
            .set(USERS.PASSWORD_HASH, user.passwordHash)
            .set(USERS.CREATED_AT, now)
            .set(USERS.UPDATED_AT, now)
            .onConflict(USERS.ID)
            .doUpdate()
            .set(USERS.USERNAME, user.username)
            .set(USERS.EMAIL, user.email)
            .set(USERS.PASSWORD_HASH, user.passwordHash)
            .set(USERS.UPDATED_AT, now)
            .execute()

        user
    }

    override suspend fun findById(id: PlayerId): User? = withContext(Dispatchers.IO) {
        dsl.select(USERS.ID, USERS.USERNAME, USERS.EMAIL, USERS.PASSWORD_HASH)
            .from(USERS)
            .where(USERS.ID.eq(id.value))
            .fetchOne()
            ?.let { record ->
                User(
                    id = PlayerId(record.value1()!!),
                    username = record.value2()!!,
                    email = record.value3()!!,
                    passwordHash = record.value4()!!
                )
            }
    }

    override suspend fun findByUsername(username: String): User? = withContext(Dispatchers.IO) {
        dsl.select(USERS.ID, USERS.USERNAME, USERS.EMAIL, USERS.PASSWORD_HASH)
            .from(USERS)
            .where(DSL.lower(USERS.USERNAME).eq(username.lowercase()))
            .fetchOne()
            ?.let { record ->
                User(
                    id = PlayerId(record.value1()!!),
                    username = record.value2()!!,
                    email = record.value3()!!,
                    passwordHash = record.value4()!!
                )
            }
    }

    override suspend fun findByEmail(email: String): User? = withContext(Dispatchers.IO) {
        dsl.select(USERS.ID, USERS.USERNAME, USERS.EMAIL, USERS.PASSWORD_HASH)
            .from(USERS)
            .where(DSL.lower(USERS.EMAIL).eq(email.lowercase()))
            .fetchOne()
            ?.let { record ->
                User(
                    id = PlayerId(record.value1()!!),
                    username = record.value2()!!,
                    email = record.value3()!!,
                    passwordHash = record.value4()!!
                )
            }
    }

    override suspend fun existsByUsername(username: String): Boolean = withContext(Dispatchers.IO) {
        dsl.fetchExists(
            dsl.select(USERS.ID)
                .from(USERS)
                .where(DSL.lower(USERS.USERNAME).eq(username.lowercase()))
        )
    }

    override suspend fun existsByEmail(email: String): Boolean = withContext(Dispatchers.IO) {
        dsl.fetchExists(
            dsl.select(USERS.ID)
                .from(USERS)
                .where(DSL.lower(USERS.EMAIL).eq(email.lowercase()))
        )
    }

    override suspend fun delete(id: PlayerId): Unit = withContext(Dispatchers.IO) {
        dsl.deleteFrom(USERS)
            .where(USERS.ID.eq(id.value))
            .execute()
    }

    override suspend fun findAll(): List<User> = withContext(Dispatchers.IO) {
        dsl.select(USERS.ID, USERS.USERNAME, USERS.EMAIL, USERS.PASSWORD_HASH)
            .from(USERS)
            .fetch()
            .map { record ->
                User(
                    id = PlayerId(record.value1()!!),
                    username = record.value2()!!,
                    email = record.value3()!!,
                    passwordHash = record.value4()!!
                )
            }
    }
}
