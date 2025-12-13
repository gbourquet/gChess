plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
    idea
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.gchess"
version = "1.0.0"

repositories {
    mavenCentral()
}

// Configure source sets for different test types
sourceSets {
    // Unit tests
    create("unitTest") {
        kotlin.srcDir("src/unitTest/kotlin")
        resources.srcDir("src/unitTest/resources")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

    // Architecture tests
    create("architectureTest") {
        kotlin.srcDir("src/architectureTest/kotlin")
        resources.srcDir("src/architectureTest/resources")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

    // Integration tests (E2E)
    create("integrationTest") {
        kotlin.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

    // Documentation generation
    create("docGen") {
        kotlin.srcDir("src/docGen/kotlin")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

// Configure dependencies for test source sets
configurations {
    // Configuration pour la génération de code jOOQ
    create("jooqGenerator")

    getByName("unitTestImplementation") {
        extendsFrom(configurations.implementation.get())
    }
    getByName("unitTestRuntimeOnly") {
        extendsFrom(configurations.runtimeOnly.get())
    }
    getByName("architectureTestImplementation") {
        extendsFrom(configurations.implementation.get())
    }
    getByName("architectureTestRuntimeOnly") {
        extendsFrom(configurations.runtimeOnly.get())
    }
    getByName("integrationTestImplementation") {
        extendsFrom(configurations.implementation.get())
    }
    getByName("integrationTestRuntimeOnly") {
        extendsFrom(configurations.runtimeOnly.get())
    }
    getByName("docGenImplementation") {
        extendsFrom(configurations.implementation.get())
    }
    getByName("docGenRuntimeOnly") {
        extendsFrom(configurations.runtimeOnly.get())
    }
}

dependencies {
    // Testcontainers BOM (Bill of Materials) for version management
    implementation(platform("org.testcontainers:testcontainers-bom:2.0.2"))

    // JUnit BOM (Bill of Materials) for version management
    implementation(platform("org.junit:junit-bom:5.11.4"))

    // Ktor Server
    implementation("io.ktor:ktor-server-core:3.3.2")
    implementation("io.ktor:ktor-server-netty:3.3.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.3.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.2")
    implementation("io.ktor:ktor-server-cors:3.3.2")
    implementation("io.ktor:ktor-server-websockets:3.3.2")

    // Authentication
    implementation("io.ktor:ktor-server-auth:3.3.2")
    implementation("io.ktor:ktor-server-auth-jwt:3.3.2")

    // OpenAPI / Swagger Documentation
    implementation("io.bkbn:kompendium-core:4.0.3")
    implementation("io.ktor:ktor-server-swagger:3.3.2")

    // Koin for Dependency Injection
    implementation("io.insert-koin:koin-ktor:4.1.0")
    implementation("io.insert-koin:koin-logger-slf4j:4.1.0")

    // ULID for unique identifiers
    implementation("de.huxhorn.sulky:de.huxhorn.sulky.ulid:8.3.0")

    // Date/Time - Using kotlin.time (built into Kotlin 2.1+)
    // Note: kotlin.time.Instant and kotlin.time.Clock are now in stdlib, no dependency needed

    // Password hashing (BCrypt)
    implementation("org.mindrot:jbcrypt:0.4")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.21")

    // Configuration
    implementation("com.typesafe:config:1.4.3")

    // Database - PostgreSQL
    implementation("org.postgresql:postgresql:42.7.8")

    // Database - jOOQ (type-safe SQL)
    implementation("org.jooq:jooq:3.20.8")
    implementation("org.jooq:jooq-kotlin:3.20.8")

    // Database - Connection Pooling
    implementation("com.zaxxer:HikariCP:7.0.2")

    // Database - Migrations
    implementation("org.liquibase:liquibase-core:5.0.1")

    // Unit Testing
    "unitTestImplementation"("io.ktor:ktor-server-test-host:3.3.3")
    "unitTestImplementation"("io.kotest:kotest-runner-junit5:6.0.5")
    "unitTestImplementation"("io.kotest:kotest-assertions-core:6.0.5")
    "unitTestImplementation"("io.insert-koin:koin-test:4.1.0")
    "unitTestImplementation"("io.insert-koin:koin-test-junit5:4.1.0")
    "unitTestImplementation"("io.mockk:mockk:1.14.6")

    // Architecture Testing
    "architectureTestImplementation"("com.tngtech.archunit:archunit-junit5:1.4.1")
    "architectureTestImplementation"("org.junit.jupiter:junit-jupiter-api") // version from BOM
    "architectureTestRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine") // version from BOM
    "architectureTestRuntimeOnly"("org.junit.platform:junit-platform-launcher") // Explicitly add launcher

    // Integration Testing (E2E)
    "integrationTestImplementation"("io.ktor:ktor-server-test-host:3.3.3")
    "integrationTestImplementation"("io.kotest:kotest-runner-junit5:6.0.5")
    "integrationTestImplementation"("io.kotest:kotest-assertions-core:6.0.5")
    "integrationTestImplementation"("io.insert-koin:koin-test:4.1.0")
    "integrationTestImplementation"("io.insert-koin:koin-test-junit5:4.1.0")

    // Testcontainers for integration tests (versions managed by BOM)
    "integrationTestImplementation"("org.testcontainers:testcontainers")
    "integrationTestImplementation"("org.testcontainers:testcontainers-postgresql")

    // jOOQ code generation dependencies (uses Testcontainers)
    "jooqGenerator"("org.jooq:jooq-codegen:3.20.8")
    "jooqGenerator"("org.jooq:jooq-meta:3.20.8")
    "jooqGenerator"("org.testcontainers:testcontainers-postgresql:2.0.2") // BOM doesn't work for custom configs
    "jooqGenerator"("org.postgresql:postgresql:42.7.8")
    "jooqGenerator"("org.liquibase:liquibase-core:5.0.1")

    // Documentation Generation
    "docGenImplementation"("io.ktor:ktor-server-test-host:3.3.3")
}

application {
    mainClass.set("com.gchess.ApplicationKt")
}

// Unit test task
val unitTest = tasks.register<Test>("unitTest") {
    description = "Runs unit tests"
    group = "verification"

    testClassesDirs = sourceSets["unitTest"].output.classesDirs
    classpath = sourceSets["unitTest"].runtimeClasspath

    useJUnitPlatform()
}

// Architecture test task
val architectureTest = tasks.register<Test>("architectureTest") {
    description = "Runs architecture tests"
    group = "verification"

    testClassesDirs = sourceSets["architectureTest"].output.classesDirs
    classpath = sourceSets["architectureTest"].runtimeClasspath

    useJUnitPlatform()

    shouldRunAfter(unitTest)
}

// Configure processIntegrationTestResources to handle duplicate resources
tasks.named<ProcessResources>("processIntegrationTestResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// Integration test task
val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (E2E)"
    group = "verification"

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath

    useJUnitPlatform()

    shouldRunAfter(unitTest, architectureTest)
}

// Make check task run all test types
tasks.named("check") {
    dependsOn(unitTest, architectureTest, integrationTest)
}

// Disable default test task since we have custom ones
tasks.named("test") {
    enabled = false
}

kotlin {
    jvmToolchain(21)
}

// ============================================
// Shadow JAR Configuration
// ============================================
// Configure Shadow plugin to create fat JAR for Docker deployment
tasks.shadowJar {
    archiveBaseName.set("gChess")
    archiveClassifier.set("all")
    archiveVersion.set(project.version.toString())
    mergeServiceFiles()
}

// Make build depend on shadowJar instead of jar
tasks.named("build") {
    dependsOn(tasks.shadowJar)
}

// ============================================
// jOOQ Code Generation with Testcontainers
// ============================================

// Créer la tâche de setup de la base de données pour jOOQ
val setupJooqDatabase by tasks.registering(JooqTestcontainersTask::class) {
    inputs.files(fileTree("src/main/resources/db/changelog")) // surveille les migrations
    outputs.dir(layout.buildDirectory.dir("generated-src/jooq/main")) // répertoire de sortie
}

// Tâche personnalisée pour générer le code jOOQ
val generateJooq by tasks.registering(JavaExec::class) {
    group = "jooq"
    description = "Génère le code jOOQ depuis PostgreSQL (via Testcontainers)"

    dependsOn(setupJooqDatabase)

    // Classpath pour jOOQ codegen
    classpath = configurations["jooqGenerator"]

    mainClass.set("org.jooq.codegen.GenerationTool")

    // Le fichier de configuration sera créé par setupJooqDatabase
    val jooqConfigFile = project.provider {
            if (project.extensions.extraProperties.has("jooqConfigFile")) {
                project.extensions.extraProperties["jooqConfigFile"] as String
            } else {
                project.layout.buildDirectory.dir("jooq-config/jooq-config.xml").get().asFile.absolutePath
            }
        }

    args = listOf(jooqConfigFile.get())

    // S'assurer que le répertoire de sortie existe
    doFirst {
        project.layout.buildDirectory.dir("generated-src/jooq/main").get().asFile.mkdirs()
    }

    inputs.files(fileTree("src/main/resources/db/changelog")) // surveille les migrations
    inputs.file(jooqConfigFile)                     // surveille le fichier jooq-config.xml
    outputs.dir(layout.buildDirectory.dir("generated-src/jooq/main")) // répertoire de sortie

}

// Ajouter les sources générées au sourceSet principal
sourceSets.main {
    java.srcDir(layout.buildDirectory.dir("generated-src/jooq/main"))
}

// Faire en sorte que compileKotlin dépende de la génération jOOQ
tasks.named("compileKotlin") {
    dependsOn(generateJooq)
}

// ============================================
// Documentation Tasks
// ============================================

// Task to generate OpenApiConfig.kt with synced version
val generateOpenApiConfig = tasks.register("generateOpenApiConfig") {
    description = "Generates OpenApiConfig.kt from openapi-config.json with version from build.gradle.kts"
    group = "documentation"

    val configFile = file("$projectDir/src/main/resources/openapi-config.json")
    val outputFile = file("$projectDir/src/main/kotlin/com/gchess/infrastructure/config/OpenApiConfig.kt")

    inputs.file(configFile)
    inputs.property("version", version)
    outputs.file(outputFile)

    doLast {
        // Parse JSON configuration
        val jsonSlurper = groovy.json.JsonSlurper()
        val config = jsonSlurper.parse(configFile) as Map<*, *>

        val title = config["title"] as String
        val description = config["description"] as String
        val contact = config["contact"] as Map<*, *>
        val servers = config["servers"] as Map<*, *>
        val localServer = servers["local"] as Map<*, *>

        outputFile.writeText("""
package com.gchess.infrastructure.config

/**
 * OpenAPI documentation configuration.
 * Centralizes all metadata for API documentation generation.
 *
 * NOTE: This file is auto-generated by the 'generateOpenApiConfig' Gradle task.
 * - Configuration is loaded from src/main/resources/openapi-config.json
 * - VERSION is synchronized with build.gradle.kts
 *
 * To modify: Edit openapi-config.json or change version in build.gradle.kts
 */
object OpenApiConfig {
    const val TITLE = "$title"
    const val VERSION = "$version" // Auto-synced from build.gradle.kts

    const val DESCRIPTION = ""${'"'}${description}""${'"'}

    object Contact {
        const val NAME = "${contact["name"]}"
        const val EMAIL = "${contact["email"]}"
    }

    object Server {
        const val LOCAL_URL = "${localServer["url"]}"
        const val LOCAL_DESCRIPTION = "${localServer["description"]}"
    }
}
        """.trimIndent())

        println("Generated OpenApiConfig.kt with version: $version")
        println("  - Loaded configuration from: ${configFile.name}")
    }
}

// Generate OpenApiConfig before compilation
tasks.named("compileKotlin") {
    dependsOn(generateOpenApiConfig)
}

// Task to generate OpenAPI spec file
val generateOpenApiSpec = tasks.register<JavaExec>("generateOpenApiSpec") {
    description = "Generates the OpenAPI specification JSON file into src/main/resources/openapi/"
    group = "documentation"

    val outputDir = file("$projectDir/src/main/resources/openapi")
    val outputFile = file("$outputDir/openapi.json")

    // Create output directory
    doFirst {
        outputDir.mkdirs()
    }

    // Configure JavaExec
    mainClass.set("com.gchess.GenerateOpenApiSpecKt")
    classpath = sourceSets["docGen"].runtimeClasspath
    args(outputFile.absolutePath)

    // Dependencies - only depends on compilation
    dependsOn(tasks.named("compileKotlin"))
    dependsOn(tasks.named("compileDocGenKotlin"))

    outputs.file(outputFile)
    outputs.upToDateWhen { false } // Always regenerate
}

// Run OpenAPI generation before integration tests to ensure it's up to date
tasks.named("integrationTest") {
    dependsOn(generateOpenApiSpec)
}

// Configure IntelliJ IDEA to recognize custom test source sets
idea {
    module {
        testSources.from(
            sourceSets["unitTest"].kotlin.srcDirs,
            sourceSets["architectureTest"].kotlin.srcDirs,
            sourceSets["integrationTest"].kotlin.srcDirs
        )
    }
}
