package com.quantjumpstock.core.application.user

import com.quantjumpstock.core.domain.model.user.KisAccountType
import com.quantjumpstock.core.domain.model.user.UserKisAccount
import com.quantjumpstock.core.domain.port.output.UserKisAccountRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.infrastructure.security.AppSecretCipher
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * KIS 계정 도메인 서비스.
 *
 * A+ 모델 (단일 활성 + 7일 휴지통):
 * - 사용자당 활성 row 1개 + 휴지통 row 0~1개. partial unique 가 활성 row 단일성 강제.
 * - 모드 변경 (POST 가 다른 accountType) 시 BE 가 기존 활성을 자동 soft delete.
 * - 휴지통 row 는 7일 경과 시 [hardDeleteExpired] 가 영구 삭제 (Cloud Scheduler 호출).
 * - [restoreFromTrash] 는 휴지통/활성 row 를 스왑하되, 만료 시 [TrashExpiredException].
 */
@Service
class UserKisAccountService(
    private val userKisAccountRepository: UserKisAccountRepository,
    private val userRepository: UserRepository,
    private val appSecretCipher: AppSecretCipher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * KIS 계정 정보 등록/업데이트.
     *
     * 1. 활성 row 없음 → 신규 INSERT (모드 무관).
     * 2. 활성 row 있고 같은 `accountType` → 기존 update (휴지통 미발생).
     * 3. 활성 row 있고 **다른 `accountType`** → 기존을 soft delete (모드 전환).
     *    기존 휴지통이 있으면 hard delete 후 새 휴지통 생성 (사용자당 휴지통 1개 정책).
     *
     * Phase 1A PRE Task 7: AppSecret 신규 등록은 GCM(v2) 컬럼에 저장. v1(ECB) 는 빈 문자열.
     */
    @Transactional
    fun registerOrUpdateKisAccount(userId: String, request: KisAccountRequest): UserKisAccount {
        logger.info("🔐 Registering/Updating KIS account for user: $userId")

        val user = userRepository.findByUserId(userId)
            ?: throw IllegalArgumentException("User not found: $userId")

        val encryptedV2 = appSecretCipher.encryptForStorage(request.appSecret)
        val existingActive = user.id?.let { userKisAccountRepository.findByUserId(it) }

        return when {
            existingActive == null -> insertNew(user.id!!, request, encryptedV2)

            existingActive.accountType == request.accountType ->
                updateExisting(existingActive, request, encryptedV2)

            else -> {
                // 모드 전환: 기존 활성 → 휴지통 으로 이동, 신규 활성 row INSERT.
                evictTrashIfPresent(userId)
                userKisAccountRepository.save(existingActive.softDelete())
                logger.info("🗑️ Existing ${existingActive.accountType} moved to trash (mode change)")
                insertNew(user.id!!, request, encryptedV2)
            }
        }
    }

    private fun insertNew(userId: Long, req: KisAccountRequest, encryptedV2: String): UserKisAccount {
        val account = UserKisAccount.createNew(
            userId = userId,
            appKey = req.appKey,
            appSecretEncrypted = "",
            appSecretEncryptedV2 = encryptedV2,
            accountNumber = req.accountNumber,
            accountProductCode = req.accountProductCode,
            accountType = req.accountType,
            enabled = req.enabled
        )
        val saved = userKisAccountRepository.save(account)
        logger.info("✅ KIS account registered: type=${saved.accountType}")
        return saved
    }

    private fun updateExisting(
        existing: UserKisAccount,
        req: KisAccountRequest,
        encryptedV2: String
    ): UserKisAccount {
        val updated = existing.update(
            appKey = req.appKey,
            appSecretEncrypted = "",
            appSecretEncryptedV2 = encryptedV2,
            accountNumber = req.accountNumber,
            accountProductCode = req.accountProductCode,
            accountType = req.accountType,
            enabled = req.enabled
        )
        val saved = userKisAccountRepository.save(updated)
        logger.info("✅ KIS account updated (same type)")
        return saved
    }

    /** 사용자당 휴지통 1개 정책 — 기존 휴지통이 있으면 hard delete. */
    private fun evictTrashIfPresent(userId: String) {
        val trashed = userKisAccountRepository.findTrashedByUserUserId(userId) ?: return
        trashed.id?.let {
            userKisAccountRepository.deleteById(it)
            logger.info("♻️ Evicted previous trash row (id=$it) before new soft delete")
        }
    }

    /**
     * 활성 KIS 계정 조회 (없으면 IllegalArgumentException — 컨트롤러에서 404).
     * BE-1: 응답은 마스킹된 값.
     */
    @Transactional(readOnly = true)
    fun getKisAccount(userId: String): KisAccountResponse {
        val kisAccount = userKisAccountRepository.findActiveByUserUserId(userId)
            ?: throw IllegalArgumentException("KIS account not found or not active: $userId")
        return KisAccountResponse.from(kisAccount)
    }

    /** 휴지통 row 조회 (없으면 null — 컨트롤러에서 404). */
    @Transactional(readOnly = true)
    fun getTrashedKisAccount(userId: String): KisAccountResponse? {
        return userKisAccountRepository.findTrashedByUserUserId(userId)?.let { KisAccountResponse.from(it) }
    }

    /**
     * 활성 KIS 계정의 자동매매 토글. 휴지통 row 와 무관.
     */
    @Transactional
    fun toggleKisAccount(userId: String, enabled: Boolean) {
        val active = userKisAccountRepository.findActiveByUserUserId(userId)
            ?: throw IllegalArgumentException("KIS account not found: $userId")
        userKisAccountRepository.save(active.toggleEnabled(enabled))
        logger.info("✅ KIS account ${if (enabled) "enabled" else "disabled"} for user: $userId")
    }

    /**
     * 활성 row 를 휴지통으로 이동 (사용자 DELETE 요청).
     * 기존 휴지통 있으면 hard delete 후 새 휴지통 생성 (사용자당 1개 정책).
     */
    @Transactional
    fun softDeleteActiveKisAccount(userId: String) {
        val active = userKisAccountRepository.findActiveByUserUserId(userId)
            ?: throw IllegalArgumentException("KIS account not found: $userId")
        evictTrashIfPresent(userId)
        userKisAccountRepository.save(active.softDelete())
        logger.info("🗑️ KIS account soft-deleted (moved to trash) for user: $userId")
    }

    /**
     * 휴지통 row 를 활성으로 복원.
     *
     * - 휴지통 없음 → IllegalArgumentException (컨트롤러에서 404).
     * - 보관 기간(7일) 만료 → [TrashExpiredException] (컨트롤러에서 410 Gone).
     * - 현재 활성 row 가 있으면 스왑 (활성 → 휴지통, 휴지통 → 활성).
     *
     * 스왑 시 partial unique 위반을 피하기 위해 활성을 먼저 softDelete 한 뒤 휴지통을 restore 한다.
     * 동일 트랜잭션 내 두 save 가 발생하므로 commit 시점에 SQL 순서가 보장된다.
     */
    @Transactional
    fun restoreFromTrash(userId: String): KisAccountResponse {
        val trashed = userKisAccountRepository.findTrashedByUserUserId(userId)
            ?: throw IllegalArgumentException("Trashed KIS account not found: $userId")

        if (trashed.isExpiredInTrash()) {
            throw TrashExpiredException("휴지통 보관 기간(7일)이 만료되어 복원할 수 없습니다")
        }

        val currentActive = userKisAccountRepository.findActiveByUserUserId(userId)
        if (currentActive != null) {
            userKisAccountRepository.save(currentActive.softDelete())
            logger.info("↔️ Active row moved to trash (swap with restored row)")
        }

        val restored = userKisAccountRepository.save(trashed.restore())
        logger.info("♻️ KIS account restored from trash for user: $userId")
        return KisAccountResponse.from(restored)
    }

    /**
     * 7일 경과 휴지통 row hard delete. Cloud Scheduler 가 매일 1회 호출.
     *
     * @return 삭제된 row 수
     */
    @Transactional
    fun hardDeleteExpired(retentionDays: Long = 7): Int {
        val threshold = LocalDateTime.now().minusDays(retentionDays)
        val expired = userKisAccountRepository.findExpiredTrashed(threshold)
        expired.forEach { row ->
            row.id?.let { userKisAccountRepository.deleteById(it) }
        }
        if (expired.isNotEmpty()) {
            logger.info("🔥 Hard-deleted ${expired.size} expired KIS trash rows (older than $retentionDays days)")
        }
        return expired.size
    }

    /**
     * 복호화된 AppSecret 조회 (내부 사용 전용).
     *
     * Phase 1A PRE Task 7: v2(GCM) 컬럼 우선 복호화. v2 가 비어있으면 v1(ECB) fallback —
     * Task 6 재암호화 Runner 가 모든 row 의 v2 를 채울 때까지의 안전망이다.
     * V63 에서 v1 컬럼이 drop 되면 본 fallback 분기도 함께 제거된다.
     */
    @Transactional(readOnly = true)
    fun getDecryptedAppSecret(userId: String): String {
        val kisAccount = userKisAccountRepository.findActiveByUserUserId(userId)
            ?: throw IllegalArgumentException("KIS account not found: $userId")
        return appSecretCipher.decrypt(kisAccount.appSecretEncryptedV2, kisAccount.appSecretEncrypted)
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
 * KIS 계정 조회 응답 (FE 노출용).
 *
 * BE-1 보안: `appKey` / `accountNumber` 는 마스킹된 값만 반환 (도메인 메서드 사용).
 * `deletedAt` 은 활성 row 에서 null, 휴지통 row 에서 non-null.
 */
data class KisAccountResponse(
    val appKey: String,
    val accountNumber: String,
    val accountProductCode: String,
    val accountType: KisAccountType,
    val enabled: Boolean,
    val lastUsedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val deletedAt: LocalDateTime?
) {
    companion object {
        fun from(account: UserKisAccount): KisAccountResponse = KisAccountResponse(
            appKey = account.maskedAppKey(),
            accountNumber = account.maskedAccountNumber(),
            accountProductCode = account.accountProductCode,
            accountType = account.accountType,
            enabled = account.enabled,
            lastUsedAt = account.lastUsedAt,
            createdAt = account.createdAt,
            deletedAt = account.deletedAt
        )
    }
}

/** 휴지통 보관 기간 만료 시 발생. 컨트롤러가 410 Gone 으로 매핑. */
class TrashExpiredException(message: String) : RuntimeException(message)
