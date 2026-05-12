package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountEntity
import com.quantjumpstock.core.config.KisConfig
import com.quantjumpstock.core.infrastructure.security.AppSecretCipher
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.LocalDateTime

/**
 * KIS OAuth 토큰 발급을 담당하는 별도 빈.
 *
 * 분리 이유 (Phase 1A PRE 패널 C-1/H-1 + CodeRabbit #2 반영):
 *  - 기존 [KisApiAdapter.issueNewToken] 은 `private` + `@Transactional` 조합이라 Spring AOP
 *    프록시가 advice 를 건너뛰어 트랜잭션이 실제로 열리지 않았다.
 *  - 추가로 `accessTokenCache.compute(...)` 람다 안에서 KIS HTTPS(최대 30s) + DB write 가
 *    수행되어 ConcurrentHashMap 버킷 락이 30초까지 점유되는 교착 위험이 있었다.
 *  - **CodeRabbit #2 추가 분리**: 본 클래스의 `issueNewToken` 도 `@Transactional` 안에서
 *    KIS HTTP 를 호출하면 DB 트랜잭션이 네트워크 latency 만큼 hold 됨 → Hikari pool 압박.
 *    KIS 호출은 트랜잭션 *밖* 에서 수행하고, DB 영속화만 [KisTokenStore.persist] 에 위임.
 *
 * 흐름:
 * 1. [issueNewToken] (no @Transactional): KIS OAuth HTTP 호출 → access_token / expires_in 파싱
 * 2. [KisTokenStore.persist] (@Transactional): deactivate + save (DB 만)
 *
 * 다중 인스턴스에서는 KIS 발급이 인스턴스 수만큼 발생할 수 있으나 본 PR 범위 외 (Phase 1B
 * 분산 발급 직렬화).
 */
@Component
class KisTokenIssuer(
    private val appSecretCipher: AppSecretCipher,
    private val tokenStore: KisTokenStore,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * KIS OAuth 토큰 발급 + DB 저장.
     *
     * **트랜잭션 경계 없음** — KIS HTTP 호출은 트랜잭션 밖. DB 저장은 [KisTokenStore.persist]
     * 가 자체 `@Transactional` 로 처리.
     */
    fun issueNewToken(userId: String, kisAccount: UserKisAccountEntity): IssuedToken {
        logger.info("Refreshing KIS access token: user=$userId type=${kisAccount.accountType}")

        // 1. KIS HTTP 호출 (네트워크 latency, 트랜잭션 밖)
        val appSecret = decryptAppSecret(kisAccount)
        val restClient = buildOauthClient(kisAccount.accountType)

        val response = restClient
            .post()
            .uri("/oauth2/tokenP")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "grant_type" to "client_credentials",
                    "appkey" to kisAccount.appKey,
                    "appsecret" to appSecret,
                ),
            )
            .retrieve()
            .body(Map::class.java)
            ?: throw RuntimeException("Failed to get access token from KIS for user: $userId")

        val token = response["access_token"] as String
        // KIS 공식 응답: expires_in 은 Long. 일부 환경에서 Int 로 역직렬화될 수 있어 Number 안전 캐스팅.
        val expiresIn = (response["expires_in"] as Number).toLong()
        val expirationTime = LocalDateTime.now().plusSeconds(expiresIn)

        // 2. DB 영속화 (트랜잭션 안, 별도 빈)
        tokenStore.persist(kisAccount.user, kisAccount.accountType, token, expirationTime)

        return IssuedToken(token, expirationTime)
    }

    /**
     * AppSecret 복호화. fallback 정책은 [AppSecretCipher] 가 단일 소스.
     */
    private fun decryptAppSecret(entity: UserKisAccountEntity): String =
        appSecretCipher.decrypt(entity.appSecretEncryptedV2, entity.appSecretEncrypted)

    /**
     * OAuth 토큰 발급용 단발성 RestClient. 캐싱 가치가 낮으므로 매번 새로 만든다.
     */
    private fun buildOauthClient(accountType: KisAccountType): RestClient {
        val baseUrl = KisConfig.getBaseUrlForAccountType(accountType)
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(10))
                    setReadTimeout(Duration.ofSeconds(30))
                },
            )
            .build()
    }

    data class IssuedToken(val accessToken: String, val expirationTime: LocalDateTime)
}
