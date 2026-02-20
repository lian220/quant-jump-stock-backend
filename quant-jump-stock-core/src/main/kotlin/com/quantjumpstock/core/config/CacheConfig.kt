package com.quantjumpstock.core.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val simpleCacheManager = SimpleCacheManager()
        simpleCacheManager.setCaches(
            listOf(
                buildCache("benchmark", 1, TimeUnit.HOURS, 100),
                buildCache("buySignals", 6, TimeUnit.HOURS, 50),
                buildCache("categories", 24, TimeUnit.HOURS, 10),
                buildCache("marketplaceStrategies", 6, TimeUnit.HOURS, 500),
                buildCache("strategyDetail", 6, TimeUnit.HOURS, 200),
                buildCache("recentPredictions", 6, TimeUnit.HOURS, 20),
                buildCache("tickerPredictions", 6, TimeUnit.HOURS, 200),
            )
        )
        return simpleCacheManager
    }

    private fun buildCache(name: String, duration: Long, unit: TimeUnit, maxSize: Long): CaffeineCache {
        val cache = Caffeine.newBuilder()
            .expireAfterWrite(duration, unit)
            .maximumSize(maxSize)
            .build<Any, Any>()
        return CaffeineCache(name, cache)
    }
}
