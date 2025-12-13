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

/**
 * Environment-specific configuration for the application.
 *
 * This object centralizes environment variable reading and provides
 * sensible defaults for different environments (local, test, prod).
 *
 * Environment variables can be set via:
 * - .env files (local development)
 * - CI/CD secrets (GitHub Actions)
 * - Cloud platform environment variables (Render, AWS, etc.)
 */
object EnvironmentConfig {
    /**
     * Current environment: local, test, or prod
     */
    val environment: String = System.getenv("ENVIRONMENT") ?: "local"

    /**
     * Server port (default: 8080)
     */
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8080

    /**
     * CORS allowed origins (comma-separated)
     * Defaults based on environment if not specified
     */
    val corsOrigins: List<String> by lazy {
        System.getenv("CORS_ORIGINS")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: defaultCorsOrigins()
    }

    /**
     * Provides default CORS origins based on environment.
     *
     * - local: Common development ports
     * - test: Test domain (HTTP and HTTPS)
     * - prod: Production domain (HTTPS only)
     */
    private fun defaultCorsOrigins(): List<String> {
        return when (environment) {
            "prod" -> listOf("https://gchess.sur-le-web.fr")
            "test" -> listOf(
                "https://gchess-test.sur-le-web.fr",
                "http://gchess-test.sur-le-web.fr"
            )
            else -> listOf(
                "http://localhost:3000",
                "http://localhost:4200",
                "http://localhost:5173",
                "http://localhost:8080",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:4200",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:8080"
            )
        }
    }

    /**
     * Check if the application is running in production.
     */
    val isProduction: Boolean
        get() = environment == "prod"

    /**
     * Check if the application is running in test.
     */
    val isTest: Boolean
        get() = environment == "test"

    /**
     * Check if the application is running in local development.
     */
    val isLocal: Boolean
        get() = environment == "local"
}