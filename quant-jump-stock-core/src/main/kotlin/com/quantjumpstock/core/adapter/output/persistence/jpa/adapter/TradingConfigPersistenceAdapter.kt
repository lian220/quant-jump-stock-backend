package com.quantjumpstock.core.adapter.output.persistence.jpa.adapter

import com.quantjumpstock.core.adapter.output.persistence.jpa.TradingConfigJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.mapper.TradingConfigMapper
import com.quantjumpstock.core.domain.model.trading.TradingConfig
import com.quantjumpstock.core.domain.port.output.TradingConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

/**
 * TradingConfig Persistence Adapter - Hexagonal Architecture 준수
 */
@Repository
class TradingConfigPersistenceAdapter(
    private val tradingConfigJpaRepository: TradingConfigJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val tradingConfigMapper: TradingConfigMapper
) : TradingConfigRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(config: TradingConfig): TradingConfig {
        logger.debug("거래 설정 저장: userId=${config.userId}")

        val userEntity = userJpaRepository.findById(config.userId)
            .orElseThrow { IllegalArgumentException("User not found: ${config.userId}") }

        val entity = tradingConfigMapper.toEntity(config, userEntity)
        val savedEntity = tradingConfigJpaRepository.save(entity)
        return tradingConfigMapper.toDomain(savedEntity)
    }

    override fun findById(id: Long): TradingConfig? {
        logger.debug("거래 설정 조회: id=$id")
        return tradingConfigJpaRepository.findById(id)
            .map { tradingConfigMapper.toDomain(it) }
            .orElse(null)
    }

    override fun findByUserId(userId: Long): TradingConfig? {
        logger.debug("사용자별 거래 설정 조회: userId=$userId")
        return tradingConfigJpaRepository.findByUserId(userId)
            .map { tradingConfigMapper.toDomain(it) }
            .orElse(null)
    }

    override fun findAllEnabledWithAutoTrading(): List<TradingConfig> {
        logger.debug("자동 매매 활성화 설정 목록 조회")
        return tradingConfigJpaRepository.findAllEnabledWithAutoTrading()
            .map { tradingConfigMapper.toDomain(it) }
    }

    override fun findAllEnabled(): List<TradingConfig> {
        logger.debug("활성화 설정 목록 조회")
        return tradingConfigJpaRepository.findAllEnabled()
            .map { tradingConfigMapper.toDomain(it) }
    }

    override fun deleteById(id: Long) {
        logger.debug("거래 설정 삭제: id=$id")
        tradingConfigJpaRepository.deleteById(id)
    }

    override fun existsByUserId(userId: Long): Boolean {
        return tradingConfigJpaRepository.findByUserId(userId).isPresent
    }

    override fun countAutoTradingEnabled(): Long {
        return tradingConfigJpaRepository.countAutoTradingEnabled()
    }
}
