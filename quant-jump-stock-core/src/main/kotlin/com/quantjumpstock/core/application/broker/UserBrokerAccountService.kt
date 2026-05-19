package com.quantjumpstock.core.application.broker

import com.quantjumpstock.core.domain.event.broker.UserBrokerAccountEvent
import com.quantjumpstock.core.domain.model.broker.AccountType
import com.quantjumpstock.core.domain.model.broker.Broker
import com.quantjumpstock.core.domain.model.broker.BrokerCredentials
import com.quantjumpstock.core.domain.model.broker.UpdateAccountCommand
import com.quantjumpstock.core.domain.model.broker.UserBrokerAccount
import com.quantjumpstock.core.domain.port.output.UserBrokerAccountRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.infrastructure.security.AppSecretCipher
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * UserBrokerAccount 도메인 서비스 (Phase 1B v2.1).
 *
 * - 4-tuple (user_id, broker, account_type, account_number) 활성 unique.
 * - 사용자가 KIS + Toss × MOCK/REAL × 계좌번호 N개 동시 보유 가능.
 * - 7일 휴지통 soft delete. 만료 row 는 Cloud Scheduler 가 hard delete.
 *
 * 보안 (IDOR 가드):
 * - 모든 mutating 메서드는 `requestedUserId` (외부 식별자) + `accountId` 조합 검증.
 * - account.userId 와 requestedUserId 가 가리키는 user 의 id 가 일치하지 않으면 `ForbiddenException`.
 */
