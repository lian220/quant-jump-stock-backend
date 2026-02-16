package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserNewsSubscriptionJpaRepository : JpaRepository<UserNewsSubscriptionEntity, Long> {

    fun findByUserIdAndIsActiveTrue(userId: Long): List<UserNewsSubscriptionEntity>

    fun findBySubscriptionTypeAndSubscriptionValueAndIsActiveTrue(
        subscriptionType: String,
        subscriptionValue: String
    ): List<UserNewsSubscriptionEntity>

    fun findByUserIdAndSubscriptionTypeAndSubscriptionValueAndNotifyChannel(
        userId: Long,
        subscriptionType: String,
        subscriptionValue: String,
        notifyChannel: String
    ): UserNewsSubscriptionEntity?

    fun countByUserIdAndIsActiveTrue(userId: Long): Long
}
