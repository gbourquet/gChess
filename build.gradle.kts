plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
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
}

// Configure dependencies for test source sets
configurations {
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
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-server-cors:2.3.7")
    implementation("io.ktor:ktor-server-websockets:2.3.7")

    // Koin for Dependency Injection
    implementation("io.insert-koin:koin-ktor:3.5.3")
    implementation("io.insert-koin:koin-logger-slf4j:3.5.3")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

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

// Make check task run both test types
tasks.named("check") {
    dependsOn(unitTest, architectureTest)
}

// Disable default test task since we have custom ones
tasks.named("test") {
    enabled = false
}

kotlin {
    jvmToolchain(21)
}