@Service
class UserBrokerAccountService(
    private val brokerAccountRepository: UserBrokerAccountRepository,
    private val userRepository: UserRepository,
    private val appSecretCipher: AppSecretCipher,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 신규 계좌 등록. 같은 4-tuple 이 활성 row 로 존재하면 IllegalStateException (Controller=409). */
    @Transactional
    fun register(requestedUserId: String, request: BrokerAccountRequest): BrokerAccountResponse {
        val user = userRepository.findByUserId(requestedUserId)
            ?: throw IllegalArgumentException("User not found: $requestedUserId")
        val userId = user.id ?: throw IllegalStateException("User has no id: $requestedUserId")

        val existingActive = brokerAccountRepository.findActiveByUserIdAndKey(
            userId, request.broker, request.accountType, request.accountNumber,
        )
        if (existingActive != null) {
            throw IllegalStateException(
                "Active account already exists: broker=${request.broker} type=${request.accountType} " +
                    "number=${request.accountNumber}",
            )
        }

        val encryptedSecret = appSecretCipher.encryptForStorage(request.appSecret)
        val credentials = buildCredentials(request.broker, request.appKey, encryptedSecret)

        val newAccount = UserBrokerAccount.createNew(
            userId = userId,
            broker = request.broker,
            accountType = request.accountType,
            accountNumber = request.accountNumber,
            accountProductCode = request.accountProductCode,
            accountAlias = request.accountAlias,
            credentials = credentials,
            enabled = request.enabled,
        )

        val saved = brokerAccountRepository.save(newAccount)
        logger.info(
            "✅ Broker account registered: id={} broker={} type={}",
            saved.id, saved.broker, saved.accountType,
        )

        eventPublisher.publishEvent(
            UserBrokerAccountEvent.Registered(
                accountId = saved.id!!,
                userId = saved.userId,
                broker = saved.broker,
                accountType = saved.accountType,
            ),
        )
        return BrokerAccountResponse.from(saved)
    }

    /** 사용자의 모든 broker 계좌 (활성 + 휴지통). */
    @Transactional(readOnly = true)
    fun listAll(requestedUserId: String): BrokerAccountListResponse {
        val user = userRepository.findByUserId(requestedUserId)
            ?: throw IllegalArgumentException("User not found: $requestedUserId")
        val userId = user.id!!

        return BrokerAccountListResponse(
            active = brokerAccountRepository.findAllActiveByUserId(userId).map { BrokerAccountResponse.from(it) },
            trashed = brokerAccountRepository.findAllTrashedByUserId(userId).map { BrokerAccountResponse.from(it) },
        )
    }

    /** ID 기반 단건 조회 + IDOR 가드. */
    @Transactional(readOnly = true)
    fun getOne(requestedUserId: String, accountId: Long): BrokerAccountResponse =
        BrokerAccountResponse.from(loadOwnedAccount(requestedUserId, accountId))

    /** 계좌 정보 부분 업데이트 (credentials / alias / enabled). */
    @Transactional
    fun update(
        requestedUserId: String,
        accountId: Long,
        request: BrokerAccountUpdateRequest,
    ): BrokerAccountResponse {
        val current = loadOwnedAccount(requestedUserId, accountId)
        if (current.isTrashed()) {
            throw IllegalStateException("Cannot update trashed account: id=$accountId. Restore first.")
        }

        val newSecret = request.appSecret?.let { appSecretCipher.encryptForStorage(it) }
            ?: current.credentials.externalSecretEncrypted
        val newKey = request.appKey ?: current.credentials.externalKey
        val newCredentials = buildCredentials(current.broker, newKey, newSecret)

        val updated = current.update(
            UpdateAccountCommand(
                credentials = newCredentials,
                accountNumber = request.accountNumber ?: current.accountNumber,
                accountProductCode = request.accountProductCode ?: current.accountProductCode,
                accountAlias = request.accountAlias ?: current.accountAlias,
                enabled = request.enabled ?: current.enabled,
            ),
        )
        val saved = brokerAccountRepository.save(updated)

        eventPublisher.publishEvent(
            UserBrokerAccountEvent.Updated(
                accountId = saved.id!!, userId = saved.userId,
                broker = saved.broker, accountType = saved.accountType,
            ),
        )
        return BrokerAccountResponse.from(saved)
    }

    /** 자동매매 토글. */
    @Transactional
    fun toggle(requestedUserId: String, accountId: Long, enabled: Boolean): BrokerAccountResponse {
        val current = loadOwnedAccount(requestedUserId, accountId)
        if (current.isTrashed()) {
            throw IllegalStateException("Cannot toggle trashed account: id=$accountId")
        }
        val saved = brokerAccountRepository.save(current.toggleEnabled(enabled))
        return BrokerAccountResponse.from(saved)
    }

    /** 활성 → 휴지통 (soft delete). 같은 4-tuple 의 옛 휴지통 row 가 있으면 hard delete. */
    @Transactional
    fun softDelete(requestedUserId: String, accountId: Long) {
        val current = loadOwnedAccount(requestedUserId, accountId)
        if (current.isTrashed()) return  // 멱등성

        // 같은 4-tuple 의 휴지통 row 가 있으면 hard delete (사용자당 type 별 휴지통 1개 정책)
        // 4-tuple 정확 매칭은 별도 쿼리가 없어 전체 trashed 중 매칭 row 찾음.
        brokerAccountRepository.findAllTrashedByUserId(current.userId)
            .firstOrNull {
                it.broker == current.broker &&
                    it.accountType == current.accountType &&
                    it.accountNumber == current.accountNumber
            }
            ?.let { brokerAccountRepository.deleteById(it.id!!) }

        val trashed = brokerAccountRepository.save(current.softDelete())
        logger.info("🗑️ Broker account soft-deleted: id={}", trashed.id)

        eventPublisher.publishEvent(
            UserBrokerAccountEvent.Trashed(
                accountId = trashed.id!!, userId = trashed.userId,
                broker = trashed.broker, accountType = trashed.accountType,
            ),
        )
    }

    /** 휴지통 → 활성. 같은 4-tuple 의 현재 활성 row 가 있으면 그것을 휴지통으로 swap. */
    @Transactional
    fun restore(requestedUserId: String, accountId: Long): BrokerAccountResponse {
        val trashed = loadOwnedAccount(requestedUserId, accountId)
        if (!trashed.isTrashed()) {
            throw IllegalStateException("Account is not in trash: id=$accountId")
        }
        if (trashed.isExpiredInTrash()) {
            throw TrashExpiredException("휴지통 보관 기간(7일)이 만료되어 복원할 수 없습니다")
        }

        // 같은 4-tuple 의 활성 row swap
        brokerAccountRepository.findActiveByUserIdAndKey(
            trashed.userId, trashed.broker, trashed.accountType, trashed.accountNumber,
        )?.let { currentActive ->
            brokerAccountRepository.save(currentActive.softDelete())
            logger.info("↔️ Active ${trashed.broker}/${trashed.accountType} moved to trash (swap with restored)")
        }

        val restored = brokerAccountRepository.save(trashed.restore())
        logger.info("♻️ Broker account restored: id={}", restored.id)

        eventPublisher.publishEvent(
            UserBrokerAccountEvent.Restored(
                accountId = restored.id!!, userId = restored.userId,
                broker = restored.broker, accountType = restored.accountType,
            ),
        )
        return BrokerAccountResponse.from(restored)
    }

    /** Cloud Scheduler 가 매일 1회 호출. 7일 경과 휴지통 row hard delete. */
    @Transactional
    fun hardDeleteExpired(retentionDays: Long = 7): Int {
        val threshold = LocalDateTime.now().minusDays(retentionDays)
        val expired = brokerAccountRepository.findExpiredTrashed(threshold)
        expired.forEach { row -> row.id?.let { brokerAccountRepository.deleteById(it) } }
        if (expired.isNotEmpty()) {
            logger.info("🔥 Hard-deleted ${expired.size} expired broker account trash rows")
        }
        return expired.size
    }

    // ===== Private helpers =====

    /**
     * IDOR 가드: accountId 가 requestedUserId 의 row 인지 확인 후 반환.
     * 일치 안 하면 `ForbiddenException` (보안 로그 + 향후 Slack 알림 가능).
     */
    private fun loadOwnedAccount(requestedUserId: String, accountId: Long): UserBrokerAccount {
        val account = brokerAccountRepository.findById(accountId)
            ?: throw IllegalArgumentException("Broker account not found: $accountId")
        val user = userRepository.findByUserId(requestedUserId)
            ?: throw IllegalArgumentException("User not found: $requestedUserId")

        if (account.userId != user.id) {
            logger.warn(
                "🚨 IDOR attempt: user={} tried to access broker account id={} owned by user_id={}",
                requestedUserId, accountId, account.userId,
            )
            throw ForbiddenException("Account does not belong to user: $requestedUserId")
        }
        return account
    }

    /**
     * broker 별 credentials 변종 빌드.
     * DB 의 broker-agnostic 컬럼 (app_key + app_secret_encrypted) 을 broker 의미로 재해석.
     */
    private fun buildCredentials(
        broker: Broker,
        externalKey: String,
        encryptedSecret: String,
    ): BrokerCredentials = when (broker) {
        Broker.KIS -> BrokerCredentials.Kis(appKey = externalKey, appSecretEncrypted = encryptedSecret)
        Broker.TOSS -> BrokerCredentials.TossOAuth(clientId = externalKey, refreshTokenEncrypted = encryptedSecret)
    }
}

