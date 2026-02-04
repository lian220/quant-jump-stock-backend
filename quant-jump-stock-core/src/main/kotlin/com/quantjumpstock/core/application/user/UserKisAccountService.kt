package com.quantjumpstock.core.application.user

import com.quantjumpstock.core.domain.model.user.KisAccountType
import com.quantjumpstock.core.domain.model.user.UserKisAccount
import com.quantjumpstock.core.domain.port.output.UserKisAccountRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.infrastructure.security.EncryptionService
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserKisAccountService(
    private val userKisAccountRepository: UserKisAccountRepository,
    private val userRepository: UserRepository,
    private val encryptionService: EncryptionService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * KIS 계정 정보 등록/업데이트
     * @param userId 사용자 ID
     * @param request KIS 계정 정보
     */
    @Transactional
    fun registerOrUpdateKisAccount(userId: String, request: KisAccountRequest): UserKisAccount {
        logger.info("🔐 Registering/Updating KIS account for user: $userId")

        // 1. 사용자 조회
        val user = userRepository.findByUserId(userId)
            ?: throw IllegalArgumentException("User not found: $userId")

        // 2. AppSecret 암호화
        val encryptedSecret = encryptionService.encrypt(request.appSecret)

        // 3. 기존 계정 확인
        val existingAccount = user.id?.let { userKisAccountRepository.findByUserId(it) }

        return if (existingAccount != null) {
            // 업데이트
            val updated = existingAccount.update(
                appKey = request.appKey,
                appSecretEncrypted = encryptedSecret,
                accountNumber = request.accountNumber,
                accountProductCode = request.accountProductCode,
                accountType = request.accountType,
                enabled = request.enabled
            )
            userKisAccountRepository.save(updated)
            logger.info("✅ KIS account updated for user: $userId")
            updated
        } else {
            // 신규 등록
            val newAccount = UserKisAccount.createNew(
                userId = user.id!!,
                appKey = request.appKey,
                appSecretEncrypted = encryptedSecret,
                accountNumber = request.accountNumber,
                accountProductCode = request.accountProductCode,
                accountType = request.accountType,
                enabled = request.enabled
            )
            userKisAccountRepository.save(newAccount)
            logger.info("✅ KIS account registered for user: $userId")
            newAccount
        }
    }

    /**
     * KIS 계정 정보 조회
     * @param userId 사용자 ID
     * @return KIS 계정 정보 (복호화된 Secret 제외)
     */
    @Transactional(readOnly = true)
    fun getKisAccount(userId: String): KisAccountResponse {
        val kisAccount = userKisAccountRepository.findActiveByUserUserId(userId)
            ?: throw IllegalArgumentException("KIS account not found or not active: $userId")

        return KisAccountResponse(
            appKey = kisAccount.appKey,
            accountNumber = kisAccount.accountNumber,
            accountProductCode = kisAccount.accountProductCode,
            accountType = kisAccount.accountType,
            enabled = kisAccount.enabled,
            lastUsedAt = kisAccount.lastUsedAt,
            createdAt = kisAccount.createdAt
        )
    }

    /**
     * KIS 계정 활성화/비활성화
     */
    @Transactional
    fun toggleKisAccount(userId: String, enabled: Boolean) {
        val user = userRepository.findByUserId(userId)
            ?: throw IllegalArgumentException("User not found: $userId")

        val kisAccount = user.id?.let { userKisAccountRepository.findByUserId(it) }
            ?: throw IllegalArgumentException("KIS account not found: $userId")

        val updated = kisAccount.toggleEnabled(enabled)
        userKisAccountRepository.save(updated)

        logger.info("✅ KIS account ${if (enabled) "enabled" else "disabled"} for user: $userId")
    }

    /**
     * 복호화된 AppSecret 조회 (내부 사용 전용)
     * @param userId 사용자 ID
     * @return 복호화된 AppSecret
     */
    @Transactional(readOnly = true)
    fun getDecryptedAppSecret(userId: String): String {
        val kisAccount = userKisAccountRepository.findActiveByUserUserId(userId)
            ?: throw IllegalArgumentException("KIS account not found: $userId")

        return encryptionService.decrypt(kisAccount.appSecretEncrypted)
    }
}

/**
 * KIS 계정 등록 요청
 */
data class KisAccountRequest(
    val appKey: String,
    val appSecret: String,  // 평문 (암호화되어 저장됨)
    val accountNumber: String,
    val accountProductCode: String = "01",
    val accountType: KisAccountType = KisAccountType.MOCK,
    val enabled: Boolean = true
)

/**
 * KIS 계정 조회 응답
 */
data class KisAccountResponse(
    val appKey: String,
    val accountNumber: String,
    val accountProductCode: String,
    val accountType: KisAccountType,
    val enabled: Boolean,
    val lastUsedAt: LocalDateTime?,
    val createdAt: LocalDateTime
)
