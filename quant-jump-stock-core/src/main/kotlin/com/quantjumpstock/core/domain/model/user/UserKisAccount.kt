package com.quantjumpstock.core.domain.model.user

import java.time.LocalDateTime

/**
 * 사용자 KIS 계정 도메인 모델
 */
data class UserKisAccount(
    val id: Long? = null,
    val userId: Long,
    val appKey: String,
    val appSecretEncrypted: String,
    val accountNumber: String,
    val accountProductCode: String = "01",
    val accountType: KisAccountType = KisAccountType.MOCK,
    val enabled: Boolean = true,
    val lastUsedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 계좌번호 전체 반환 (계좌번호 + 상품코드)
     */
    fun getFullAccountNumber(): String = accountNumber + accountProductCode

    /**
     * 계정 정보 업데이트
     */
    fun update(
        appKey: String,
        appSecretEncrypted: String,
        accountNumber: String,
        accountProductCode: String,
        accountType: KisAccountType,
        enabled: Boolean
    ): UserKisAccount = copy(
        appKey = appKey,
        appSecretEncrypted = appSecretEncrypted,
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
            accountNumber: String,
            accountProductCode: String = "01",
            accountType: KisAccountType = KisAccountType.MOCK,
            enabled: Boolean = true
        ): UserKisAccount = UserKisAccount(
            userId = userId,
            appKey = appKey,
            appSecretEncrypted = appSecretEncrypted,
            accountNumber = accountNumber,
            accountProductCode = accountProductCode,
            accountType = accountType,
            enabled = enabled
        )
    }
}
