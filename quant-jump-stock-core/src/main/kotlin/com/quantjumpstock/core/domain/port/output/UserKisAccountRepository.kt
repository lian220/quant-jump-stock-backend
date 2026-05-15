package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.user.KisAccountType
import com.quantjumpstock.core.domain.model.user.UserKisAccount
import java.time.LocalDateTime

/**
 * UserKisAccount Repository Port
 *
 * A+ 모델 (단일 활성 + 7일 휴지통) 도입:
 * - 활성 row (`deleted_at IS NULL`): 사용자당 1개. 일반 조회 메서드 (`findByUserId`, `findActive*`) 가 반환.
 * - 휴지통 row (`deleted_at IS NOT NULL`): 사용자당 0~1개 (정책). `findTrashed*` 로 접근.
 *
 * 기존 시그니처 (`findByUserId` 등) 는 호환을 위해 활성 row 만 반환하도록 의미를 좁혔다.
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
}
