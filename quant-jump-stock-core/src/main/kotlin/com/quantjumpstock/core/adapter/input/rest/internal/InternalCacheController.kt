package com.quantjumpstock.core.adapter.input.rest.internal

import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/cache")
class InternalCacheController(
    private val cacheManager: CacheManager
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class EvictRequest(val cacheNames: List<String>)

    @PostMapping("/evict")
    fun evictCaches(@RequestBody request: EvictRequest): ResponseEntity<Map<String, Any>> {
        val evicted = mutableListOf<String>()
        val notFound = mutableListOf<String>()

        for (name in request.cacheNames) {
            val cache = cacheManager.getCache(name)
            if (cache != null) {
                cache.clear()
                evicted.add(name)
                log.info("Cache evicted: {}", name)
            } else {
                notFound.add(name)
                log.warn("Cache not found: {}", name)
            }
        }

        return ResponseEntity.ok(
            mapOf(
                "evicted" to evicted,
                "notFound" to notFound,
                "totalEvicted" to evicted.size
            )
        )
    }
}
