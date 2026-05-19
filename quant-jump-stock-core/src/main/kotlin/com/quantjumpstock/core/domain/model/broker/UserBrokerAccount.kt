package com.quantjumpstock.core.domain.model.broker

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 사용자 증권사 계좌 도메인 모델 (Phase 1B v2.1).
 *
 * 4-tuple `(user_id, broker, account_type, account_number)` 활성 unique.
 * 같은 사용자가 KIS + Toss × MOCK/REAL × 계좌번호 N개 동시 보유 가능.
 *
 * - `deletedAt == null` : 활성. partial unique 가 4-tuple 단위 강제.
 * - `deletedAt != null` : 휴지통. 7일 경과 시 Cloud Scheduler 가 hard delete.
 *
 * 기존 `UserKisAccount` 후속. V70 (4주 운영 후) 에서 `user_kis_accounts` 폐기.
 *
 * 확장성 설계 (backend-architect spec-panel 권고):
 * - `credentials: BrokerCredentials` sealed value object — broker 추가가 닫힌 변경 (OCP).
 * - `accountProductCode: String?` — KIS 고유 개념. 다른 broker 에선 NULL.
 */
data class UserBrokerAccount(
    val id: Long? = null,
    val userId: Long,
    val broker: Broker,
    val accountType: AccountType,
    val accountNumber: String,
    /** broker-specific. KIS='01' (해외주식), Toss=null. */
    val accountProductCode: String? = null,
    val accountAlias: String? = null,
    val credentials: BrokerCredentials,
    val enabled: Boolean = true,
    val lastUsedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val deletedAt: LocalDateTime? = null,
) {
    /** 계좌번호 + 상품코드 결합 (KIS API 호출용). productCode 없으면 계좌번호만. */
    fun getFullAccountNumber(): String = accountNumber + (accountProductCode ?: "")

    /** 휴지통 row 인지 (deletedAt 마커). */
    fun isTrashed(): Boolean = deletedAt != null

    /** 휴지통 보관 기간 만료 여부 (기본 7일). */
    fun isExpiredInTrash(now: LocalDateTime = LocalDateTime.now(), retentionDays: Long = 7): Boolean =
        deletedAt?.let { ChronoUnit.DAYS.between(it, now) >= retentionDays } ?: false

    /** 활성 → 휴지통. */
    fun softDelete(now: LocalDateTime = LocalDateTime.now()): UserBrokerAccount = copy(
        deletedAt = now,
        enabled = false,
        updatedAt = now
    )

    /** 휴지통 → 활성. */
    fun restore(now: LocalDateTime = LocalDateTime.now()): UserBrokerAccount = copy(
        deletedAt = null,
        enabled = true,
        updatedAt = now
    )

    /**
     * external key 마스킹: 앞 4 + `********` + 뒤 4. 8자 이하면 전체 별표.
     * KIS appKey, Toss client_id 등 broker 별 다른 의미.
     */
    fun maskedExternalKey(): String {
        val key = credentials.externalKey
        return if (key.length <= 8) "*".repeat(key.length.coerceAtLeast(4))
        else "${key.take(4)}********${key.takeLast(4)}"
    }

    /**
     * 계좌번호 마스킹: `****` + 뒤 4 (+ `-{상품코드}` 있을 때).
     * 예: `12345678-01` → `****5678-01`, productCode null 이면 `****5678`.
     */
    fun maskedAccountNumber(): String {
        val masked = if (accountNumber.length <= 4) "*".repeat(accountNumber.length)
        else "*".repeat(accountNumber.length - 4) + accountNumber.takeLast(4)
        return if (accountProductCode != null) "$masked-$accountProductCode" else masked
    }

    /**
     * 표시명 — 사용자 별명이 있으면 그것, 없으면 자동 생성.
     * 예: "메인 계좌" 또는 "KIS REAL ****5678-01"
     */
    fun displayName(): String = accountAlias ?: "$broker $accountType ${maskedAccountNumber()}"

    /**
     * 계정 정보 업데이트 (Command 형태 — 향후 필드 추가 시 시그니처 안정성).
     */
    fun update(command: UpdateAccountCommand): UserBrokerAccount = copy(
        credentials = command.credentials,
        accountNumber = command.accountNumber,
        accountProductCode = command.accountProductCode,
        accountAlias = command.accountAlias,
        enabled = command.enabled,
        updatedAt = LocalDateTime.now()
    )

    /** 활성/비활성 토글. */
    fun toggleEnabled(enabled: Boolean): UserBrokerAccount = copy(
        enabled = enabled,
        updatedAt = LocalDateTime.now()
    )

    /** 마지막 사용 시간 업데이트. */
    fun markAsUsed(): UserBrokerAccount = copy(
        lastUsedAt = LocalDateTime.now()
    )

    companion object {
        fun createNew(
            userId: Long,
            broker: Broker,
            accountType: AccountType,
            accountNumber: String,
            accountProductCode: String? = null,
            accountAlias: String? = null,
            credentials: BrokerCredentials,
            enabled: Boolean = true,
        ): UserBrokerAccount = UserBrokerAccount(
            userId = userId,
            broker = broker,
            accountType = accountType,
            accountNumber = accountNumber,
            accountProductCode = accountProductCode,
            accountAlias = accountAlias,
            credentials = credentials,
            enabled = enabled,
        )
    }
}

/**
 * 계좌 업데이트 명령 (Command Object).
 * 향후 broker 별 필드 추가 시 시그니처 안정성. 메서드 파라미터 폭발 방지.
 */
data class UpdateAccountCommand(
    val credentials: BrokerCredentials,
    val accountNumber: String,
    val accountProductCode: String?,
    val accountAlias: String?,
    val enabled: Boolean,
)
