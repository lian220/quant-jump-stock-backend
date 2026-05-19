package com.quantjumpstock.core.adapter.output.external.broker

import com.quantjumpstock.core.domain.model.broker.Broker
import com.quantjumpstock.core.domain.model.broker.UserBrokerAccount
import com.quantjumpstock.core.domain.port.output.BrokerApiAdapter
import org.springframework.stereotype.Component

/**
 * Toss BrokerApiAdapter skeleton (Phase 1B v2.1 — MVP).
 *
 * 본 어댑터는 인터페이스 컴파일 통과 + Startup 검증 통과 보장만 한다.
 * 모든 메서드는 `NotImplementedError` — 실제 Toss API 통합은 별도 phase.
 * 사용자가 Toss 계좌 등록 시도 → Controller 레벨에서 차단 (whitelist) 권장.
 */
@Component
class TossBrokerAdapter : BrokerApiAdapter {

    override val supportedBroker: Broker = Broker.TOSS

    override fun getAccessToken(account: UserBrokerAccount): String {
        throw NotImplementedError(
            "Toss API integration pending. broker_account_id=${account.id}",
        )
    }
}
