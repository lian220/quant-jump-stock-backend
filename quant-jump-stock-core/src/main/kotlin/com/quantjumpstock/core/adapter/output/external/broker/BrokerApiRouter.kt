package com.quantjumpstock.core.adapter.output.external.broker

import com.quantjumpstock.core.domain.model.broker.Broker
import com.quantjumpstock.core.domain.port.output.BrokerApiAdapter
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Broker → BrokerApiAdapter routing (Phase 1B v2.1).
 *
 * Startup 검증 (@PostConstruct): 모든 `Broker` enum 에 대해 adapter 가 등록되었는지 자가 검증.
 * 누락 시 즉시 부팅 실패 — early fail. broker 추가 시 adapter @Component 만 만들면 자동 매핑.
 */
@Component
class BrokerApiRouter(
    private val adapters: List<BrokerApiAdapter>,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private lateinit var routes: Map<Broker, BrokerApiAdapter>

    @PostConstruct
    fun validateAndIndex() {
        val byBroker = adapters.associateBy { it.supportedBroker }

        // 중복 검출: 같은 broker 를 두 어댑터가 지원하면 사일런트 라우팅 버그 위험.
        val duplicates = adapters.groupingBy { it.supportedBroker }.eachCount().filter { it.value > 1 }
        require(duplicates.isEmpty()) {
            "Multiple BrokerApiAdapter for same broker: $duplicates"
        }

        // 누락 검출: Broker enum 항목 중 매핑 없는 게 있으면 부팅 실패.
        val missing = Broker.entries.filter { it !in byBroker }
        require(missing.isEmpty()) {
            "Missing BrokerApiAdapter for broker(s): $missing. Add @Component implementation."
        }

        routes = byBroker
        logger.info("BrokerApiRouter initialized: ${routes.mapValues { it.value::class.simpleName }}")
    }

    fun route(broker: Broker): BrokerApiAdapter =
        routes[broker] ?: throw UnsupportedBrokerException(broker)
}

class UnsupportedBrokerException(broker: Broker) :
    RuntimeException("No BrokerApiAdapter registered for broker: $broker")
