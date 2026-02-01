package com.quantjumpstock.core.domain.trading.port.output

/**
 * Trading API Port (Output Port)
 * 외부 증권사 API와의 연동을 추상화합니다.
 *
 * ⚠️ 모든 메서드는 userId를 명시적으로 받아 사용자별 KIS 계정으로 API 호출
 */
interface TradingApiPort {

    /**
     * Access Token 조회
     * @param userId 사용자 ID (user_kis_accounts 테이블에서 조회)
     * @return Access Token
     */
    fun getAccessToken(userId: String): String

    /**
     * 해외 주식 잔고 조회
     * @param userId 사용자 ID
     * @param exchange 거래소 코드 (기본값: NASD)
     * @return 잔고 정보 Map
     */
    fun getOverseasBalance(userId: String, exchange: String = "NASD"): Map<String, Any>

    /**
     * 해외 주식 주문
     * @param userId 사용자 ID
     * @param ticker 종목 코드
     * @param orderType 주문 유형 (BUY/SELL)
     * @param quantity 수량
     * @param price 가격
     * @return 주문 결과
     */
    fun placeOrder(
        userId: String,
        ticker: String,
        orderType: String,
        quantity: Int,
        price: String
    ): Map<String, Any>
}
