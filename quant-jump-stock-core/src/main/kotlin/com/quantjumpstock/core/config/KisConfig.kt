package com.quantjumpstock.core.config

import com.quantjumpstock.core.adapter.output.persistence.jpa.AccountTypeEntityEnum

/**
 * KIS API URL 상수 제공.
 *
 * 사용자별 KIS 계정 정보는 user_broker_accounts 테이블에서 관리 (broker='KIS').
 */
object KisConfig {
    const val PRODUCTION_URL = "https://openapi.koreainvestment.com:9443"
    const val SIMULATION_URL = "https://openapivts.koreainvestment.com:29443"

    fun getBaseUrlForAccountType(accountType: AccountTypeEntityEnum): String {
        return when (accountType) {
            AccountTypeEntityEnum.REAL -> PRODUCTION_URL
            AccountTypeEntityEnum.MOCK -> SIMULATION_URL
        }
    }
}
