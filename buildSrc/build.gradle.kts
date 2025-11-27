plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // BOM platform doesn't work well in buildSrc, so we specify versions explicitly
    implementation("org.testcontainers:testcontainers-postgresql:2.0.2")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("org.liquibase:liquibase-core:5.0.1")
}
