package com.quantjumpstock.core.domain.port.output

/**
 * UserTier Repository Port - 도메인 포트
 * 백테스트 Rate Limiting을 위한 사용자 티어 조회/수정
 */
interface UserTierRepository {

    /**
     * 백테스트 실행 가능 여부 확인
     * @return BacktestLimitInfo (allowed, remaining, dailyLimit, tier)
     */
    fun checkBacktestLimit(userId: String): BacktestLimitInfo

    /**
     * 백테스트 카운트 증가 (tier 레코드 없으면 자동 생성)
     */
    fun incrementBacktestCount(userId: String)

    /**
     * 원자적 체크 + 카운트 증가 (TOCTOU 방지)
     * 한도 내이면 카운트 증가 후 결과 반환, 초과면 allowed=false 반환
     */
    fun checkAndIncrementBacktestCount(userId: String): BacktestLimitInfo

    /**
     * 신규 사용자에 대해 무료 티어 생성
     * 이미 존재하면 무시
     */
    fun createFreeTierForUser(userId: String)

    /**
     * 어드민용: userDbId 기반 티어 상세 조회
     */
    fun getAdminTierDetails(userDbId: Long): AdminTierDetails?

    /**
     * 어드민용: 사용자 티어 업데이트
     */
    fun updateAdminTier(
        userDbId: Long,
        tier: String,
        startedAt: java.time.LocalDateTime?,
        expiresAt: java.time.LocalDateTime?
    )
}

data class AdminTierDetails(
    val tier: String,
    val startedAt: java.time.LocalDateTime?,
    val expiresAt: java.time.LocalDateTime?,
    val backtestCountToday: Int
)

data class BacktestLimitInfo(
    val allowed: Boolean,
    val remaining: Int,
    val dailyLimit: Int,
    val tier: String,
    val message: String? = null
)
