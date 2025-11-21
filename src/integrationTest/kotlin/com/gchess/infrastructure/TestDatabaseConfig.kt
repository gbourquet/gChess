package com.gchess.infrastructure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

/**
 * Test database configuration using Testcontainers.
 *
 * Provides:
 * - Singleton PostgreSQL container (shared across all tests for performance)
 * - Test DataSource and DSLContext
 * - Database cleanup between tests (TRUNCATE all tables)
 *
 * The container is started once and reused across all integration tests.
 * This approach is much faster than starting a new container per test.
 */
object TestDatabaseConfig {

    /**
     * Singleton PostgreSQL container.
     * Started once and reused across all tests.
     */
    private val postgresContainer: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("gchess_test")
            .withUsername("test_user")
            .withPassword("test_password")
            .also { container ->
                container.start()
                println("✅ Test PostgreSQL container started: ${container.jdbcUrl}")
            }
    }

    /**
     * Creates a test DataSource connected to the Testcontainers PostgreSQL instance.
     *
     * Configuration:
     * - HikariCP connection pool
     * - Small pool size (5 connections) for tests
     * - Fast timeouts for quick failure detection
     *
     * @return DataSource connected to test database
     */
    fun createTestDataSource(): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = postgresContainer.jdbcUrl
            username = postgresContainer.username
            password = postgresContainer.password
            driverClassName = "org.postgresql.Driver"

            // Small pool for tests
            maximumPoolSize = 5

            // Fast timeouts for tests
            connectionTimeout = 10_000 // 10 seconds
            idleTimeout = 60_000 // 1 minute
            maxLifetime = 300_000 // 5 minutes

            // Test query
            connectionTestQuery = "SELECT 1"

            // Auto-commit enabled by default
            isAutoCommit = true

            // Transaction isolation
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
        }

        return HikariDataSource(config)
    }

    /**
     * Creates a test DSLContext (jOOQ) and runs Liquibase migrations.
     *
     * This function:
     * 1. Runs Liquibase migrations on the test database (if not already applied)
     * 2. Creates and returns a jOOQ DSLContext
     *
     * @param dataSource Test DataSource
     * @return DSLContext configured for test database
     */
    fun createTestDslContext(dataSource: DataSource): DSLContext {
        // Run migrations
        runMigrations(dataSource)

        // Create and return DSLContext
        return DSL.using(dataSource, SQLDialect.POSTGRES)
    }

    /**
     * Runs Liquibase migrations on the test database.
     *
     * @param dataSource Test DataSource
     */
    private fun runMigrations(dataSource: DataSource) {
        dataSource.connection.use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))

            val liquibase = Liquibase(
                "db/changelog/db.changelog-master.xml",
                ClassLoaderResourceAccessor(),
                database
            )

            liquibase.update(Contexts())
            println("✅ Test database migrations applied successfully")
        }
    }

    /**
     * Cleans the test database by truncating all tables.
     *
     * This is called between tests to ensure a clean slate.
     * Uses TRUNCATE CASCADE to handle foreign key constraints.
     *
     * Tables cleaned:
     * - game_moves (child of games)
     * - matches (child of games)
     * - games (parent, references users)
     * - users (parent)
     *
     * @param dsl DSLContext for test database
     */
    fun cleanDatabase(dsl: DSLContext) {
        dsl.execute("TRUNCATE TABLE game_moves, matches, games, users CASCADE")
    }

    /**
     * Stops the PostgreSQL container.
     *
     * Typically not needed as the container will be stopped when the JVM exits.
     * Provided for explicit cleanup if needed.
     */
    fun stopContainer() {
        if (postgresContainer.isRunning) {
            postgresContainer.stop()
            println("✅ Test PostgreSQL container stopped")
        }
    }
}