// ===== DTOs =====

/**
 * 신규 계좌 등록 요청.
 *
 * 입력 검증:
 *  - appKey/appSecret 길이 상한
 *  - accountNumber 8자리 숫자 (KIS) — broker 별 다른 패턴 가능, MVP 는 KIS 패턴 기본
 *  - accountProductCode KIS 한정 2자리 숫자 (null 허용)
 */
data class BrokerAccountRequest(
    val broker: Broker,
    val accountType: AccountType,

    @field:NotBlank
    @field:Pattern(regexp = "^\\d{8,12}$", message = "accountNumber 는 숫자 8~12자리")
    val accountNumber: String,

    @field:Pattern(regexp = "^\\d{2}$", message = "accountProductCode 는 숫자 2자리 또는 null")
    val accountProductCode: String? = null,

    @field:Size(max = 50, message = "accountAlias 는 50자 이하")
    val accountAlias: String? = null,

    @field:NotBlank
    @field:Size(min = 10, max = 100, message = "appKey 길이는 10~100자")
    val appKey: String,

    @field:NotBlank
    @field:Size(min = 10, max = 200, message = "appSecret 길이는 10~200자")
    val appSecret: String,

    val enabled: Boolean = true,
)

/**
 * 계좌 부분 업데이트 요청 (PATCH). 모든 필드 optional.
 */
data class BrokerAccountUpdateRequest(
    @field:Pattern(regexp = "^\\d{8,12}$") val accountNumber: String? = null,
    @field:Pattern(regexp = "^\\d{2}$") val accountProductCode: String? = null,
    @field:Size(max = 50) val accountAlias: String? = null,
    @field:Size(min = 10, max = 100) val appKey: String? = null,
    @field:Size(min = 10, max = 200) val appSecret: String? = null,
    val enabled: Boolean? = null,
)

/**
 * 계좌 응답. 마스킹된 값만 반환.
 */
data class BrokerAccountResponse(
    val id: Long,
    val broker: Broker,
    val accountType: AccountType,
    val accountNumber: String,             // masked
    val accountProductCode: String?,
    val accountAlias: String?,
    val displayName: String,
    val appKey: String,                    // masked
    val enabled: Boolean,
    val lastUsedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
) {
    companion object {
        fun from(account: UserBrokerAccount): BrokerAccountResponse = BrokerAccountResponse(
            id = account.id!!,
            broker = account.broker,
            accountType = account.accountType,
            accountNumber = account.maskedAccountNumber(),
            accountProductCode = account.accountProductCode,
            accountAlias = account.accountAlias,
            displayName = account.displayName(),
            appKey = account.maskedExternalKey(),
            enabled = account.enabled,
            lastUsedAt = account.lastUsedAt,
            createdAt = account.createdAt,
            deletedAt = account.deletedAt,
        )
    }
}

data class BrokerAccountListResponse(
    val active: List<BrokerAccountResponse>,
    val trashed: List<BrokerAccountResponse>,
)

class TrashExpiredException(message: String) : RuntimeException(message)
class ForbiddenException(message: String) : RuntimeException(message)
