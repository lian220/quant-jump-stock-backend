package com.quantjumpstock.core.domain.model.broker

/**
 * broker 별 인증 자격증명 (Phase 1B v2.1).
 *
 * sealed interface 로 다형성 캡슐화. 도메인 표면에서 broker 추가가 닫혀 있는 변경이 되도록 (OCP).
 * DB 컬럼은 broker-agnostic schema (`app_key` + `app_secret_encrypted`) 유지하되 adapter 가
 * 각 변종으로 매핑. 새 broker 가 다른 인증 모델 (예: Toss OAuth refresh token) 이면
 * 본 sealed interface 변종 + 매핑만 추가, 도메인 사용자 코드는 영향 없음.
 *
 * 컬럼 의미는 broker 마다 다음과 같이 재해석:
 * - KIS:  app_key=KIS appKey,  app_secret_encrypted=GCM(KIS appSecret)
 * - Toss: app_key=client_id,   app_secret_encrypted=GCM(refresh_token) (S4 skeleton 단계엔 명세만)
 *
 * 향후 broker 의 인증 모델이 fixed-2-column 으로 표현 불가능해지면 별도 `broker_credentials`
 * 테이블 도입 검토 (Newman 장기 권고). 현재는 비용/효익상 불필요.
 */
sealed interface BrokerCredentials {

    /** broker-agnostic identifier (KIS appKey, Toss client_id 등). 외부 broker API 인증 시 사용. */
    val externalKey: String

    /** GCM 암호화된 secret. Base64(IV(12B) || ciphertext+tag(16B)). 복호화는 AppSecretCipher. */
    val externalSecretEncrypted: String

    /** KIS: appKey + appSecret. */
    data class Kis(
        val appKey: String,
        val appSecretEncrypted: String,
    ) : BrokerCredentials {
        override val externalKey: String get() = appKey
        override val externalSecretEncrypted: String get() = appSecretEncrypted
    }

    /**
     * Toss: client_id + refresh_token (S4 skeleton 도입 시 정합화).
     * 실제 호출은 NotImplementedError. 본 변종은 도메인 표면 안정성을 위한 placeholder.
     */
    data class TossOAuth(
        val clientId: String,
        val refreshTokenEncrypted: String,
    ) : BrokerCredentials {
        override val externalKey: String get() = clientId
        override val externalSecretEncrypted: String get() = refreshTokenEncrypted
    }
}
