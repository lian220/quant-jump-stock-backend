package com.quantjumpstock.core.adapter.output.persistence.jpa.mapper

import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeSignalExecutedEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeSignal as JpaTradeSignal
import com.quantjumpstock.core.adapter.output.persistence.jpa.ExecutionDecision as JpaExecutionDecision
import com.quantjumpstock.core.domain.model.trading.TradeSignalExecuted
import com.quantjumpstock.core.domain.model.trading.TradeSignal
import com.quantjumpstock.core.domain.model.trading.ExecutionDecision
import org.springframework.stereotype.Component

/**
 * TradeSignalExecuted Mapper - JPA Entity ↔ Domain Model 변환
 */
@Component
class TradeSignalExecutedMapper {

    /**
     * JPA Entity → Domain Model
     */
    fun toDomain(entity: TradeSignalExecutedEntity): TradeSignalExecuted {
        return TradeSignalExecuted(
            id = entity.id,
            userId = entity.user.id ?: throw IllegalStateException("User ID is null"),
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

    /**
     * Domain Model → JPA Entity
     */
    fun toEntity(
        domain: TradeSignalExecuted,
        user: UserEntity,
        trade: TradeEntity? = null
    ): TradeSignalExecutedEntity {
        return TradeSignalExecutedEntity(
            id = domain.id,
            user = user,
            recommendationId = domain.recommendationId,
            ticker = domain.ticker,
            signal = mapSignalToJpa(domain.signal),
            confidence = domain.confidence,
            executionDecision = mapDecisionToJpa(domain.executionDecision),
            skipReason = domain.skipReason,
            executedTrade = trade,
            createdAt = domain.createdAt
        )
    }

    private fun mapSignal(jpaSignal: JpaTradeSignal): TradeSignal {
        return when (jpaSignal) {
            JpaTradeSignal.BUY -> TradeSignal.BUY
            JpaTradeSignal.SELL -> TradeSignal.SELL
            JpaTradeSignal.HOLD -> TradeSignal.HOLD
        }
    }

    private fun mapSignalToJpa(domainSignal: TradeSignal): JpaTradeSignal {
        return when (domainSignal) {
            TradeSignal.BUY -> JpaTradeSignal.BUY
            TradeSignal.SELL -> JpaTradeSignal.SELL
            TradeSignal.HOLD -> JpaTradeSignal.HOLD
        }
    }

    private fun mapDecision(jpaDecision: JpaExecutionDecision): ExecutionDecision {
        return when (jpaDecision) {
            JpaExecutionDecision.EXECUTED -> ExecutionDecision.EXECUTED
            JpaExecutionDecision.SKIPPED -> ExecutionDecision.SKIPPED
            JpaExecutionDecision.FAILED -> ExecutionDecision.FAILED
        }
    }

    private fun mapDecisionToJpa(domainDecision: ExecutionDecision): JpaExecutionDecision {
        return when (domainDecision) {
            ExecutionDecision.EXECUTED -> JpaExecutionDecision.EXECUTED
            ExecutionDecision.SKIPPED -> JpaExecutionDecision.SKIPPED
            ExecutionDecision.FAILED -> JpaExecutionDecision.FAILED
        }
    }
}
