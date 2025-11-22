package com.gchess.infrastructure

import io.kotest.core.spec.style.StringSpec
import org.jooq.DSLContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin

/**
 * Base class for E2E integration tests that use PostgreSQL via Testcontainers.
 *
 * Features:
 * - Uses a shared Testcontainers PostgreSQL instance (auto-started on first use)
 * - Cleans database before each test (TRUNCATE all tables)
 * - Stops Koin after each test (prevents module conflicts)
 *
 * Usage:
 * ```
 * class MyE2ETest : DatabaseE2ETest({
 *     "my test" {
 *         testApplication {
 *             application { module() } // Uses production config with Testcontainers DB
 *             // ... test code
 *         }
 *     }
 * })
 * ```
 */
abstract class DatabaseE2ETest(body: StringSpec.() -> Unit = {}) : StringSpec({
    // Apply test body
    body()

    // Before each test: set system properties for Testcontainers and clean the database
    beforeTest {
        // Set system properties so ConfigFactory.load() picks up Testcontainers parameters
        val testConfig = TestDatabaseConfig.createTestConfig()
        System.setProperty("database.url", testConfig.getString("database.url"))
        System.setProperty("database.user", testConfig.getString("database.user"))
        System.setProperty("database.password", testConfig.getString("database.password"))
        System.setProperty("database.poolSize", testConfig.getInt("database.poolSize").toString())

        // Invalidate ConfigFactory cache so it re-reads system properties
        com.typesafe.config.ConfigFactory.invalidateCaches()

        try {
            // Get DSLContext from Koin (if already initialized)
            if (GlobalContext.getOrNull() != null) {
                val dsl = GlobalContext.get().get<DSLContext>()
                TestDatabaseConfig.cleanDatabase(dsl)
            }
        } catch (e: Exception) {
            // If Koin not initialized yet, database will be cleaned on first access
            // This happens when testApplication hasn't started yet
        }
    }

    // After each test: clean database, stop Koin, and clear system properties
    afterTest {
        try {
            // Clean database before stopping Koin
            if (GlobalContext.getOrNull() != null) {
                val dsl = GlobalContext.get().get<DSLContext>()
                TestDatabaseConfig.cleanDatabase(dsl)
            }
        } catch (e: Exception) {
            // Ignore errors if Koin already stopped
        } finally {
            // Always stop Koin to prevent module conflicts between tests
            stopKoin()

            // Clear system properties
            System.clearProperty("database.url")
            System.clearProperty("database.user")
            System.clearProperty("database.password")
            System.clearProperty("database.poolSize")
        }
    }
})
