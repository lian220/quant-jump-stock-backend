package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.broker.AccountType
import com.quantjumpstock.core.domain.model.broker.Broker
import com.quantjumpstock.core.domain.model.broker.UserBrokerAccount
import java.time.LocalDateTime

/**
 * UserBrokerAccount Repository Port (Phase 1B v2.1).
 *
 * 키 정책: `(user_id, broker, account_type, account_number)` 4-tuple 활성 unique.
 * - 같은 사용자가 KIS + Toss × MOCK/REAL × 계좌번호 N개 동시 보유 가능.
 * - 휴지통 row 는 unique 제약 무관 (활성과 공존).
 *
 * port 표면 정책 (backend-architect Fowler 권고):
 * - 내부 식별자 `Long userId` 만 사용. 외부 식별자 (Supabase UUID 등) 변환은 Service 책임.
 *
 * 메서드 명명 (Adzic 권고):
 * - "활성" = `deletedAt IS NULL`. enabled 토글 무관. KDoc 명시.
 */
interface UserBrokerAccountRepository {
    fun save(account: UserBrokerAccount): UserBrokerAccount

    fun findById(id: Long): UserBrokerAccount?

    /** 사용자의 활성 계좌 전부 (deletedAt IS NULL, enabled 토글 무관). 모든 broker + 모든 type. */
    fun findAllActiveByUserId(userId: Long): List<UserBrokerAccount>

    /** 4-tuple 로 활성 계좌 단건 조회 (deletedAt IS NULL). */
    fun findActiveByUserIdAndKey(
        userId: Long,
        broker: Broker,
        accountType: AccountType,
        accountNumber: String,
    ): UserBrokerAccount?

    /** 사용자의 휴지통 row 전부 (deletedAt IS NOT NULL). */
    fun findAllTrashedByUserId(userId: Long): List<UserBrokerAccount>

    /** Scheduler 가 호출. `deleted_at < thresholdAt` 인 모든 휴지통 row. */
    fun findExpiredTrashed(thresholdAt: LocalDateTime): List<UserBrokerAccount>

    fun deleteById(id: Long)
}
