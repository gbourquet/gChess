package com.gchess.user.domain.port

/**
 * Port for hashing and verifying passwords.
 *
 * This abstraction allows the domain to hash passwords without depending
 * on a specific hashing algorithm implementation.
 */
interface PasswordHasher {
    /**
     * Hashes a plain text password.
     *
     * @param plainPassword The password in plain text
     * @return The hashed password (safe to store)
     */
    fun hash(plainPassword: String): String

    /**
     * Verifies that a plain text password matches a hashed password.
     *
     * @param plainPassword The password to verify
     * @param hashedPassword The stored hash to compare against
     * @return true if the password matches, false otherwise
     */
    fun verify(plainPassword: String, hashedPassword: String): Boolean
}
