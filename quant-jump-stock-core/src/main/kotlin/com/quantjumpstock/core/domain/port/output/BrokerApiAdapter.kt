package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.broker.Broker
import com.quantjumpstock.core.domain.model.broker.UserBrokerAccount

/**
 * Broker API Adapter port (Phase 1B v2.1).
 *
 * 각 증권사 어댑터가 구현. `BrokerApiRouter` 가 `Broker` 기준으로 라우팅.
 * 신규 broker 추가 시:
 *  1. `Broker` enum 항목 추가
 *  2. 본 인터페이스 구현체 (`@Component`) 추가
 *  3. `BrokerApiRouter` @PostConstruct 자가 검증이 자동으로 매핑 확인 → 누락 시 부팅 실패
 *
 * 본 단계 (MVP) 에서는 인터페이스만 정의. 실제 주문 라우팅은 다음 phase 에서.
 */
interface BrokerApiAdapter {
    val supportedBroker: Broker

    /**
     * 인증 토큰 발급. broker 마다 흐름 다름:
     *  - KIS: appKey + appSecret → access_token (24h)
     *  - Toss: client_id + refresh_token → access_token (S4 skeleton, NotImplementedError)
     */
    fun getAccessToken(account: UserBrokerAccount): String
}
