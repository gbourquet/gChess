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
package com.gchess.infrastructure.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.gchess.shared.domain.model.PlayerId
import java.util.*

/**
 * Configuration for JWT token generation and validation.
 *
 * Configuration is loaded from environment variables with sensible defaults for development.
 *
 * **IMPORTANT for production:**
 * - JWT_SECRET MUST be set to a secure value (minimum 256 bits)
 * - Use a strong random secret (e.g., output of `openssl rand -base64 32`)
 * - Consider using RS256 with public/private keys for microservices
 *
 * Environment variables:
 * - JWT_SECRET: Secret key for signing tokens (REQUIRED in production)
 * - JWT_VALIDITY_MS: Token validity in milliseconds (default: 24 hours)
 */
object JwtConfig {
    // Load from environment variable, fallback to default for development
    private val SECRET = System.getenv("JWT_SECRET")
        ?: "gchess-jwt-secret-key-change-in-production".also {
            println("⚠️  WARNING: Using default JWT secret. Set JWT_SECRET environment variable in production!")
        }

    private const val ISSUER = "gchess"
    private const val AUDIENCE = "gchess-users"
    const val REALM = "gChess API"

    // Token validity: default 24 hours, configurable via env var
    private val VALIDITY_MS = System.getenv("JWT_VALIDITY_MS")?.toLongOrNull()
        ?: (24 * 60 * 60 * 1000L) // 24 hours

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
