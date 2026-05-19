package com.quantjumpstock.core.domain.event.broker

import com.quantjumpstock.core.domain.model.broker.AccountType
import com.quantjumpstock.core.domain.model.broker.Broker
import java.time.LocalDateTime

/**
 * 계좌 관련 도메인 이벤트 (Phase 1B v2.1).
 *
 * 현재 (S2): 클래스만 선언, 발행 0. consumer 가 1개 (AutoTrading 단일 모듈) 이므로 직접 호출과
 * 의미 차이 없음. 발행 코드는 S5 (Service) 단계에서 ApplicationEventPublisher 통해 도입 예정.
 *
 * 장기 (S10+): broker 2개+ 또는 consumer 2개+ 시점에 Pub/Sub 으로 분리 (Hohpe 권고).
 * 도메인 이벤트는 cross-module integration 의 안전한 진화 경계.
 */
sealed interface UserBrokerAccountEvent {
    val accountId: Long
    val userId: Long
    val broker: Broker
    val accountType: AccountType
    val occurredAt: LocalDateTime

    /** 신규 계좌 등록. */
    data class Registered(
        override val accountId: Long,
        override val userId: Long,
        override val broker: Broker,
        override val accountType: AccountType,
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : UserBrokerAccountEvent

    /** 기존 계좌 정보 업데이트 (credentials / accountAlias / enabled 등). */
    data class Updated(
        override val accountId: Long,
        override val userId: Long,
        override val broker: Broker,
        override val accountType: AccountType,
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : UserBrokerAccountEvent

    /**
     * 계좌 → 휴지통 (soft delete).
     * 향후 AutoTrading 이 본 이벤트 받아 해당 계좌의 활성 포지션 정리 trigger.
     */
    data class Trashed(
        override val accountId: Long,
        override val userId: Long,
        override val broker: Broker,
        override val accountType: AccountType,
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : UserBrokerAccountEvent

    /** 휴지통 → 활성 복원. */
    data class Restored(
        override val accountId: Long,
        override val userId: Long,
        override val broker: Broker,
        override val accountType: AccountType,
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : UserBrokerAccountEvent
}
