package com.quantjumpstock.core

import io.github.cdimascio.dotenv.Dotenv
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.slf4j.LoggerFactory
import java.io.File

@SpringBootApplication
class QuantJumpStockCoreApplication

private val logger = LoggerFactory.getLogger(QuantJumpStockCoreApplication::class.java)

fun main(args: Array<String>) {
    logger.info("Starting Quant Jump Stock Core Application...")

    // Load .env from project root (parent directory) for local development
    // Docker Compose environments will use env_file directly
    if (System.getenv("DOCKER_CONTAINER") == null) {
        try {
            val currentDir = File(System.getProperty("user.dir"))
            val projectRoot = currentDir.parentFile ?: currentDir

            // Determine which .env file to load based on profile
            val profile = System.getenv("SPRING_PROFILES_ACTIVE") ?: "local"
            val envFileName = when (profile) {
                "prod" -> ".env.prod"
                else -> ".env.local"
            }

            logger.info("Loading environment from: ${projectRoot.absolutePath}/$envFileName")

            val dotenv = Dotenv.configure()
                .directory(projectRoot.absolutePath)
                .filename(envFileName)
                .ignoreIfMissing()
                .load()

            // Export to System properties for Spring Boot
            var loadedCount = 0
            dotenv.entries().forEach { entry ->
                System.setProperty(entry.key, entry.value)
                loadedCount++
            }

            logger.info("✅ Loaded $loadedCount environment variables from $envFileName")
        } catch (e: Exception) {
            logger.warn("⚠️ Failed to load .env file: ${e.message}. Using system environment variables only.")
        }
    } else {
        logger.info("Running in Docker container, using Docker environment variables")
    }

    runApplication<QuantJumpStockCoreApplication>(*args)
}
