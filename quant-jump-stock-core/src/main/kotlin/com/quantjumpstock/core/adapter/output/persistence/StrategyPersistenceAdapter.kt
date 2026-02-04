package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyJpaRepository
import com.quantjumpstock.core.domain.strategy.port.output.StrategyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Optional

/**
 * Strategy Persistence Adapter (Output Adapter)
 * StrategyRepository 인터페이스를 구현하여 JPA와 연동합니다.
 */
@Component
class StrategyPersistenceAdapter(
    private val strategyJpaRepository: StrategyJpaRepository
) : StrategyRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(strategy: StrategyEntity): StrategyEntity {
        logger.debug("전략 저장: name=${strategy.name}")
        return strategyJpaRepository.save(strategy)
    }

    override fun findById(id: Long): Optional<StrategyEntity> {
        logger.debug("전략 조회: id=$id")
        return strategyJpaRepository.findById(id)
    }

    override fun findByIdWithBacktestResults(id: Long): Optional<StrategyEntity> {
        logger.debug("전략 조회 (백테스트 포함): id=$id")
        return strategyJpaRepository.findByIdWithBacktestResults(id)
    }

    override fun findByOwnerId(ownerId: Long): List<StrategyEntity> {
        logger.debug("소유자별 전략 목록 조회: ownerId=$ownerId")
        return strategyJpaRepository.findByOwnerId(ownerId)
    }

    override fun delete(strategy: StrategyEntity) {
        logger.debug("전략 삭제: id=${strategy.id}")
        strategyJpaRepository.delete(strategy)
    }

    override fun existsById(id: Long): Boolean {
        return strategyJpaRepository.existsById(id)
    }
}
