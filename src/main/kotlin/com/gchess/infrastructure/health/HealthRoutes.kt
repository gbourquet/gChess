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
package com.gchess.infrastructure.health

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Simple health status response for load balancers and monitoring tools.
 *
 * @property status Overall health status ("UP" or "DOWN")
 * @property timestamp Unix timestamp when the check was performed
 * @property version Application version
 */
@Serializable
data class HealthStatus(
    val status: String,
    val timestamp: Long,
    val version: String = "1.0.0"
)

/**
 * Detailed health status with component-level health checks.
 *
 * @property status Overall health status ("UP" or "DOWN")
 * @property timestamp Unix timestamp when the check was performed
 * @property version Application version
 * @property checks Map of component names to their health status
 */
@Serializable
data class DetailedHealthStatus(
    val status: String,
    val timestamp: Long,
    val version: String = "1.0.0",
    val checks: Map<String, ComponentHealth>
)

/**
 * Health status of an individual component.
 *
 * @property status Component status ("UP" or "DOWN")
 * @property message Optional message with additional details
 */
@Serializable
data class ComponentHealth(
    val status: String,
    val message: String? = null
)

/**
 * Configures health check routes for the application.
 *
 * This provides multiple endpoints for different monitoring purposes:
 * - `/health`: Simple UP/DOWN status for load balancers
 * - `/actuator/health`: Detailed status with component checks
 * - `/ready`: Readiness probe for orchestrators (checks dependencies)
 * - `/alive`: Liveness probe for orchestrators (lightweight check)
 *
 * @receiver Application The Ktor application to configure
 */
fun Application.configureHealthRoutes() {
    val healthCheckService: HealthCheckService by inject()

    routing {
        /**
         * Simple health check endpoint for load balancers.
         *
         * Returns 200 OK if the application is running.
         * This is a lightweight check that doesn't verify external dependencies.
         *
         * Response: {"status": "UP", "timestamp": 1234567890, "version": "1.0.0"}
         */
        get("/health") {
            call.respond(
                HttpStatusCode.OK, HealthStatus(
                    status = "UP",
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        /**
         * Detailed health check endpoint with component-level checks.
         *
         * Verifies connectivity to:
         * - Database (PostgreSQL)
         * - Matchmaking queue
         *
         * Returns 200 OK if all components are healthy.
         * Returns 503 Service Unavailable if any component is down.
         *
         * Response: {
         *   "status": "UP",
         *   "timestamp": 1234567890,
         *   "version": "1.0.0",
         *   "checks": {
         *     "database": {"status": "UP", "message": "Connected"},
         *     "matchmaking": {"status": "UP", "message": "Queue size: 5"}
         *   }
         * }
         */
        get("/actuator/health") {
            val dbHealth = healthCheckService.checkDatabaseConnectivity()
            val matchmakingHealth = healthCheckService.checkMatchmakingQueue()

            val checks = mapOf(
                "database" to dbHealth,
                "matchmaking" to matchmakingHealth
            )

            val overallStatus = if (checks.all { it.value.status == "UP" }) "UP" else "DOWN"
            val httpStatus =
                if (overallStatus == "UP") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable

            call.respond(
                httpStatus, DetailedHealthStatus(
                    status = overallStatus,
                    timestamp = System.currentTimeMillis(),
                    checks = checks
                )
            )
        }

        /**
         * Readiness probe for Kubernetes/orchestrators.
         *
         * Indicates whether the application is ready to serve traffic.
         * Checks all external dependencies (database, matchmaking queue).
         *
         * Returns 200 OK if ready, 503 Service Unavailable if not ready.
         */
        get("/ready") {
            val isReady = healthCheckService.isApplicationReady()
            if (isReady) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "READY"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "NOT_READY"))
            }
        }

        /**
         * Liveness probe for Kubernetes/orchestrators.
         *
         * Indicates whether the application process is alive and should not be restarted.
         * This is a lightweight check that doesn't verify external dependencies.
         *
         * Always returns 200 OK (if we can respond, the application is alive).
         */
        get("/alive") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ALIVE"))
        }
    }
}