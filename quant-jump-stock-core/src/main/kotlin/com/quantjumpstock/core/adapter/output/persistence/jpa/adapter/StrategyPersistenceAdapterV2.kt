package com.quantjumpstock.core.adapter.output.persistence.jpa.adapter

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyCategoryRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyStatus as JpaStrategyStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.mapper.StrategyMapper
import com.quantjumpstock.core.domain.model.strategy.Strategy
import com.quantjumpstock.core.domain.model.strategy.StrategyStatus
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

/**
 * Strategy Persistence Adapter V2 - Hexagonal Architecture 준수
 *
 * 특징:
 * - 도메인 포트(StrategyRepository) 구현
 * - 도메인 모델(Strategy)만 사용
 * - JPA Entity는 내부에서만 사용
 * - Mapper를 통한 변환
 */
@Repository("strategyPersistenceAdapterV2")
class StrategyPersistenceAdapterV2(
    private val strategyJpaRepository: StrategyJpaRepository,
    private val strategyCategoryRepository: StrategyCategoryRepository,
    private val userJpaRepository: UserJpaRepository,
    private val strategyMapper: StrategyMapper
) : StrategyRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(strategy: Strategy): Strategy {
        logger.debug("전략 저장: name=${strategy.name}")

        // 카테고리 엔티티 조회
        val categoryEntity = strategyCategoryRepository.findById(strategy.categoryId)
            .orElseThrow { IllegalArgumentException("Category not found: ${strategy.categoryId}") }

        // 소유자 엔티티 조회 (optional)
        val ownerEntity = strategy.ownerId?.let { ownerId ->
            userJpaRepository.findById(ownerId).orElse(null)
        }

        val entity = if (strategy.id != null) {
            // 기존 엔티티 업데이트
            val existingEntity = strategyJpaRepository.findById(strategy.id)
                .orElseThrow { IllegalArgumentException("Strategy not found: ${strategy.id}") }
            strategyMapper.updateEntity(existingEntity, strategy)
        } else {
            // 새 엔티티 생성
            strategyMapper.toEntity(strategy, categoryEntity, ownerEntity)
        }

        val savedEntity = strategyJpaRepository.save(entity)
        return strategyMapper.toDomain(savedEntity)
    }

    override fun findById(id: Long): Strategy? {
        logger.debug("전략 조회: id=$id")
        return strategyJpaRepository.findById(id)
            .map { strategyMapper.toDomain(it) }
            .orElse(null)
    }

    override fun findByOwnerId(ownerId: Long): List<Strategy> {
        logger.debug("소유자별 전략 목록 조회: ownerId=$ownerId")
        return strategyJpaRepository.findByOwnerId(ownerId)
            .map { strategyMapper.toDomain(it) }
    }

    override fun findByStatus(status: StrategyStatus): List<Strategy> {
        logger.debug("상태별 전략 목록 조회: status=$status")
        val jpaStatus = mapStatusToJpa(status)
        return strategyJpaRepository.findByStatus(jpaStatus)
            .map { strategyMapper.toDomain(it) }
    }

    override fun findByCategoryId(categoryId: Long): List<Strategy> {
        logger.debug("카테고리별 전략 목록 조회: categoryId=$categoryId")
        return strategyJpaRepository.findByCategoryId(categoryId)
            .map { strategyMapper.toDomain(it) }
    }

    override fun findPublishedAndPublic(): List<Strategy> {
        logger.debug("마켓플레이스용 전략 목록 조회")
        return strategyJpaRepository.findByIsPublicTrueAndStatus(
            JpaStrategyStatus.PUBLISHED,
            org.springframework.data.domain.Pageable.unpaged()
        ).content.map { strategyMapper.toDomain(it) }
    }

    override fun findAll(): List<Strategy> {
        logger.debug("전체 전략 목록 조회")
        return strategyJpaRepository.findAll()
            .map { strategyMapper.toDomain(it) }
    }

    override fun deleteById(id: Long) {
        logger.debug("전략 삭제: id=$id")
        strategyJpaRepository.deleteById(id)
    }

    override fun existsById(id: Long): Boolean {
        return strategyJpaRepository.existsById(id)
    }

    override fun findByIdWithBacktestResults(id: Long): Strategy? {
        logger.debug("백테스트 포함 전략 조회: id=$id")
        return strategyJpaRepository.findByIdWithBacktestResults(id)
            .map { strategyMapper.toDomain(it) }
            .orElse(null)
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
}
