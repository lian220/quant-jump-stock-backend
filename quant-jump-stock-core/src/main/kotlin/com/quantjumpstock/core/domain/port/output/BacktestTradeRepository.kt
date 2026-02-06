package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.backtest.BacktestTrade

/**
 * BacktestTrade Repository Port - 도메인 포트
 *
 * 특징:
 * - 도메인 모델(BacktestTrade)만 사용
 * - JPA Entity 타입 없음
 * - 구현은 adapter/output/persistence/에서 수행
 */
interface BacktestTradeRepository {

    /**
     * 백테스트 거래 내역 일괄 저장
     */
    fun saveAll(trades: List<BacktestTrade>): List<BacktestTrade>
}
