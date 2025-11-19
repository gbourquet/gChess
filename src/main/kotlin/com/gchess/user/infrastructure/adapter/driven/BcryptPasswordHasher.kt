package com.gchess.user.infrastructure.adapter.driven

import com.gchess.user.domain.port.PasswordHasher
import org.mindrot.jbcrypt.BCrypt

/**
 * BCrypt implementation of the PasswordHasher port.
 *
 * BCrypt is a secure password hashing algorithm that includes:
 * - Automatic salt generation
 * - Configurable work factor (cost)
 * - Resistance to rainbow table attacks
 */
class BcryptPasswordHasher(
    private val workFactor: Int = 12
) : PasswordHasher {

    init {
        require(workFactor in 4..31) { "BCrypt work factor must be between 4 and 31" }
    }

    /**
     * Hashes a password using BCrypt.
     * The salt is automatically generated.
     */
    override fun hash(plainPassword: String): String {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(workFactor))
    }

    /**
     * Verifies a password against a BCrypt hash.
     */
    override fun verify(plainPassword: String, hashedPassword: String): Boolean {
        return try {
            BCrypt.checkpw(plainPassword, hashedPassword)
        } catch (e: IllegalArgumentException) {
            // Invalid hash format
            false
        }
    }
}
