package com.quantjumpstock.core.domain.model.user

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 사용자 KIS 계정 도메인 모델
 *
 * A+ 모델 (단일 계좌 + 7일 휴지통): `deletedAt` 으로 활성/휴지통 구분.
 * - `deletedAt == null`  : 활성. partial unique 가 사용자당 1개 강제.
 * - `deletedAt != null`  : 휴지통. 7일 경과 시 Cloud Scheduler 가 hard delete.
 */
data class UserKisAccount(
    val id: Long? = null,
    val userId: Long,
    val appKey: String,
    val appSecretEncrypted: String,
    val appSecretEncryptedV2: String? = null,
    val accountNumber: String,
    val accountProductCode: String = "01",
    val accountType: KisAccountType = KisAccountType.MOCK,
    val enabled: Boolean = true,
    val lastUsedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val deletedAt: LocalDateTime? = null
) {
    /**
     * 계좌번호 전체 반환 (계좌번호 + 상품코드)
     */
    fun getFullAccountNumber(): String = accountNumber + accountProductCode

    /** 휴지통 row 인지 (deletedAt 마커). */
    fun isTrashed(): Boolean = deletedAt != null

    /**
     * 휴지통 보관 기간 만료 여부 (기본 7일).
     * 만료 시 사용자는 복원할 수 없고 Cloud Scheduler 가 hard delete 한다.
     */
    fun isExpiredInTrash(now: LocalDateTime = LocalDateTime.now(), retentionDays: Long = 7): Boolean =
        deletedAt?.let { ChronoUnit.DAYS.between(it, now) >= retentionDays } ?: false

    /** 활성 row → 휴지통 row 로 표시 (deletedAt 설정). */
    fun softDelete(now: LocalDateTime = LocalDateTime.now()): UserKisAccount = copy(
        deletedAt = now,
        enabled = false,
        updatedAt = now
    )

    /** 휴지통 row → 활성 row 로 복구 (deletedAt 해제). */
    fun restore(now: LocalDateTime = LocalDateTime.now()): UserKisAccount = copy(
        deletedAt = null,
        enabled = true,
        updatedAt = now
    )

    /**
     * APP KEY 마스킹 (BE-1): 앞 4 + `****` + 뒤 4 외 미노출.
     * 8자 이하면 전체 별표.
     */
    fun maskedAppKey(): String =
        if (appKey.length <= 8) "*".repeat(appKey.length.coerceAtLeast(4))
        else "${appKey.take(4)}********${appKey.takeLast(4)}"

    /**
     * 계좌번호 마스킹: `****` + 뒤 4 + `-{상품코드}`.
     * 예: `12345678-01` → `****5678-01`.
     */
    fun maskedAccountNumber(): String {
        val masked = if (accountNumber.length <= 4) "*".repeat(accountNumber.length)
        else "*".repeat(accountNumber.length - 4) + accountNumber.takeLast(4)
        return "$masked-$accountProductCode"
    }

    /**
     * 계정 정보 업데이트
     */
    fun update(
        appKey: String,
        appSecretEncrypted: String,
        appSecretEncryptedV2: String?,
        accountNumber: String,
        accountProductCode: String,
        accountType: KisAccountType,
        enabled: Boolean
    ): UserKisAccount = copy(
        appKey = appKey,
        appSecretEncrypted = appSecretEncrypted,
        appSecretEncryptedV2 = appSecretEncryptedV2,
        accountNumber = accountNumber,
        accountProductCode = accountProductCode,
        accountType = accountType,
        enabled = enabled,
        updatedAt = LocalDateTime.now()
    )

    /**
     * 활성화/비활성화 토글
     */
    fun toggleEnabled(enabled: Boolean): UserKisAccount = copy(
        enabled = enabled,
        updatedAt = LocalDateTime.now()
    )

    /**
     * 마지막 사용 시간 업데이트
     */
    fun markAsUsed(): UserKisAccount = copy(
        lastUsedAt = LocalDateTime.now()
    )

    companion object {
        fun createNew(
            userId: Long,
            appKey: String,
            appSecretEncrypted: String,
            appSecretEncryptedV2: String? = null,
            accountNumber: String,
            accountProductCode: String = "01",
            accountType: KisAccountType = KisAccountType.MOCK,
            enabled: Boolean = true
        ): UserKisAccount = UserKisAccount(
            userId = userId,
            appKey = appKey,
            appSecretEncrypted = appSecretEncrypted,
            appSecretEncryptedV2 = appSecretEncryptedV2,
            accountNumber = accountNumber,
            accountProductCode = accountProductCode,
            accountType = accountType,
            enabled = enabled
        )
    }
}
