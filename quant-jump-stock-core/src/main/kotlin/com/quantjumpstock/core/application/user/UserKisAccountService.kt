package com.quantjumpstock.core.application.user

import com.quantjumpstock.core.domain.model.user.KisAccountType
import com.quantjumpstock.core.domain.model.user.UserKisAccount
import com.quantjumpstock.core.domain.port.output.UserKisAccountRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.infrastructure.security.EncryptionService
import com.quantjumpstock.core.infrastructure.security.EncryptionServiceGcm
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserKisAccountService(
    private val userKisAccountRepository: UserKisAccountRepository,
    private val userRepository: UserRepository,
    private val encryptionServiceLegacy: EncryptionService,
    private val encryptionServiceGcm: EncryptionServiceGcm
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * KIS 계정 정보 등록/업데이트
     *
     * Phase 1A PRE Task 7: AppSecret 신규 등록은 GCM(v2) 컬럼에 저장한다.
     * v1(ECB) 컬럼은 빈 문자열로 마킹되며, Task 8 (V61) 에서 drop 예정.
     */
    @Transactional
    fun registerOrUpdateKisAccount(userId: String, request: KisAccountRequest): UserKisAccount {
        logger.info("🔐 Registering/Updating KIS account for user: $userId")

        val user = userRepository.findByUserId(userId)
            ?: throw IllegalArgumentException("User not found: $userId")

        val encryptedV2 = encryptionServiceGcm.encrypt(request.appSecret)

        val existingAccount = user.id?.let { userKisAccountRepository.findByUserId(it) }

        return if (existingAccount != null) {
            val updated = existingAccount.update(
                appKey = request.appKey,
                appSecretEncrypted = "",
                appSecretEncryptedV2 = encryptedV2,
                accountNumber = request.accountNumber,
                accountProductCode = request.accountProductCode,
                accountType = request.accountType,
                enabled = request.enabled
            )
            val saved = userKisAccountRepository.save(updated)
            logger.info("✅ KIS account updated for user: $userId")
            saved
        } else {
            val newAccount = UserKisAccount.createNew(
                userId = user.id!!,
                appKey = request.appKey,
                appSecretEncrypted = "",
                appSecretEncryptedV2 = encryptedV2,
                accountNumber = request.accountNumber,
                accountProductCode = request.accountProductCode,
                accountType = request.accountType,
                enabled = request.enabled
            )
            val saved = userKisAccountRepository.save(newAccount)
            logger.info("✅ KIS account registered for user: $userId")
            saved
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
     *
     * Phase 1A PRE Task 7: v2(GCM) 컬럼 우선 복호화. v2 가 비어있으면 v1(ECB) fallback
     * — Task 6 재암호화 Runner 가 모든 row 의 v2 를 채울 때까지의 안전망이다.
     * Task 8 (V61) 에서 v1 컬럼이 drop 되면 본 fallback 분기도 함께 제거된다.
     */
    @Transactional(readOnly = true)
    fun getDecryptedAppSecret(userId: String): String {
        val kisAccount = userKisAccountRepository.findActiveByUserUserId(userId)
            ?: throw IllegalArgumentException("KIS account not found: $userId")

        return kisAccount.appSecretEncryptedV2
            ?.let { encryptionServiceGcm.decrypt(it) }
            ?: encryptionServiceLegacy.decrypt(kisAccount.appSecretEncrypted)
    }
}

/**
 * KIS 계정 등록 요청.
 *
 * 입력 검증 (Phase 1A PRE 보안 리뷰 H-3 반영):
 *  - appKey/appSecret 길이 상한으로 대용량 페이로드 차단.
 *  - accountNumber 는 KIS 규격(숫자 8자리)만 허용.
 *  - accountProductCode 는 2자리 숫자(예: "01" 해외주식).
 */
data class KisAccountRequest(
    @field:NotBlank
    @field:Size(min = 10, max = 100, message = "appKey 길이는 10~100자")
    val appKey: String,

    @field:NotBlank
    @field:Size(min = 10, max = 200, message = "appSecret 길이는 10~200자")
    val appSecret: String,  // 평문 (암호화되어 저장됨)

    @field:NotBlank
    @field:Pattern(regexp = "^\\d{8}$", message = "accountNumber 는 숫자 8자리")
    val accountNumber: String,

    @field:Pattern(regexp = "^\\d{2}$", message = "accountProductCode 는 숫자 2자리")
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
