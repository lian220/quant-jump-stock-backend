package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.adapter.output.persistence.jpa.TradingConfigEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TradingConfigJpaRepository : JpaRepository<TradingConfigEntity, Long> {

    @Query("SELECT tc FROM TradingConfigEntity tc WHERE tc.user.id = :userId")
    fun findByUserId(userId: Long): Optional<TradingConfigEntity>

    @Query("""
        SELECT tc FROM TradingConfigEntity tc
        JOIN FETCH tc.user
        WHERE tc.enabled = true AND tc.autoTradingEnabled = true
    """)
    fun findAllEnabledWithAutoTrading(): List<TradingConfigEntity>

    @Query("""
        SELECT tc FROM TradingConfigEntity tc
        WHERE tc.enabled = true
    """)
    fun findAllEnabled(): List<TradingConfigEntity>

    @Query("SELECT COUNT(tc) FROM TradingConfigEntity tc WHERE tc.enabled = true AND tc.autoTradingEnabled = true")
    fun countAutoTradingEnabled(): Long
}
