package com.gchess.infrastructure.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.gchess.shared.domain.model.PlayerId
import java.util.*

/**
 * Configuration for JWT token generation and validation.
 *
 * In a production environment:
 * - SECRET should be stored in environment variables
 * - Use a stronger secret (at least 256 bits)
 * - Consider using RS256 with public/private keys
 */
object JwtConfig {
    // TODO: Move to environment variables in production
    private const val SECRET = "gchess-jwt-secret-key-change-in-production"
    private const val ISSUER = "gchess"
    private const val AUDIENCE = "gchess-users"
    const val REALM = "gChess API"

    // Token validity: 24 hours
    private const val VALIDITY_MS = 24 * 60 * 60 * 1000L // 24 hours

    private val algorithm = Algorithm.HMAC256(SECRET)

    /**
     * Generates a JWT token for a given player.
     *
     * @param playerId The ID of the player to generate a token for
     * @return The generated JWT token as a string
     */
    fun generateToken(playerId: PlayerId): String {
        return JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("playerId", playerId.value)
            .withExpiresAt(Date(System.currentTimeMillis() + VALIDITY_MS))
            .sign(algorithm)
    }

    /**
     * Creates a JWT verifier for validating tokens.
     *
     * @return A configured JWT verifier
     */
    fun makeVerifier() = JWT.require(algorithm)
        .withAudience(AUDIENCE)
        .withIssuer(ISSUER)
        .build()

    /**
     * Extracts the player ID from a validated JWT token.
     *
     * @param payload The JWT payload (from JWTPrincipal)
     * @return The PlayerId embedded in the token
     */
    fun extractPlayerId(payload: com.auth0.jwt.interfaces.Payload): PlayerId {
        val playerIdValue = payload.getClaim("playerId").asString()
        return PlayerId.fromString(playerIdValue)
    }
}
