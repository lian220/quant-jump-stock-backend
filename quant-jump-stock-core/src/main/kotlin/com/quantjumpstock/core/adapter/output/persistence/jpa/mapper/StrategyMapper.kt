package com.quantjumpstock.core.adapter.output.persistence.jpa.mapper

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyCategoryEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyStatus as JpaStrategyStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.RebalanceFrequency as JpaRebalanceFrequency
import com.quantjumpstock.core.domain.model.strategy.Strategy
import com.quantjumpstock.core.domain.model.strategy.StrategyStatus
import com.quantjumpstock.core.domain.model.strategy.RebalanceFrequency
import org.springframework.stereotype.Component

/**
 * Strategy Mapper - JPA Entity ↔ Domain Model 변환
 *
 * 특징:
 * - Component로 등록하여 DI 사용
 * - 양방향 매핑 제공
 * - 연관 엔티티는 ID만 매핑 (Lazy Loading 방지)
 */
@Component
class StrategyMapper {

    /**
     * JPA Entity → Domain Model
     */
    fun toDomain(entity: StrategyEntity): Strategy {
        return Strategy(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            categoryId = entity.category.id ?: throw IllegalStateException("Category ID is null"),
            ownerId = entity.owner?.id,
            isPublic = entity.isPublic,
            isPremium = entity.isPremium,
            status = mapStatus(entity.status),
            conditions = entity.conditions,
            riskSettings = entity.riskSettings,
            positionSizing = entity.positionSizing,
            tradingCosts = entity.tradingCosts,
            rebalanceFrequency = mapRebalanceFrequency(entity.rebalanceFrequency),
            subscriberCount = entity.subscriberCount,
            averageRating = entity.averageRating,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Domain Model → JPA Entity (새 엔티티 생성용)
     *
     * @param domain 도메인 모델
     * @param category 연관된 카테고리 엔티티 (이미 조회된 상태)
     * @param owner 연관된 사용자 엔티티 (이미 조회된 상태, nullable)
     */
    fun toEntity(
        domain: Strategy,
        category: StrategyCategoryEntity,
        owner: com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity? = null
    ): StrategyEntity {
        return StrategyEntity(
            id = domain.id,
            name = domain.name,
            description = domain.description,
            category = category,
            owner = owner,
            isPublic = domain.isPublic,
            isPremium = domain.isPremium,
            status = mapStatusToJpa(domain.status),
            conditions = domain.conditions,
            riskSettings = domain.riskSettings,
            positionSizing = domain.positionSizing,
            tradingCosts = domain.tradingCosts,
            rebalanceFrequency = mapRebalanceFrequencyToJpa(domain.rebalanceFrequency),
            subscriberCount = domain.subscriberCount,
            averageRating = domain.averageRating,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    /**
     * 기존 Entity 업데이트용 (연관관계 유지)
     */
    fun updateEntity(entity: StrategyEntity, domain: Strategy): StrategyEntity {
        entity.name = domain.name
        entity.description = domain.description
        entity.isPublic = domain.isPublic
        entity.isPremium = domain.isPremium
        entity.status = mapStatusToJpa(domain.status)
        entity.conditions = domain.conditions
        entity.riskSettings = domain.riskSettings
        entity.positionSizing = domain.positionSizing
        entity.tradingCosts = domain.tradingCosts
        entity.rebalanceFrequency = mapRebalanceFrequencyToJpa(domain.rebalanceFrequency)
        entity.subscriberCount = domain.subscriberCount
        entity.averageRating = domain.averageRating
        entity.updatedAt = domain.updatedAt
        return entity
    }

    /**
     * JPA StrategyStatus → Domain StrategyStatus
     */
    private fun mapStatus(jpaStatus: JpaStrategyStatus): StrategyStatus {
        return when (jpaStatus) {
            JpaStrategyStatus.DRAFT -> StrategyStatus.DRAFT
            JpaStrategyStatus.PENDING_REVIEW -> StrategyStatus.PENDING_REVIEW
            JpaStrategyStatus.APPROVED -> StrategyStatus.APPROVED
            JpaStrategyStatus.PUBLISHED -> StrategyStatus.PUBLISHED
            JpaStrategyStatus.REJECTED -> StrategyStatus.REJECTED
            JpaStrategyStatus.ACTIVE -> StrategyStatus.ACTIVE
            JpaStrategyStatus.ARCHIVED -> StrategyStatus.ARCHIVED
        }
    }

    /**
     * Domain StrategyStatus → JPA StrategyStatus
     */
    private fun mapStatusToJpa(domainStatus: StrategyStatus): JpaStrategyStatus {
        return when (domainStatus) {
            StrategyStatus.DRAFT -> JpaStrategyStatus.DRAFT
            StrategyStatus.PENDING_REVIEW -> JpaStrategyStatus.PENDING_REVIEW
            StrategyStatus.APPROVED -> JpaStrategyStatus.APPROVED
            StrategyStatus.PUBLISHED -> JpaStrategyStatus.PUBLISHED
            StrategyStatus.REJECTED -> JpaStrategyStatus.REJECTED
            StrategyStatus.ACTIVE -> JpaStrategyStatus.ACTIVE
            StrategyStatus.ARCHIVED -> JpaStrategyStatus.ARCHIVED
        }
    }

    /**
     * JPA RebalanceFrequency → Domain RebalanceFrequency
     */
    private fun mapRebalanceFrequency(jpaFrequency: JpaRebalanceFrequency): RebalanceFrequency {
        return when (jpaFrequency) {
            JpaRebalanceFrequency.DAILY -> RebalanceFrequency.DAILY
            JpaRebalanceFrequency.WEEKLY -> RebalanceFrequency.WEEKLY
            JpaRebalanceFrequency.MONTHLY -> RebalanceFrequency.MONTHLY
            JpaRebalanceFrequency.QUARTERLY -> RebalanceFrequency.QUARTERLY
            JpaRebalanceFrequency.YEARLY -> RebalanceFrequency.YEARLY
            JpaRebalanceFrequency.NONE -> RebalanceFrequency.NONE
        }
    }

    /**
     * Domain RebalanceFrequency → JPA RebalanceFrequency
     */
    private fun mapRebalanceFrequencyToJpa(domainFrequency: RebalanceFrequency): JpaRebalanceFrequency {
        return when (domainFrequency) {
            RebalanceFrequency.DAILY -> JpaRebalanceFrequency.DAILY
            RebalanceFrequency.WEEKLY -> JpaRebalanceFrequency.WEEKLY
            RebalanceFrequency.MONTHLY -> JpaRebalanceFrequency.MONTHLY
            RebalanceFrequency.QUARTERLY -> JpaRebalanceFrequency.QUARTERLY
            RebalanceFrequency.YEARLY -> JpaRebalanceFrequency.YEARLY
            RebalanceFrequency.NONE -> JpaRebalanceFrequency.NONE
        }
    }
}
