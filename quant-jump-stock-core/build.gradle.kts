import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.5.10"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.11.4"
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    kotlin("plugin.jpa") version "2.1.21"
}

group = "com.quantjumpstock"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation(platform("com.google.cloud:spring-cloud-gcp-dependencies:7.4.4"))
    implementation("com.google.cloud:spring-cloud-gcp-starter-pubsub")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // PostgreSQL
    implementation("org.postgresql:postgresql:42.7.1")

    // Flyway for database migration
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Connection Pool (Spring Boot BOM 관리)
    implementation("com.zaxxer:HikariCP")

    // Environment variables
    implementation("io.github.cdimascio:java-dotenv:5.3.1")

    // Quartz Scheduler
    implementation("org.springframework.boot:spring-boot-starter-quartz")

    // SpringDoc OpenAPI (Swagger)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    // Database (H2 - CDS training에서 사용)
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")

    // Testing Frameworks
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.2.1")

    // Testcontainers
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:mongodb:1.19.3")
    testImplementation("org.testcontainers:gcloud:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")

    // Embedded MongoDB
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:4.11.0")
}

// ============================================
// GraalVM Native Image Configuration
// ============================================
graalvmNative {
    binaries {
        named("main") {
            imageName.set("quant-jump-stock-core")
            mainClass.set("com.quantjumpstock.core.QuantJumpStockCoreApplicationKt")
            buildArgs.addAll(
                "--no-fallback",
                "-H:+ReportExceptionStackTraces",
                "--initialize-at-run-time=sun.security.rsa"
            )
            // CI에서 -PnativeImageArgs="-J-Xmx12g" 전달 시 적용
            if (project.hasProperty("nativeImageArgs")) {
                val extraArgs = project.property("nativeImageArgs").toString()
                buildArgs.addAll(extraArgs.split(","))
            }
        }
    }
    binaries.all {
        buildArgs.add("--verbose")
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
