package com.gchess.user.domain.model

/**
 * Value object representing user credentials for authentication.
 *
 * Important: This contains the plain text password and should only be used
 * during the authentication process. Never persist or log this object.
 */
data class Credentials(
    val username: String,
    val password: String
) {
    init {
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(password.isNotBlank()) { "Password cannot be blank" }
    }

    /**
     * Returns a masked version for safe logging.
     * Never log the actual credentials!
     */
    override fun toString(): String = "Credentials(username=$username, password=***)"
}
