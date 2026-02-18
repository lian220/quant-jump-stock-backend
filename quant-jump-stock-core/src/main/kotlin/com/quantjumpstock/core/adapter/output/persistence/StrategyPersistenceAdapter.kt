package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyCategoryJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyStatus as JpaStrategyStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.StockSelectionType as JpaStockSelectionType
import com.quantjumpstock.core.adapter.output.persistence.jpa.RebalanceFrequency as JpaRebalanceFrequency
import com.quantjumpstock.core.domain.model.backtest.UniverseType
import com.quantjumpstock.core.domain.model.strategy.Strategy
import com.quantjumpstock.core.domain.model.strategy.StockSelectionType
import com.quantjumpstock.core.domain.model.strategy.StrategyStatus
import com.quantjumpstock.core.domain.model.strategy.RebalanceFrequency
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

/**
 * Strategy Persistence Adapter (Output Adapter)
 * StrategyRepository 인터페이스를 구현하여 JPA와 연동합니다.
 *
 * 특징:
 * - 도메인 모델(Strategy)과 JPA Entity(StrategyEntity) 간 변환
 * - JPA enum과 Domain enum 간 변환
 */
@Component("strategyPersistenceAdapterV2")
class StrategyPersistenceAdapter(
    private val strategyJpaRepository: StrategyJpaRepository,
    private val categoryJpaRepository: StrategyCategoryJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val objectMapper: ObjectMapper
) : StrategyRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(strategy: Strategy): Strategy {
        logger.debug("전략 저장: name=${strategy.name}")
        val entity = toEntity(strategy)
        val saved = strategyJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): Strategy? {
        logger.debug("전략 조회: id=$id")
        return strategyJpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByIdWithBacktestResults(id: Long): Strategy? {
        logger.debug("전략 조회 (백테스트 포함): id=$id")
        return strategyJpaRepository.findByIdWithBacktestResults(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByOwnerId(ownerId: Long): List<Strategy> {
        logger.debug("소유자별 전략 목록 조회: ownerId=$ownerId")
        return strategyJpaRepository.findByOwnerId(ownerId).map { toDomain(it) }
    }

    override fun findByStatus(status: StrategyStatus): List<Strategy> {
        return strategyJpaRepository.findByStatus(mapStatus(status)).map { toDomain(it) }
    }

    override fun findByStatus(status: StrategyStatus, pageable: Pageable): Page<Strategy> {
        return strategyJpaRepository.findByStatus(mapStatus(status), pageable).map { toDomain(it) }
    }

    override fun findByStatusAndCategoryCode(status: StrategyStatus, categoryCode: String, pageable: Pageable): Page<Strategy> {
        return strategyJpaRepository.findByStatusAndCategoryCode(mapStatus(status), categoryCode, pageable).map { toDomain(it) }
    }

    override fun findByCategoryCode(categoryCode: String, pageable: Pageable): Page<Strategy> {
        return strategyJpaRepository.findByCategory_Code(categoryCode, pageable).map { toDomain(it) }
    }

    override fun findAll(pageable: Pageable): Page<Strategy> {
        return strategyJpaRepository.findAll(pageable).map { toDomain(it) }
    }

    override fun findByCategoryId(categoryId: Long): List<Strategy> {
        return strategyJpaRepository.findByCategoryId(categoryId).map { toDomain(it) }
    }

    override fun findPublishedAndPublic(): List<Strategy> {
        return strategyJpaRepository.findByStatus(JpaStrategyStatus.PUBLISHED)
            .filter { it.isPublic }
            .map { toDomain(it) }
    }

    override fun findAll(): List<Strategy> {
        return strategyJpaRepository.findAll().map { toDomain(it) }
    }

    override fun deleteById(id: Long) {
        logger.debug("전략 삭제: id=$id")
        strategyJpaRepository.deleteById(id)
    }

    override fun existsById(id: Long): Boolean {
        return strategyJpaRepository.existsById(id)
    }

    override fun count(): Long {
        return strategyJpaRepository.count()
    }

    override fun countByStatus(status: StrategyStatus): Long {
        return strategyJpaRepository.countByStatus(mapStatus(status))
    }

    override fun sumSubscriberCount(): Long {
        return strategyJpaRepository.sumSubscriberCount() ?: 0L
    }

    // ===== Mapping Functions =====

    private fun toDomain(entity: StrategyEntity): Strategy {
        return Strategy(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            categoryId = entity.category.id!!,
            ownerId = entity.owner?.id,
            isPublic = entity.isPublic,
            isPremium = entity.isPremium,
            status = mapStatus(entity.status),
            stockSelectionType = mapStockSelectionType(entity.stockSelectionType),
            investmentPhilosophy = entity.investmentPhilosophy,
            // SCRUM-344: 유니버스 설정 + 대표 백테스트
            recommendedUniverseType = parseUniverseType(entity.recommendedUniverseType),
            supportedUniverseTypes = parseSupportedUniverseTypes(entity.supportedUniverseTypes),
            canonicalBacktestId = entity.canonicalBacktestId,
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

    private fun toEntity(domain: Strategy): StrategyEntity {
        val category = categoryJpaRepository.findById(domain.categoryId)
            .orElseThrow { IllegalArgumentException("Category not found: ${domain.categoryId}") }

        // 기존 Entity가 있으면 조회하여 연관관계 유지
        val existingEntity = domain.id?.let { strategyJpaRepository.findById(it).orElse(null) }

        val owner = when {
            existingEntity != null -> existingEntity.owner
            domain.ownerId != null -> {
                val resolved = userJpaRepository.findById(domain.ownerId).orElse(null)
                if (resolved == null) {
                    logger.warn("전략 소유자를 찾을 수 없습니다: ownerId=${domain.ownerId}, strategyName=${domain.name}")
                }
                resolved
            }
            else -> null
        }

        return StrategyEntity(
            id = domain.id,
            name = domain.name,
            description = domain.description,
            category = category,
            owner = owner,
            isPublic = domain.isPublic,
            isPremium = domain.isPremium,
            status = mapStatus(domain.status),
            stockSelectionType = mapStockSelectionType(domain.stockSelectionType),
            investmentPhilosophy = domain.investmentPhilosophy,
            // SCRUM-344: 유니버스 설정 + 대표 백테스트
            recommendedUniverseType = domain.recommendedUniverseType.name,
            supportedUniverseTypes = objectMapper.writeValueAsString(domain.supportedUniverseTypes.map { it.name }),
            canonicalBacktestId = domain.canonicalBacktestId,
            conditions = domain.conditions,
            riskSettings = domain.riskSettings,
            positionSizing = domain.positionSizing,
            tradingCosts = domain.tradingCosts,
            rebalanceFrequency = mapRebalanceFrequency(domain.rebalanceFrequency),
            subscriberCount = domain.subscriberCount,
            averageRating = domain.averageRating,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    private fun mapStatus(jpaStatus: JpaStrategyStatus): StrategyStatus = when (jpaStatus) {
        JpaStrategyStatus.DRAFT -> StrategyStatus.DRAFT
        JpaStrategyStatus.PENDING_REVIEW -> StrategyStatus.PENDING_REVIEW
        JpaStrategyStatus.APPROVED -> StrategyStatus.APPROVED
        JpaStrategyStatus.PUBLISHED -> StrategyStatus.PUBLISHED
        JpaStrategyStatus.REJECTED -> StrategyStatus.REJECTED
        JpaStrategyStatus.ACTIVE -> StrategyStatus.ACTIVE
        JpaStrategyStatus.ARCHIVED -> StrategyStatus.ARCHIVED
    }

    private fun mapStatus(domainStatus: StrategyStatus): JpaStrategyStatus = when (domainStatus) {
        StrategyStatus.DRAFT -> JpaStrategyStatus.DRAFT
        StrategyStatus.PENDING_REVIEW -> JpaStrategyStatus.PENDING_REVIEW
        StrategyStatus.APPROVED -> JpaStrategyStatus.APPROVED
        StrategyStatus.PUBLISHED -> JpaStrategyStatus.PUBLISHED
        StrategyStatus.REJECTED -> JpaStrategyStatus.REJECTED
        StrategyStatus.ACTIVE -> JpaStrategyStatus.ACTIVE
        StrategyStatus.ARCHIVED -> JpaStrategyStatus.ARCHIVED
    }

    private fun mapStockSelectionType(jpaType: JpaStockSelectionType): StockSelectionType = when (jpaType) {
        JpaStockSelectionType.SCREENING -> StockSelectionType.SCREENING
        JpaStockSelectionType.PORTFOLIO -> StockSelectionType.PORTFOLIO
    }

    private fun mapStockSelectionType(domainType: StockSelectionType): JpaStockSelectionType = when (domainType) {
        StockSelectionType.SCREENING -> JpaStockSelectionType.SCREENING
        StockSelectionType.PORTFOLIO -> JpaStockSelectionType.PORTFOLIO
    }

    private fun mapRebalanceFrequency(jpaFreq: JpaRebalanceFrequency): RebalanceFrequency = when (jpaFreq) {
        JpaRebalanceFrequency.DAILY -> RebalanceFrequency.DAILY
        JpaRebalanceFrequency.WEEKLY -> RebalanceFrequency.WEEKLY
        JpaRebalanceFrequency.MONTHLY -> RebalanceFrequency.MONTHLY
        JpaRebalanceFrequency.QUARTERLY -> RebalanceFrequency.QUARTERLY
        JpaRebalanceFrequency.YEARLY -> RebalanceFrequency.YEARLY
        JpaRebalanceFrequency.NONE -> RebalanceFrequency.NONE
    }

    private fun mapRebalanceFrequency(domainFreq: RebalanceFrequency): JpaRebalanceFrequency = when (domainFreq) {
        RebalanceFrequency.DAILY -> JpaRebalanceFrequency.DAILY
        RebalanceFrequency.WEEKLY -> JpaRebalanceFrequency.WEEKLY
        RebalanceFrequency.MONTHLY -> JpaRebalanceFrequency.MONTHLY
        RebalanceFrequency.QUARTERLY -> JpaRebalanceFrequency.QUARTERLY
        RebalanceFrequency.YEARLY -> JpaRebalanceFrequency.YEARLY
        RebalanceFrequency.NONE -> JpaRebalanceFrequency.NONE
    }

    // ===== SCRUM-344: Universe Type Parsing =====

    private fun parseUniverseType(value: String): UniverseType = try {
        UniverseType.valueOf(value)
    } catch (e: Exception) {
        logger.warn("Unknown universeType: {}, defaulting to MARKET", value)
        UniverseType.MARKET
    }

    private fun parseSupportedUniverseTypes(json: String): List<UniverseType> = try {
        objectMapper.readValue<List<String>>(json).mapNotNull { name ->
            try { UniverseType.valueOf(name) } catch (e: Exception) { null }
        }
    } catch (e: Exception) {
        logger.warn("supportedUniverseTypes JSON 파싱 실패, 기본값 사용: {}", e.message)
        listOf(UniverseType.MARKET, UniverseType.PORTFOLIO, UniverseType.FIXED)
    }
}
