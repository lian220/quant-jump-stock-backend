package com.quantjumpstock.core.config

import com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType

/**
 * KIS API 설정
 *
 * ⚠️ 사용자별 KIS 계정 정보는 user_kis_accounts 테이블에서 관리됩니다
 * 이 클래스는 URL 상수만 제공합니다
 */
object KisConfig {
    const val PRODUCTION_URL = "https://openapi.koreainvestment.com:9443"
    const val SIMULATION_URL = "https://openapivts.koreainvestment.com:29443"

    /**
     * 계정 타입에 따라 적절한 KIS API URL 반환
     * @param accountType REAL(실전) 또는 MOCK(모의)
     * @return KIS API Base URL
     */
    fun getBaseUrlForAccountType(accountType: KisAccountType): String {
        return when (accountType) {
            KisAccountType.REAL -> PRODUCTION_URL
            KisAccountType.MOCK -> SIMULATION_URL
        }
    }
}
