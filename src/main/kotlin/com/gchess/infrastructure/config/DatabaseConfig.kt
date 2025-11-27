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

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Scope
import liquibase.command.CommandScope
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
 *
 * La configuration est chargée depuis application.conf via Typesafe Config.
 * Les valeurs peuvent être surchargées par des variables d'environnement.
 */
object DatabaseConfig {

    /**
     * Charge la configuration depuis application.conf (ou application-test.conf pour les tests).
     * Typesafe Config gère automatiquement la fusion avec les variables d'environnement.
     */
    private val config: Config = ConfigFactory.load()

    /**
     * Crée et configure un DataSource HikariCP pour PostgreSQL.
     *
     * La configuration est lue depuis application.conf :
     * - database.url (surchargeable par DATABASE_URL)
     * - database.user (surchargeable par DATABASE_USER)
     * - database.password (surchargeable par DATABASE_PASSWORD)
     * - database.poolSize (surchargeable par DATABASE_POOL_SIZE)
     *
     * @param customConfig Configuration optionnelle pour surcharger (utilisé par les tests)
     * @return DataSource configuré avec HikariCP
     */
    fun createDataSource(customConfig: Config? = null): DataSource {
        val cfg = customConfig ?: config

        val jdbcUrl = cfg.getString("database.url")
        val username = cfg.getString("database.user")
        val password = cfg.getString("database.password")
        val maximumPoolSize = cfg.getInt("database.poolSize")

        val hikariConfig = HikariConfig().apply {
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

        return HikariDataSource(hikariConfig)
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

            // On définit le ResourceAccessor dans un Scope temporaire
            Scope.child(Scope.Attr.resourceAccessor, ClassLoaderResourceAccessor()) {

                CommandScope("update").apply {

                    // 1. Utilisation de la clé String "database" (Stable)
                    addArgumentValue("database", database)

                    // 2. Utilisation de la clé String "changelogFile" (Stable)
                    addArgumentValue("changelogFile", "db/changelog/db.changelog-master.xml")

                    // 3. Contexts et Labels
                    addArgumentValue("contexts", Contexts().toString())
                    addArgumentValue(
                        "labelFilter",
                        LabelExpression().originalString
                    ).execute() // Exécution de la commande
                }.execute()

                println("✅ Migrations Liquibase exécutées avec succès")
            }
        }
    }
}
