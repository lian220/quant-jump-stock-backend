package com.quantjumpstock.core.adapter.output.persistence.jpa.mapper

import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeSide as JpaTradeSide
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeStatus as JpaTradeStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import com.quantjumpstock.core.domain.model.trading.Trade
import com.quantjumpstock.core.domain.model.trading.TradeSide
import com.quantjumpstock.core.domain.model.trading.TradeStatus
import org.springframework.stereotype.Component

/**
 * Trade Mapper - JPA Entity ↔ Domain Model 변환
 */
@Component
class TradeMapper {

    /**
     * JPA Entity → Domain Model
     */
    fun toDomain(entity: TradeEntity): Trade {
        return Trade(
            id = entity.id,
            userId = entity.user.id ?: throw IllegalStateException("User ID is null"),
            ticker = entity.ticker,
            side = mapSide(entity.side),
            quantity = entity.quantity,
            price = entity.price,
            totalAmount = entity.totalAmount,
            commission = entity.commission,
            status = mapStatus(entity.status),
            kisOrderId = entity.kisOrderId,
            executedAt = entity.executedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Domain Model → JPA Entity (새 엔티티 생성용)
     *
     * @param domain 도메인 모델
     * @param user 연관된 사용자 엔티티 (이미 조회된 상태)
     */
    fun toEntity(domain: Trade, user: UserEntity): TradeEntity {
        return TradeEntity(
            id = domain.id,
            user = user,
            ticker = domain.ticker,
            side = mapSideToJpa(domain.side),
            quantity = domain.quantity,
            price = domain.price,
            totalAmount = domain.totalAmount,
            commission = domain.commission,
            status = mapStatusToJpa(domain.status),
            kisOrderId = domain.kisOrderId,
            executedAt = domain.executedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    /**
     * 기존 Entity 업데이트용
     */
    fun updateEntity(entity: TradeEntity, domain: Trade): TradeEntity {
        return entity.copy(
            status = mapStatusToJpa(domain.status),
            kisOrderId = domain.kisOrderId,
            executedAt = domain.executedAt,
            updatedAt = domain.updatedAt
        )
    }

    private fun mapSide(jpaSide: JpaTradeSide): TradeSide {
        return when (jpaSide) {
            JpaTradeSide.BUY -> TradeSide.BUY
            JpaTradeSide.SELL -> TradeSide.SELL
        }
    }

    fun mapSideToJpa(domainSide: TradeSide): JpaTradeSide {
        return when (domainSide) {
            TradeSide.BUY -> JpaTradeSide.BUY
            TradeSide.SELL -> JpaTradeSide.SELL
        }
    }

    private fun mapStatus(jpaStatus: JpaTradeStatus): TradeStatus {
        return when (jpaStatus) {
            JpaTradeStatus.PENDING -> TradeStatus.PENDING
            JpaTradeStatus.EXECUTED -> TradeStatus.EXECUTED
            JpaTradeStatus.FAILED -> TradeStatus.FAILED
            JpaTradeStatus.CANCELLED -> TradeStatus.CANCELLED
        }
    }

    fun mapStatusToJpa(domainStatus: TradeStatus): JpaTradeStatus {
        return when (domainStatus) {
            TradeStatus.PENDING -> JpaTradeStatus.PENDING
            TradeStatus.EXECUTED -> JpaTradeStatus.EXECUTED
            TradeStatus.FAILED -> JpaTradeStatus.FAILED
            TradeStatus.CANCELLED -> JpaTradeStatus.CANCELLED
        }
    }
}
