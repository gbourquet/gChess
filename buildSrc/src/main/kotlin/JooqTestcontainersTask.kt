import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import java.sql.DriverManager

/**
 * Tâche Gradle personnalisée pour configurer une base de données PostgreSQL temporaire
 * via Testcontainers, puis préparer la génération du code jOOQ.
 *
 * Cette tâche :
 * 1. Démarre un conteneur PostgreSQL via Testcontainers
 * 2. Exécute les migrations Liquibase pour créer le schéma
 * 3. Crée un fichier de configuration jOOQ avec les credentials de connexion
 * 4. Configure un shutdown hook pour arrêter le conteneur proprement
 */
open class JooqTestcontainersTask : DefaultTask() {

    @TaskAction
    fun setupDatabase() {
        logger.lifecycle("🚀 Démarrage du conteneur PostgreSQL pour génération jOOQ...")

        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("gchess_codegen")
            .withUsername("gchess")
            .withPassword("gchess")

        postgres.start()

        try {
            logger.lifecycle("✅ PostgreSQL container démarré: ${postgres.jdbcUrl}")

            // Exécuter les migrations Liquibase
            runMigrations(postgres.jdbcUrl, postgres.username, postgres.password)

            // Créer le fichier de configuration jOOQ avec les bonnes credentials
            createJooqConfigFile(postgres.jdbcUrl, postgres.username, postgres.password)

            logger.lifecycle("✅ Migrations Liquibase exécutées avec succès")
            logger.lifecycle("✅ Fichier de configuration jOOQ créé")
            logger.lifecycle("⏳ jOOQ va maintenant se connecter à: ${postgres.jdbcUrl}")

            // Shutdown hook pour arrêter le conteneur proprement
            Runtime.getRuntime().addShutdownHook(Thread {
                logger.lifecycle("🛑 Arrêt du conteneur PostgreSQL...")
                postgres.stop()
                logger.lifecycle("✅ Conteneur PostgreSQL arrêté")
            })

        } catch (e: Exception) {
            logger.error("❌ Erreur lors de la configuration de la base de données : $e")
            postgres.stop()
            throw e
        }
    }

    private fun createJooqConfigFile(jdbcUrl: String, username: String, password: String) {
        val configDir = project.layout.buildDirectory.dir("jooq-config").get().asFile
        configDir.mkdirs()

        val configFile = configDir.resolve("jooq-config.xml")

        val configXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <configuration xmlns="http://www.jooq.org/xsd/jooq-codegen-3.19.0.xsd">
                <jdbc>
                    <driver>org.postgresql.Driver</driver>
                    <url>$jdbcUrl</url>
                    <user>$username</user>
                    <password>$password</password>
                </jdbc>
                <generator>
                    <name>org.jooq.codegen.KotlinGenerator</name>
                    <database>
                        <name>org.jooq.meta.postgres.PostgresDatabase</name>
                        <inputSchema>public</inputSchema>
                    </database>
                    <generate>
                        <deprecated>false</deprecated>
                        <records>true</records>
                        <immutablePojos>true</immutablePojos>
                        <fluentSetters>true</fluentSetters>
                        <kotlinNotNullPojoAttributes>true</kotlinNotNullPojoAttributes>
                    </generate>
                    <target>
                        <packageName>com.gchess.infrastructure.persistence.jooq</packageName>
                        <directory>${project.layout.buildDirectory.dir("generated-src/jooq/main").get().asFile.absolutePath}</directory>
                    </target>
                </generator>
            </configuration>
        """.trimIndent()

        configFile.writeText(configXml)

        // Stocker le chemin du fichier de config pour que jOOQ puisse le trouver
        project.extensions.extraProperties["jooqConfigFile"] = configFile.absolutePath
    }

    private fun runMigrations(jdbcUrl: String, username: String, password: String) {
        logger.lifecycle("🔄 Exécution des migrations Liquibase...")

        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))

            // Utiliser DirectoryResourceAccessor pointant vers src/main/resources du projet
            val resourcesDir = project.projectDir.resolve("src/main/resources")
            val resourceAccessor = liquibase.resource.DirectoryResourceAccessor(resourcesDir)

            val liquibase = Liquibase(
                "db/changelog/db.changelog-master.xml",
                resourceAccessor,
                database
            )

            liquibase.update(Contexts())
        }
    }
}
