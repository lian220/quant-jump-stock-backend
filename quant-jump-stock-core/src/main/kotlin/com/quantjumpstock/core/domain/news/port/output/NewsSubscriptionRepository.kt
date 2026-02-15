package com.quantjumpstock.core.domain.news.port.output

import com.quantjumpstock.core.domain.news.model.NewsSubscription

interface NewsSubscriptionRepository {
    fun findByUserAndTypeAndValueAndChannel(
        userId: Long, type: String, value: String, channel: String
    ): NewsSubscription?

    fun countActiveByUser(userId: Long): Long
    fun save(subscription: NewsSubscription): NewsSubscription
    fun findById(id: Long): NewsSubscription?
    fun findActiveByUser(userId: Long): List<NewsSubscription>
    fun findActiveByTypeAndValue(type: String, value: String): List<NewsSubscription>
}
