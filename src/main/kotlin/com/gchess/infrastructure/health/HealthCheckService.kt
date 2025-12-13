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

import com.gchess.matchmaking.domain.port.MatchmakingQueue
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

/**
 * Service for performing health checks on critical application components.
 *
 * This service is used by orchestration tools (Docker, Kubernetes, load balancers)
 * to determine if the application is healthy and ready to serve traffic.
 *
 * @property dslContext jOOQ DSL context for database connectivity checks
 * @property matchmakingQueue Matchmaking queue for service health checks
 */
class HealthCheckService(
    private val dslContext: DSLContext,
    private val matchmakingQueue: MatchmakingQueue
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Checks database connectivity by executing a simple query.
     *
     * @return ComponentHealth with status UP if database is reachable, DOWN otherwise
     */
    fun checkDatabaseConnectivity(): ComponentHealth {
        return try {
            dslContext.selectOne().fetch()
            ComponentHealth(status = "UP", message = "Database connection successful")
        } catch (e: Exception) {
            logger.error("Database health check failed", e)
            ComponentHealth(status = "DOWN", message = "Database unreachable: ${e.message}")
        }
    }

    /**
     * Checks matchmaking queue health and reports current queue size.
     *
     * @return ComponentHealth with status UP if queue is accessible, DOWN otherwise
     */
    fun checkMatchmakingQueue(): ComponentHealth {
        return try {
            runBlocking {
                val queueSize = matchmakingQueue.getQueueSize()
                ComponentHealth(status = "UP", message = "Queue size: $queueSize")
            }
        } catch (e: Exception) {
            logger.error("Matchmaking queue health check failed", e)
            ComponentHealth(status = "DOWN", message = "Queue unavailable: ${e.message}")
        }
    }

    /**
     * Determines if the application is ready to serve traffic.
     *
     * The application is considered ready if:
     * - Database is reachable
     * - Matchmaking queue is accessible
     *
     * @return true if all critical components are healthy, false otherwise
     */
    fun isApplicationReady(): Boolean {
        val dbHealth = checkDatabaseConnectivity()
        val queueHealth = checkMatchmakingQueue()
        return dbHealth.status == "UP" && queueHealth.status == "UP"
    }

    /**
     * Determines if the application is alive (basic liveness check).
     *
     * This is a lightweight check that simply confirms the JVM process is running.
     * Unlike readiness checks, liveness checks don't verify external dependencies.
     *
     * @return always true (if we can execute this code, the application is alive)
     */
    fun isApplicationAlive(): Boolean = true
}