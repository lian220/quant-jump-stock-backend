package com.quantjumpstock.core.adapter.output.persistence.jpa.adapter

import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeSignalExecutedJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.mapper.TradeSignalExecutedMapper
import com.quantjumpstock.core.domain.model.trading.TradeSignalExecuted
import com.quantjumpstock.core.domain.port.output.TradeSignalExecutedRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * TradeSignalExecuted Persistence Adapter - Hexagonal Architecture 준수
 */
@Repository
class TradeSignalExecutedPersistenceAdapter(
    private val tradeSignalExecutedJpaRepository: TradeSignalExecutedJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val tradeJpaRepository: TradeJpaRepository,
    private val mapper: TradeSignalExecutedMapper
) : TradeSignalExecutedRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(signal: TradeSignalExecuted): TradeSignalExecuted {
        logger.debug("거래 신호 실행 기록 저장: userId=${signal.userId}, ticker=${signal.ticker}")

        val userEntity = userJpaRepository.findById(signal.userId)
            .orElseThrow { IllegalArgumentException("User not found: ${signal.userId}") }

        val tradeEntity = signal.executedTradeId?.let { tradeId ->
            tradeJpaRepository.findById(tradeId).orElse(null)
        }

        val entity = mapper.toEntity(signal, userEntity, tradeEntity)
        val savedEntity = tradeSignalExecutedJpaRepository.save(entity)
        return mapper.toDomain(savedEntity)
    }

    override fun findById(id: Long): TradeSignalExecuted? {
        logger.debug("거래 신호 실행 기록 조회: id=$id")
        return tradeSignalExecutedJpaRepository.findById(id)
            .map { mapper.toDomain(it) }
            .orElse(null)
    }

    override fun findByUserId(userId: Long): List<TradeSignalExecuted> {
        logger.debug("사용자별 거래 신호 실행 기록 조회: userId=$userId")
        return tradeSignalExecutedJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .map { mapper.toDomain(it) }
    }

    override fun findByUserIdAndTicker(userId: Long, ticker: String): List<TradeSignalExecuted> {
        logger.debug("사용자 및 티커별 거래 신호 실행 기록 조회: userId=$userId, ticker=$ticker")
        return tradeSignalExecutedJpaRepository.findRecentSignalsForTicker(
            userId,
            ticker,
            LocalDateTime.now().minusDays(30)
        ).map { mapper.toDomain(it) }
    }

    override fun findByUserIdAndCreatedAtAfter(userId: Long, after: LocalDateTime): List<TradeSignalExecuted> {
        logger.debug("사용자별 최근 거래 신호 실행 기록 조회: userId=$userId, after=$after")
        return tradeSignalExecutedJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .filter { it.createdAt >= after }
            .map { mapper.toDomain(it) }
    }

    override fun deleteById(id: Long) {
        logger.debug("거래 신호 실행 기록 삭제: id=$id")
        tradeSignalExecutedJpaRepository.deleteById(id)
    }
}
