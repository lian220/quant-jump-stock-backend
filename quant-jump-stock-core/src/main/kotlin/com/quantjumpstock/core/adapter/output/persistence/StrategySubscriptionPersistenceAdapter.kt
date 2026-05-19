package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategySubscriptionEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategySubscriptionJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.SubscriptionStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.domain.model.backtest.UniverseType
import com.quantjumpstock.core.domain.port.output.AdminSubscriptionInfo
import com.quantjumpstock.core.domain.port.output.AdminSubscriptionPage
import com.quantjumpstock.core.domain.port.output.StrategySubscriptionRepository
import com.quantjumpstock.core.domain.port.output.StrategySubscriptionView
import com.quantjumpstock.core.domain.port.output.SubscribeResult
import com.quantjumpstock.core.domain.port.output.SubscriptionDetail
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class StrategySubscriptionPersistenceAdapter(
    private val jpaRepository: StrategySubscriptionJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val strategyJpaRepository: StrategyJpaRepository
) : StrategySubscriptionRepository {

    override fun findActiveByUserId(userId: Long): List<StrategySubscriptionView> {
        return jpaRepository
            .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
            .mapNotNull { entity ->
                val strategyId = entity.strategy.id ?: return@mapNotNull null
                StrategySubscriptionView(
                    strategyId = strategyId,
                    preferredUniverseType = entity.preferredUniverseType,
                    brokerAccountId = entity.brokerAccountId,
                )
            }
    }

    @Transactional
    override fun subscribe(userDbId: Long, strategyId: Long, universeType: UniverseType): SubscribeResult {
        // 이미 구독 중인지 확인
        val existing = jpaRepository.findByUserIdAndStrategyId(userDbId, strategyId).orElse(null)

        if (existing != null) {
            return when (existing.status) {
                SubscriptionStatus.ACTIVE -> SubscribeResult(
                    success = false,
                    subscriptionId = existing.id,
                    alreadySubscribed = true
                )
                SubscriptionStatus.CANCELLED, SubscriptionStatus.PAUSED -> {
                    // 재구독: 기존 레코드 재활성화
                    existing.status = SubscriptionStatus.ACTIVE
                    existing.cancelledAt = null
                    existing.preferredUniverseType = universeType
                    existing.notifySignals = true
                    existing.notifyRebalance = true
                    val saved = jpaRepository.save(existing)
                    strategyJpaRepository.incrementSubscriberCount(strategyId)
                    SubscribeResult(success = true, subscriptionId = saved.id, alreadySubscribed = false)
                }
            }
        }

        val userEntity = userJpaRepository.findById(userDbId).orElseThrow {
            IllegalArgumentException("사용자를 찾을 수 없습니다: $userDbId")
        }
        val strategyEntity = strategyJpaRepository.findById(strategyId).orElseThrow {
            IllegalArgumentException("전략을 찾을 수 없습니다: $strategyId")
        }

        val entity = StrategySubscriptionEntity(
            user = userEntity,
            strategy = strategyEntity,
            status = SubscriptionStatus.ACTIVE,
            notifySignals = true,
            notifyRebalance = true,
            preferredUniverseType = universeType
        )
        val saved = jpaRepository.save(entity)
        strategyJpaRepository.incrementSubscriberCount(strategyId)
        return SubscribeResult(success = true, subscriptionId = saved.id, alreadySubscribed = false)
    }

    @Transactional
    override fun unsubscribe(userDbId: Long, strategyId: Long): Boolean {
        val existing = jpaRepository.findByUserIdAndStrategyId(userDbId, strategyId).orElse(null)
            ?: return false

        if (existing.status != SubscriptionStatus.ACTIVE) return false

        jpaRepository.updateStatus(existing.id!!, SubscriptionStatus.CANCELLED, LocalDateTime.now())
        strategyJpaRepository.decrementSubscriberCount(strategyId)
        return true
    }

    override fun countActiveByUserId(userDbId: Long): Long {
        return jpaRepository.countActiveByUserId(userDbId)
    }

    override fun countActiveByUserIds(userDbIds: List<Long>): Map<Long, Long> {
        if (userDbIds.isEmpty()) return emptyMap()
        return jpaRepository.countActiveByUserIdIn(userDbIds).associate { row ->
            (row[0] as Long) to (row[1] as Long)
        }
    }

    override fun findDetailsByUserId(userDbId: Long): List<SubscriptionDetail> {
        return jpaRepository.findActiveSubscriptionsWithStrategy(userDbId).mapNotNull { entity ->
            toSubscriptionDetail(entity)
        }
    }

    override fun findByIdAndUserId(subscriptionId: Long, userDbId: Long): SubscriptionDetail? {
        val entity = jpaRepository.findById(subscriptionId).orElse(null) ?: return null
        if (entity.user.id != userDbId) return null
        if (entity.status != SubscriptionStatus.ACTIVE) return null
        return toSubscriptionDetail(entity)
    }

    @Transactional
    override fun updateAlertEnabled(subscriptionId: Long, alertEnabled: Boolean): Boolean {
        val entity = jpaRepository.findById(subscriptionId).orElse(null) ?: return false
        if (entity.status != SubscriptionStatus.ACTIVE) return false
        entity.notifySignals = alertEnabled
        entity.notifyRebalance = alertEnabled
        jpaRepository.save(entity)
        return true
    }

    @Transactional
    override fun updateBrokerAccountId(subscriptionId: Long, brokerAccountId: Long?): Boolean {
        val entity = jpaRepository.findById(subscriptionId).orElse(null) ?: return false
        if (entity.status != SubscriptionStatus.ACTIVE) return false
        entity.brokerAccountId = brokerAccountId
        jpaRepository.save(entity)
        return true
    }

    override fun existsActiveByUserIdAndStrategyId(userDbId: Long, strategyId: Long): Boolean {
        return jpaRepository.existsByUserIdAndStrategyIdAndStatus(userDbId, strategyId, SubscriptionStatus.ACTIVE)
    }

    override fun findActiveSubscriptionId(userDbId: Long, strategyId: Long): Long? {
        return jpaRepository.findByUserIdAndStrategyId(userDbId, strategyId)
            .orElse(null)
            ?.takeIf { it.status == SubscriptionStatus.ACTIVE }
            ?.id
    }

    override fun findActiveSubscriptionByBrokerAccountId(
        brokerAccountId: Long,
        excludeSubscriptionId: Long?,
    ): SubscriptionDetail? {
        return jpaRepository.findActiveByBrokerAccountId(brokerAccountId, excludeSubscriptionId)
            .firstNotNullOfOrNull { toSubscriptionDetail(it) }
    }

    override fun findAdminSubscriptions(strategyId: Long?, userId: Long?, page: Int, size: Int): AdminSubscriptionPage {
        val safeSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "subscribedAt"))

        val pagedResult = when {
            strategyId != null -> jpaRepository.findByStrategyId(strategyId, pageable)
            userId != null -> jpaRepository.findByUserId(userId, pageable)
            else -> jpaRepository.findByStatus(SubscriptionStatus.ACTIVE, pageable)
        }

        val items = pagedResult.content.map { entity ->
            AdminSubscriptionInfo(
                subscriptionId = entity.id ?: 0L,
                userDbId = entity.user.id ?: 0L,
                userLoginId = entity.user.userId,
                strategyId = entity.strategy.id ?: 0L,
                strategyName = entity.strategy.name,
                status = entity.status.name,
                alertEnabled = entity.notifySignals || entity.notifyRebalance,
                subscribedAt = entity.subscribedAt
            )
        }
        val activeCount = when {
            strategyId != null || userId != null -> items.count { it.status == "ACTIVE" }.toLong()
            else -> pagedResult.totalElements
        }

        return AdminSubscriptionPage(items = items, total = pagedResult.totalElements, activeCount = activeCount)
    }

    private fun toSubscriptionDetail(entity: StrategySubscriptionEntity): SubscriptionDetail? {
        val strategyId = entity.strategy.id ?: return null
        return SubscriptionDetail(
            subscriptionId = entity.id ?: return null,
            strategyId = strategyId,
            strategyName = entity.strategy.name,
            strategyDescription = entity.strategy.description,
            isPremiumStrategy = entity.strategy.isPremium,
            alertEnabled = entity.notifySignals || entity.notifyRebalance,
            preferredUniverseType = entity.preferredUniverseType,
            subscribedAt = entity.subscribedAt,
            brokerAccountId = entity.brokerAccountId,
        )
    }
}
