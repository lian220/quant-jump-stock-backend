package com.quantjumpstock.core.domain.model.broker

/**
 * 증권사 식별자.
 *
 * 신규 broker 추가 시:
 *  1. 본 enum 에 등록
 *  2. `BrokerApiAdapter` 구현체 (`@Component`) 추가
 *  3. `BrokerApiRouter` 의 startup 검증 (@PostConstruct) 이 자동 매핑 확인 → 누락 시 부팅 실패
 */
enum class Broker {
    KIS,
    TOSS,
    ;

    companion object {
        fun fromString(s: String): Broker = entries.firstOrNull { it.name == s.uppercase() }
            ?: throw IllegalArgumentException("Unknown broker: $s")
    }
}
