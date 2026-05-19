package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.user.KisAccountType
import com.quantjumpstock.core.domain.model.user.UserKisAccount
import java.time.LocalDateTime

/**
 * UserKisAccount Repository Port
 *
 * 모델 변천:
 * - A+ (V61): 사용자당 활성 1개 row (partial unique `(user_id) WHERE deleted_at IS NULL`).
 * - 1B (V63): 사용자당 account_type 별 활성 1개 row (`(user_id, account_type) WHERE deleted_at IS NULL`).
 *
 * 시그니처 정책:
 * - `*ByUserIdAndType(userId, accountType)` — 1B 권장 시그니처. 활성 row 단건 반환.
 * - `findAllActiveByUserId(userId)` — 1B 신규. type 무관 활성 row 전부 반환 (MOCK + REAL).
 * - 기존 `findActiveByUserUserId(userId)`, `findByUserId(userId)` 등 단건 메서드는 호환 유지.
 *   1B 에서는 "사용자의 마지막 활성 row 1개" 의미로 좁아짐 (다중 활성 row 존재 시 임의 1개).
 *   호출부 마이그레이션은 S3 에서 수행.
 */
interface UserKisAccountRepository {
    fun save(kisAccount: UserKisAccount): UserKisAccount
    fun findById(id: Long): UserKisAccount?

    /** 활성 row (deleted_at IS NULL) 만 반환. 휴지통은 제외. */
    fun findByUserId(userId: Long): UserKisAccount?

    /** 활성 row (deleted_at IS NULL) 만 반환. 휴지통은 제외. */
    fun findByUserUserId(userId: String): UserKisAccount?

    /** 활성 row (deleted_at IS NULL) 만 반환. `enabled` 토글 무관. */
    fun findActiveByUserUserId(userId: String): UserKisAccount?

    /** 휴지통 row (deleted_at IS NOT NULL). 가장 최근 1개 반환. */
    fun findTrashedByUserUserId(userId: String): UserKisAccount?

    /** Scheduler 가 호출. `deleted_at < thresholdAt` 인 모든 휴지통 row. */
    fun findExpiredTrashed(thresholdAt: LocalDateTime): List<UserKisAccount>

    fun findByUserIdAndAccountType(userId: Long, accountType: KisAccountType): UserKisAccount?
    fun deleteById(id: Long)

    /** 활성 row 존재 여부. */
    fun existsByUserId(userId: Long): Boolean

    // ===== 1B 신규 시그니처 (account_type 별 분리) =====

    /** 활성 row (deleted_at IS NULL) 중 특정 account_type 1개 반환. */
    fun findActiveByUserIdAndType(userId: Long, accountType: KisAccountType): UserKisAccount?

    /** 활성 row (deleted_at IS NULL) 중 특정 account_type 1개 반환. (String userId) */
    fun findActiveByUserUserIdAndType(userId: String, accountType: KisAccountType): UserKisAccount?

    /** 사용자의 활성 row 전부 반환 (MOCK + REAL). */
    fun findAllActiveByUserId(userId: Long): List<UserKisAccount>

    /** 사용자의 활성 row 전부 반환 (MOCK + REAL). (String userId) */
    fun findAllActiveByUserUserId(userId: String): List<UserKisAccount>

    /** 휴지통 row 중 특정 account_type 의 가장 최근 1개. */
    fun findTrashedByUserIdAndType(userId: Long, accountType: KisAccountType): UserKisAccount?

    /** 휴지통 row 중 특정 account_type 의 가장 최근 1개. (String userId) */
    fun findTrashedByUserUserIdAndType(userId: String, accountType: KisAccountType): UserKisAccount?
}
