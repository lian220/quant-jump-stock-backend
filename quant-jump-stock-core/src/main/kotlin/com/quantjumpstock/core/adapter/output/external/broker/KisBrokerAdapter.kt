package com.quantjumpstock.core.adapter.output.external.broker

import com.quantjumpstock.core.domain.model.broker.Broker
import com.quantjumpstock.core.domain.model.broker.BrokerCredentials
import com.quantjumpstock.core.domain.model.broker.UserBrokerAccount
import com.quantjumpstock.core.domain.port.output.BrokerApiAdapter
import org.springframework.stereotype.Component

/**
 * KIS BrokerApiAdapter (Phase 1B v2.1 — MVP).
 *
 * 기존 [com.quantjumpstock.core.adapter.output.external.KisApiAdapter] 는 그대로 유지
 * (AutoTrading → TradingApiPort 경로 호환). 본 어댑터는 신규 broker-agnostic 경로용.
 *
 * MVP 단계에서는 credentials 검증만 수행. 실제 KIS HTTP 호출은 다음 phase 에서
 * 기존 [KisApiAdapter] / [KisTokenIssuer] 와 통합.
 */
@Component
class KisBrokerAdapter : BrokerApiAdapter {

    override val supportedBroker: Broker = Broker.KIS

    override fun getAccessToken(account: UserBrokerAccount): String {
        require(account.broker == Broker.KIS) { "Expected KIS account, got ${account.broker}" }
        require(account.credentials is BrokerCredentials.Kis) {
            "KIS account expects BrokerCredentials.Kis, got ${account.credentials::class.simpleName}"
        }
        // MVP: stub. 실제 KIS 호출 통합은 다음 phase.
        throw NotImplementedError(
            "KIS access token via BrokerApiAdapter not wired yet. " +
                "Use TradingApiPort (legacy) for auto-trading. broker_account_id=${account.id}",
        )
    }
}
