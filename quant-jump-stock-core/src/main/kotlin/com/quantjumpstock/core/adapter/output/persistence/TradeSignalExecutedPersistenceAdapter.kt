package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeSignalExecutedEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeSignalExecutedJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.ExecutionDecision as JpaExecutionDecision
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeSignal as JpaTradeSignal
import com.quantjumpstock.core.domain.model.trading.ExecutionDecision
import com.quantjumpstock.core.domain.model.trading.TradeSignal
import com.quantjumpstock.core.domain.model.trading.TradeSignalExecuted
import com.quantjumpstock.core.domain.port.output.TradeSignalExecutedRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * TradeSignalExecuted Persistence Adapter (Output Adapter)
 * TradeSignalExecutedRepository 인터페이스를 구현하여 JPA와 연동합니다.
 */
@Component
class TradeSignalExecutedPersistenceAdapter(
    private val tradeSignalExecutedJpaRepository: TradeSignalExecutedJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val tradeJpaRepository: TradeJpaRepository
) : TradeSignalExecutedRepository {

    override fun save(signal: TradeSignalExecuted): TradeSignalExecuted {
        val userEntity = userJpaRepository.findById(signal.userId).orElseThrow {
            IllegalArgumentException("User not found: ${signal.userId}")
        }
        val tradeEntity = signal.executedTradeId?.let {
            tradeJpaRepository.findById(it).orElse(null)
        }
        val entity = toEntity(signal, userEntity, tradeEntity)
        val saved = tradeSignalExecutedJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): TradeSignalExecuted? {
        return tradeSignalExecutedJpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByUserId(userId: Long): List<TradeSignalExecuted> {
        return tradeSignalExecutedJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .map { toDomain(it) }
    }

    override fun findByUserIdAndTicker(userId: Long, ticker: String): List<TradeSignalExecuted> {
        return tradeSignalExecutedJpaRepository.findRecentSignalsForTicker(
            userId, ticker, LocalDateTime.MIN
        ).map { toDomain(it) }
    }

    override fun findByUserIdAndCreatedAtAfter(userId: Long, after: LocalDateTime): List<TradeSignalExecuted> {
        return tradeSignalExecutedJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .filter { it.createdAt.isAfter(after) }
            .map { toDomain(it) }
    }

    override fun deleteById(id: Long) {
        tradeSignalExecutedJpaRepository.deleteById(id)
    }

    // ===== Mapping Functions =====

    private fun toDomain(entity: TradeSignalExecutedEntity): TradeSignalExecuted {
        return TradeSignalExecuted(
            id = entity.id,
            userId = entity.user.id!!,
            recommendationId = entity.recommendationId,
            ticker = entity.ticker,
            signal = mapSignal(entity.signal),
            confidence = entity.confidence,
            executionDecision = mapDecision(entity.executionDecision),
            skipReason = entity.skipReason,
            executedTradeId = entity.executedTrade?.id,
            createdAt = entity.createdAt
        )
    }

    private fun toEntity(
        domain: TradeSignalExecuted,
        userEntity: com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity,
        tradeEntity: com.quantjumpstock.core.adapter.output.persistence.jpa.TradeEntity?
    ): TradeSignalExecutedEntity {
        return TradeSignalExecutedEntity(
            id = domain.id,
            user = userEntity,
            recommendationId = domain.recommendationId,
            ticker = domain.ticker,
            signal = mapSignal(domain.signal),
            confidence = domain.confidence,
            executionDecision = mapDecision(domain.executionDecision),
            skipReason = domain.skipReason,
            executedTrade = tradeEntity,
            createdAt = domain.createdAt
        )
    }

    private fun mapSignal(signal: JpaTradeSignal): TradeSignal = when (signal) {
        JpaTradeSignal.BUY -> TradeSignal.BUY
        JpaTradeSignal.SELL -> TradeSignal.SELL
        JpaTradeSignal.HOLD -> TradeSignal.HOLD
    }

    private fun mapSignal(signal: TradeSignal): JpaTradeSignal = when (signal) {
        TradeSignal.BUY -> JpaTradeSignal.BUY
        TradeSignal.SELL -> JpaTradeSignal.SELL
        TradeSignal.HOLD -> JpaTradeSignal.HOLD
    }

    private fun mapDecision(decision: JpaExecutionDecision): ExecutionDecision = when (decision) {
        JpaExecutionDecision.EXECUTED -> ExecutionDecision.EXECUTED
        JpaExecutionDecision.SKIPPED -> ExecutionDecision.SKIPPED
        JpaExecutionDecision.FAILED -> ExecutionDecision.FAILED
    }

    private fun mapDecision(decision: ExecutionDecision): JpaExecutionDecision = when (decision) {
        ExecutionDecision.EXECUTED -> JpaExecutionDecision.EXECUTED
        ExecutionDecision.SKIPPED -> JpaExecutionDecision.SKIPPED
        ExecutionDecision.FAILED -> JpaExecutionDecision.FAILED
    }
}
