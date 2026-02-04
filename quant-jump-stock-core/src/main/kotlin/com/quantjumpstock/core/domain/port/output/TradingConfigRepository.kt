package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.trading.TradingConfig

/**
 * TradingConfig Repository Port - 도메인 포트
 */
interface TradingConfigRepository {

    fun save(config: TradingConfig): TradingConfig

    fun findById(id: Long): TradingConfig?

    fun findByUserId(userId: Long): TradingConfig?

    /**
     * 자동 매매가 활성화된 설정 목록 조회
     */
    fun findAllEnabledWithAutoTrading(): List<TradingConfig>

    /**
     * 활성화된 설정 목록 조회
     */
    fun findAllEnabled(): List<TradingConfig>

    fun deleteById(id: Long)

    fun existsByUserId(userId: Long): Boolean

    /**
     * 자동 매매 활성화된 설정 개수
     */
    fun countAutoTradingEnabled(): Long
}
