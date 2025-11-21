plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
    idea
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
    // Ktor Server
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-server-cors:2.3.7")
    implementation("io.ktor:ktor-server-websockets:2.3.7")

    // Authentication
    implementation("io.ktor:ktor-server-auth:2.3.7")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.7")

    // OpenAPI / Swagger Documentation
    implementation("io.bkbn:kompendium-core:3.14.4")
    implementation("io.ktor:ktor-server-swagger:2.3.7")

    // Koin for Dependency Injection
    implementation("io.insert-koin:koin-ktor:3.5.3")
    implementation("io.insert-koin:koin-logger-slf4j:3.5.3")

    // ULID for unique identifiers
    implementation("de.huxhorn.sulky:de.huxhorn.sulky.ulid:8.3.0")

    // Date/Time (kotlinx-datetime for multiplatform support)
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // Password hashing (BCrypt)
    implementation("org.mindrot:jbcrypt:0.4")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Database - PostgreSQL
    implementation("org.postgresql:postgresql:42.7.3")

    // Database - jOOQ (type-safe SQL)
    implementation("org.jooq:jooq:3.19.6")
    implementation("org.jooq:jooq-kotlin:3.19.6")

    // Database - Connection Pooling
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Database - Migrations
    implementation("org.liquibase:liquibase-core:4.26.0")

    // Unit Testing
    "unitTestImplementation"("io.ktor:ktor-server-test-host:2.3.7")
    "unitTestImplementation"("io.kotest:kotest-runner-junit5:5.8.0")
    "unitTestImplementation"("io.kotest:kotest-assertions-core:5.8.0")
    "unitTestImplementation"("io.insert-koin:koin-test:3.5.3")
    "unitTestImplementation"("io.insert-koin:koin-test-junit5:3.5.3")

    // Architecture Testing
    "architectureTestImplementation"("com.tngtech.archunit:archunit-junit5:1.2.1")
    "architectureTestImplementation"("org.junit.jupiter:junit-jupiter-api:5.10.1")
    "architectureTestRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.10.1")

    // Integration Testing (E2E)
    "integrationTestImplementation"("io.ktor:ktor-server-test-host:2.3.7")
    "integrationTestImplementation"("io.kotest:kotest-runner-junit5:5.8.0")
    "integrationTestImplementation"("io.kotest:kotest-assertions-core:5.8.0")
    "integrationTestImplementation"("io.insert-koin:koin-test:3.5.3")
    "integrationTestImplementation"("io.insert-koin:koin-test-junit5:3.5.3")

    // Testcontainers for integration tests
    "integrationTestImplementation"("org.testcontainers:testcontainers:1.19.7")
    "integrationTestImplementation"("org.testcontainers:postgresql:1.19.7")

    // jOOQ code generation dependencies (uses Testcontainers)
    "jooqGenerator"("org.jooq:jooq-codegen:3.19.6")
    "jooqGenerator"("org.jooq:jooq-meta:3.19.6")
    "jooqGenerator"("org.testcontainers:postgresql:1.19.7")
    "jooqGenerator"("org.postgresql:postgresql:42.7.3")
    "jooqGenerator"("org.liquibase:liquibase-core:4.26.0")

    // Documentation Generation
    "docGenImplementation"("io.ktor:ktor-server-test-host:2.3.7")
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
// jOOQ Code Generation with Testcontainers
// ============================================

// Créer la tâche de setup de la base de données pour jOOQ
val setupJooqDatabase by tasks.registering(JooqTestcontainersTask::class)

// Tâche personnalisée pour générer le code jOOQ
val generateJooq by tasks.registering(JavaExec::class) {
    group = "jooq"
    description = "Génère le code jOOQ depuis PostgreSQL (via Testcontainers)"

    dependsOn(setupJooqDatabase)

    // Classpath pour jOOQ codegen
    classpath = configurations["jooqGenerator"]

    mainClass.set("org.jooq.codegen.GenerationTool")

    // Le fichier de configuration sera créé par setupJooqDatabase
    args = listOf(
        project.provider {
            if (project.extensions.extraProperties.has("jooqConfigFile")) {
                project.extensions.extraProperties["jooqConfigFile"] as String
            } else {
                project.layout.buildDirectory.dir("jooq-config/jooq-config.xml").get().asFile.absolutePath
            }
        }.get()
    )

    // S'assurer que le répertoire de sortie existe
    doFirst {
        project.layout.buildDirectory.dir("generated-src/jooq/main").get().asFile.mkdirs()
    }
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
