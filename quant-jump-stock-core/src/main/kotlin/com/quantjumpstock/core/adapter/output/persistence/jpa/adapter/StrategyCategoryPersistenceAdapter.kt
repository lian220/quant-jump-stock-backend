package com.quantjumpstock.core.adapter.output.persistence.jpa.adapter

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyCategoryRepository as JpaStrategyCategoryRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.mapper.StrategyCategoryMapper
import com.quantjumpstock.core.domain.model.strategy.StrategyCategory
import com.quantjumpstock.core.domain.port.output.StrategyCategoryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

/**
 * StrategyCategory Persistence Adapter - Hexagonal Architecture 준수
 */
@Repository
class StrategyCategoryPersistenceAdapter(
    private val jpaRepository: JpaStrategyCategoryRepository,
    private val mapper: StrategyCategoryMapper
) : StrategyCategoryRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(category: StrategyCategory): StrategyCategory {
        logger.debug("카테고리 저장: code=${category.code}")
        val entity = mapper.toEntity(category)
        val savedEntity = jpaRepository.save(entity)
        return mapper.toDomain(savedEntity)
    }

    override fun findById(id: Long): StrategyCategory? {
        logger.debug("카테고리 조회: id=$id")
        return jpaRepository.findById(id)
            .map { mapper.toDomain(it) }
            .orElse(null)
    }

    override fun findByCode(code: String): StrategyCategory? {
        logger.debug("카테고리 조회: code=$code")
        return jpaRepository.findByCode(code)?.let { mapper.toDomain(it) }
    }

    override fun findAll(): List<StrategyCategory> {
        logger.debug("전체 카테고리 조회")
        return jpaRepository.findAll().map { mapper.toDomain(it) }
    }

    override fun findAllActive(): List<StrategyCategory> {
        logger.debug("활성 카테고리 조회")
        return jpaRepository.findByIsActiveTrueOrderBySortOrderAsc().map { mapper.toDomain(it) }
    }

    override fun deleteById(id: Long) {
        logger.debug("카테고리 삭제: id=$id")
        jpaRepository.deleteById(id)
    }

    override fun existsById(id: Long): Boolean {
        return jpaRepository.existsById(id)
    }

    override fun existsByCode(code: String): Boolean {
        return jpaRepository.existsByCode(code)
    }
}
