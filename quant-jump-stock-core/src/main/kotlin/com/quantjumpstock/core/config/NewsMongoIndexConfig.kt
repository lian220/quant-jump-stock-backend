package com.quantjumpstock.core.config

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.context.annotation.Configuration

@Configuration
class NewsMongoIndexConfig(
    private val mongoTemplate: MongoTemplate
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun createIndexes() {
        try {
            val news = mongoTemplate.getCollection("news")
            news.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("source"),
                    Indexes.ascending("external_id")
                ),
                IndexOptions().unique(true).background(true)
            )
            news.createIndex(
                Indexes.descending("created_at"),
                IndexOptions().background(true)
            )
            news.createIndex(
                Indexes.compoundIndex(
                    Indexes.descending("importance_score"),
                    Indexes.descending("created_at")
                ),
                IndexOptions().background(true)
            )
            news.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("tickers"),
                    Indexes.descending("created_at")
                ),
                IndexOptions().background(true)
            )
            news.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("tags"),
                    Indexes.descending("created_at")
                ),
                IndexOptions().background(true)
            )

            val state = mongoTemplate.getCollection("news_collector_state")
            state.createIndex(
                Indexes.ascending("source"),
                IndexOptions().unique(true).background(true)
            )

            logger.info("뉴스 MongoDB 인덱스 생성 완료")
        } catch (e: Exception) {
            logger.warn("뉴스 MongoDB 인덱스 생성 중 오류 (이미 존재할 수 있음)", e)
        }
    }
}
