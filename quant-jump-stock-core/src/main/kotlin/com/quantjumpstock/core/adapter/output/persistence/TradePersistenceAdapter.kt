package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeSide as JpaTradeSide
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeStatus as JpaTradeStatus
import com.quantjumpstock.core.domain.model.trading.Trade
import com.quantjumpstock.core.domain.model.trading.TradeSide
import com.quantjumpstock.core.domain.model.trading.TradeStatus
import com.quantjumpstock.core.domain.port.output.TradeRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Trade Persistence Adapter (Output Adapter)
 * TradeRepository 인터페이스를 구현하여 JPA와 연동합니다.
 */
@Component
class TradePersistenceAdapter(
    private val tradeJpaRepository: TradeJpaRepository,
    private val userJpaRepository: UserJpaRepository
) : TradeRepository {

    override fun save(trade: Trade): Trade {
        val userEntity = userJpaRepository.findById(trade.userId).orElseThrow {
            IllegalArgumentException("User not found: ${trade.userId}")
        }
        val entity = toEntity(trade, userEntity)
        val saved = tradeJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): Trade? {
        return tradeJpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByUserId(userId: Long): List<Trade> {
        return tradeJpaRepository.findByUserId(userId).map { toDomain(it) }
    }

    override fun findByUserIdAndStatus(userId: Long, status: TradeStatus): List<Trade> {
        return tradeJpaRepository.findByUserIdAndStatus(userId, mapStatus(status)).map { toDomain(it) }
    }

    override fun findByTicker(ticker: String): List<Trade> {
        return tradeJpaRepository.findByTicker(ticker).map { toDomain(it) }
    }

    override fun findByUserIdAndTicker(userId: Long, ticker: String): List<Trade> {
        return tradeJpaRepository.findByUserIdAndTicker(userId, ticker).map { toDomain(it) }
    }

    override fun findByUserIdAndCreatedAtBetween(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Trade> {
        return tradeJpaRepository.findByUserIdAndCreatedAtBetween(userId, startDate, endDate)
            .map { toDomain(it) }
    }

    override fun findPendingTrades(): List<Trade> {
        return tradeJpaRepository.findByStatus(JpaTradeStatus.PENDING).map { toDomain(it) }
    }

    override fun deleteById(id: Long) {
        tradeJpaRepository.deleteById(id)
    }

    override fun existsById(id: Long): Boolean {
        return tradeJpaRepository.existsById(id)
    }

    override fun findRecentTrades(
        userId: Long,
        ticker: String,
        side: TradeSide,
        status: TradeStatus,
        since: LocalDateTime
    ): List<Trade> {
        return tradeJpaRepository.findRecentTrade(
            userId, ticker, mapSide(side), mapStatus(status), since
        ).map { toDomain(it) }
    }

    // ===== Mapping Functions =====

    private fun toDomain(entity: TradeEntity): Trade {
        return Trade(
            id = entity.id,
            userId = entity.user.id!!,
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

    private fun toEntity(domain: Trade, userEntity: com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity): TradeEntity {
        return TradeEntity(
            id = domain.id,
            user = userEntity,
            ticker = domain.ticker,
            side = mapSide(domain.side),
            quantity = domain.quantity,
            price = domain.price,
            totalAmount = domain.totalAmount,
            commission = domain.commission,
            status = mapStatus(domain.status),
            kisOrderId = domain.kisOrderId,
            executedAt = domain.executedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    private fun mapSide(side: JpaTradeSide): TradeSide = when (side) {
        JpaTradeSide.BUY -> TradeSide.BUY
        JpaTradeSide.SELL -> TradeSide.SELL
    }

    private fun mapSide(side: TradeSide): JpaTradeSide = when (side) {
        TradeSide.BUY -> JpaTradeSide.BUY
        TradeSide.SELL -> JpaTradeSide.SELL
    }

    private fun mapStatus(status: JpaTradeStatus): TradeStatus = when (status) {
        JpaTradeStatus.PENDING -> TradeStatus.PENDING
        JpaTradeStatus.EXECUTED -> TradeStatus.EXECUTED
        JpaTradeStatus.FAILED -> TradeStatus.FAILED
        JpaTradeStatus.CANCELLED -> TradeStatus.CANCELLED
    }

    private fun mapStatus(status: TradeStatus): JpaTradeStatus = when (status) {
        TradeStatus.PENDING -> JpaTradeStatus.PENDING
        TradeStatus.EXECUTED -> JpaTradeStatus.EXECUTED
        TradeStatus.FAILED -> JpaTradeStatus.FAILED
        TradeStatus.CANCELLED -> JpaTradeStatus.CANCELLED
    }
}
