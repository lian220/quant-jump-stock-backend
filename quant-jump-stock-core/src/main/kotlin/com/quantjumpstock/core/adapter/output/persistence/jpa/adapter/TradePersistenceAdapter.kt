package com.quantjumpstock.core.adapter.output.persistence.jpa.adapter

import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeStatus as JpaTradeStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.mapper.TradeMapper
import com.quantjumpstock.core.domain.model.trading.Trade
import com.quantjumpstock.core.domain.model.trading.TradeSide
import com.quantjumpstock.core.domain.model.trading.TradeStatus
import com.quantjumpstock.core.domain.port.output.TradeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Trade Persistence Adapter - Hexagonal Architecture 준수
 */
@Repository
class TradePersistenceAdapter(
    private val tradeJpaRepository: TradeJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val tradeMapper: TradeMapper
) : TradeRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(trade: Trade): Trade {
        logger.debug("거래 저장: userId=${trade.userId}, ticker=${trade.ticker}")

        val entity = if (trade.id != null) {
            // 기존 엔티티 업데이트
            val existingEntity = tradeJpaRepository.findById(trade.id)
                .orElseThrow { IllegalArgumentException("Trade not found: ${trade.id}") }
            tradeMapper.updateEntity(existingEntity, trade)
        } else {
            // 새 엔티티 생성
            val userEntity = userJpaRepository.findById(trade.userId)
                .orElseThrow { IllegalArgumentException("User not found: ${trade.userId}") }
            tradeMapper.toEntity(trade, userEntity)
        }

        val savedEntity = tradeJpaRepository.save(entity)
        return tradeMapper.toDomain(savedEntity)
    }

    override fun findById(id: Long): Trade? {
        logger.debug("거래 조회: id=$id")
        return tradeJpaRepository.findById(id)
            .map { tradeMapper.toDomain(it) }
            .orElse(null)
    }

    override fun findByUserId(userId: Long): List<Trade> {
        logger.debug("사용자별 거래 목록 조회: userId=$userId")
        return tradeJpaRepository.findByUserId(userId)
            .map { tradeMapper.toDomain(it) }
    }

    override fun findByUserIdAndStatus(userId: Long, status: TradeStatus): List<Trade> {
        logger.debug("사용자별 상태별 거래 목록 조회: userId=$userId, status=$status")
        val jpaStatus = mapStatusToJpa(status)
        return tradeJpaRepository.findByUserIdAndStatus(userId, jpaStatus)
            .map { tradeMapper.toDomain(it) }
    }

    override fun findByTicker(ticker: String): List<Trade> {
        logger.debug("티커별 거래 목록 조회: ticker=$ticker")
        return tradeJpaRepository.findByTicker(ticker)
            .map { tradeMapper.toDomain(it) }
    }

    override fun findByUserIdAndTicker(userId: Long, ticker: String): List<Trade> {
        logger.debug("사용자 및 티커별 거래 목록 조회: userId=$userId, ticker=$ticker")
        return tradeJpaRepository.findByUserIdAndTicker(userId, ticker)
            .map { tradeMapper.toDomain(it) }
    }

    override fun findByUserIdAndCreatedAtBetween(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Trade> {
        logger.debug("사용자별 기간 거래 목록 조회: userId=$userId, $startDate ~ $endDate")
        return tradeJpaRepository.findByUserIdAndCreatedAtBetween(userId, startDate, endDate)
            .map { tradeMapper.toDomain(it) }
    }

    override fun findPendingTrades(): List<Trade> {
        logger.debug("대기 중인 거래 목록 조회")
        return tradeJpaRepository.findByStatus(JpaTradeStatus.PENDING)
            .map { tradeMapper.toDomain(it) }
    }

    override fun deleteById(id: Long) {
        logger.debug("거래 삭제: id=$id")
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
        since: java.time.LocalDateTime
    ): List<Trade> {
        logger.debug("최근 거래 조회: userId=$userId, ticker=$ticker, side=$side, status=$status")
        val jpaSide = tradeMapper.mapSideToJpa(side)
        val jpaStatus = tradeMapper.mapStatusToJpa(status)
        return tradeJpaRepository.findRecentTrade(userId, ticker, jpaSide, jpaStatus, since)
            .map { tradeMapper.toDomain(it) }
    }

    private fun mapStatusToJpa(domainStatus: TradeStatus): JpaTradeStatus {
        return when (domainStatus) {
            TradeStatus.PENDING -> JpaTradeStatus.PENDING
            TradeStatus.EXECUTED -> JpaTradeStatus.EXECUTED
            TradeStatus.FAILED -> JpaTradeStatus.FAILED
            TradeStatus.CANCELLED -> JpaTradeStatus.CANCELLED
        }
    }
}
