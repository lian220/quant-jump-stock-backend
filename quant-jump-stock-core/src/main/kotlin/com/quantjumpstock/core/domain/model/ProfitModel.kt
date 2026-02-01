package com.quantjumpstock.core.domain.model

import java.math.BigDecimal

/**
 * 보유 종목 정보 (수익률 포함)
 */
data class HoldingPosition(
    val ticker: String,                    // 종목 코드
    val name: String,                      // 종목명
    val quantity: Int,                     // 보유 수량
    val averagePrice: BigDecimal,          // 매수 평균가
    val currentPrice: BigDecimal,          // 현재가
    val evaluationAmount: BigDecimal,      // 평가 금액
    val profitAmount: BigDecimal,          // 평가 손익 금액
    val profitRate: BigDecimal,            // 수익률 (%)
    val currency: String = "USD",          // 통화
    val exchange: String = "NASD"          // 거래소
)

/**
 * 계좌 전체 요약
 */
data class AccountSummary(
    val totalPurchaseAmount: BigDecimal,   // 총 매수 금액
    val totalEvaluationAmount: BigDecimal, // 총 평가 금액
    val realizedProfit: BigDecimal,        // 실현 손익
    val unrealizedProfit: BigDecimal,      // 미실현 손익 (평가 손익)
    val totalProfit: BigDecimal,           // 총 손익 (실현 + 미실현)
    val totalProfitRate: BigDecimal,       // 전체 수익률 (%)
    val currency: String = "USD"           // 기준 통화
)

/**
 * 잔고 및 수익률 조회 응답
 */
data class BalanceWithProfitResponse(
    val userId: String,                    // 사용자 ID
    val accountNumber: String,             // 계좌번호
    val holdings: List<HoldingPosition>,   // 보유 종목 리스트
    val summary: AccountSummary,           // 계좌 전체 요약
    val cashBalance: BigDecimal,           // 현금 잔고
    val totalAssets: BigDecimal,           // 총 자산 (현금 + 평가금액)
    val timestamp: String                  // 조회 시간
)
