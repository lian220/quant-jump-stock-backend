package com.quantjumpstock.core.domain.model.broker

/**
 * 계좌 유형. broker 무관.
 *
 * - MOCK: 모의 투자 — 가상 자금, 실전과 같은 API 흐름으로 검증
 * - REAL: 실전 투자 — 실제 자금 노출. 첫 활성화 시 24h cooldown (Phase 1B §6.3)
 *
 * 기존 `KisAccountType` 의 후속. Phase 1B 마이그레이션 동안 두 enum 공존.
 */
enum class AccountType { MOCK, REAL }
