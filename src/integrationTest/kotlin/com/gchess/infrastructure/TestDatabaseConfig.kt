package com.gchess.infrastructure

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.jooq.DSLContext
import org.koin.core.context.GlobalContext
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Test database configuration using Testcontainers.
 *
 * This configuration:
 * - Starts a singleton PostgreSQL container (shared across all tests)
 * - Configures Typesafe Config with Testcontainers connection parameters
 * - Allows production DatabaseConfig to create DataSource/DSLContext from the test config
 *
 * The container is started once and reused across all integration tests.
 */
object TestDatabaseConfig {

    /**
     * Singleton PostgreSQL container.
     * Started once and reused across all tests.
     */
    private val postgresContainer: PostgreSQLContainer by lazy {
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
     * Creates a Typesafe Config with Testcontainers database parameters.
     *
     * This config overrides the database.* properties with Testcontainers values.
     * The production DatabaseConfig.createDataSource() can then use this config.
     *
     * @return Config with Testcontainers database parameters
     */
    fun createTestConfig(): Config {
        // Ensure container is started
        postgresContainer

        // Load base config from application-test.conf
        val baseConfig = ConfigFactory.load("application-test")

        // Override database parameters with Testcontainers values
        return ConfigFactory.empty()
            .withValue("database.url", ConfigValueFactory.fromAnyRef(postgresContainer.jdbcUrl))
            .withValue("database.user", ConfigValueFactory.fromAnyRef(postgresContainer.username))
            .withValue("database.password", ConfigValueFactory.fromAnyRef(postgresContainer.password))
            .withValue("database.poolSize", ConfigValueFactory.fromAnyRef(5))
            .withFallback(baseConfig)
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
