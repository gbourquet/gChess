package com.gchess.infrastructure

import io.kotest.core.spec.style.StringSpec
import org.jooq.DSLContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin

/**
 * Base class for E2E integration tests that use PostgreSQL via Testcontainers.
 *
 * Features:
 * - Cleans database before each test (TRUNCATE all tables)
 * - Stops Koin after each test (prevents module conflicts)
 * - Provides access to test DSLContext if needed
 *
 * Usage:
 * ```
 * class MyE2ETest : DatabaseE2ETest({
 *     "my test" {
 *         testApplication {
 *             application { module(testModule) }
 *             // ... test code
 *         }
 *     }
 * })
 * ```
 */
abstract class DatabaseE2ETest(body: StringSpec.() -> Unit = {}) : StringSpec({
    // Apply test body
    body()

    // Before each test: clean the database
    beforeTest {
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

    // After each test: clean database and stop Koin
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
        }
    }
})
