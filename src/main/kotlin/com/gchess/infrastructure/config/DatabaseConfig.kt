package com.gchess.infrastructure.config

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
import javax.sql.DataSource

/**
 * Configuration de la base de données PostgreSQL.
 *
 * Ce fichier centralise :
 * - La configuration du pool de connexions HikariCP
 * - La création du contexte jOOQ (DSLContext)
 * - L'exécution des migrations Liquibase
 */
object DatabaseConfig {

    /**
     * Crée et configure un DataSource HikariCP pour PostgreSQL.
     *
     * @param jdbcUrl URL JDBC de la base de données (par défaut depuis env var DATABASE_URL)
     * @param username Nom d'utilisateur (par défaut depuis env var DATABASE_USER)
     * @param password Mot de passe (par défaut depuis env var DATABASE_PASSWORD)
     * @param maximumPoolSize Taille maximale du pool de connexions (par défaut 10)
     * @return DataSource configuré avec HikariCP
     */
    fun createDataSource(
        jdbcUrl: String = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/gchess_dev",
        username: String = System.getenv("DATABASE_USER") ?: "gchess",
        password: String = System.getenv("DATABASE_PASSWORD") ?: "gchess",
        maximumPoolSize: Int = System.getenv("DATABASE_POOL_SIZE")?.toIntOrNull() ?: 10
    ): DataSource {
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            this.maximumPoolSize = maximumPoolSize
            this.driverClassName = "org.postgresql.Driver"

            // Optimisations pour PostgreSQL
            this.connectionTestQuery = "SELECT 1"
            this.isAutoCommit = true
            this.transactionIsolation = "TRANSACTION_READ_COMMITTED"

            // Timeouts
            this.connectionTimeout = 30000 // 30 secondes
            this.idleTimeout = 600000 // 10 minutes
            this.maxLifetime = 1800000 // 30 minutes

            // Pool configuration
            this.minimumIdle = 2
            this.poolName = "gChess-HikariCP"
        }

        return HikariDataSource(config)
    }

    /**
     * Crée un contexte jOOQ (DSLContext) à partir d'un DataSource.
     *
     * Le DSLContext est l'interface principale de jOOQ pour exécuter des requêtes SQL
     * de manière type-safe en Kotlin.
     *
     * @param dataSource Le DataSource à utiliser
     * @return DSLContext configuré pour PostgreSQL
     */
    fun createDslContext(dataSource: DataSource): DSLContext {
        return DSL.using(dataSource, SQLDialect.POSTGRES)
    }

    /**
     * Exécute les migrations Liquibase sur la base de données.
     *
     * Cette méthode charge le fichier changelog master et applique toutes les migrations
     * qui n'ont pas encore été exécutées. Liquibase garde un historique dans une table
     * DATABASECHANGELOG pour tracker les migrations déjà appliquées.
     *
     * @param dataSource Le DataSource vers la base de données à migrer
     * @throws Exception Si les migrations échouent
     */
    fun runMigrations(dataSource: DataSource) {
        println("🔄 Exécution des migrations Liquibase...")

        dataSource.connection.use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))

            val liquibase = Liquibase(
                "db/changelog/db.changelog-master.xml",
                ClassLoaderResourceAccessor(),
                database
            )

            liquibase.update(Contexts())
            println("✅ Migrations Liquibase exécutées avec succès")
        }
    }
}
