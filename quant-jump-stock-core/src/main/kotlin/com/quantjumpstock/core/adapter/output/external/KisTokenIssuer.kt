package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountEntity
import com.quantjumpstock.core.config.KisConfig
import com.quantjumpstock.core.infrastructure.security.EncryptionService
import com.quantjumpstock.core.infrastructure.security.EncryptionServiceGcm
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.LocalDateTime

/**
 * KIS OAuth 토큰 발급을 담당하는 별도 빈.
 *
 * 분리 이유 (Phase 1A PRE 검토 C-1 / H-1 반영):
 *  - 기존 [KisApiAdapter.issueNewToken] 은 `private` + `@Transactional` 조합이라 Spring AOP
 *    프록시가 advice 를 건너뛰어 트랜잭션이 실제로 열리지 않았다. `deactivateUserTokens` 와
 *    `save` 가 별도 commit 으로 쪼개져 토큰 공백 또는 UNIQUE 제약 위반 위험이 있었다.
 *  - 추가로 `accessTokenCache.compute(...)` 람다 안에서 KIS HTTPS(최대 30s) + DB write 가
 *    수행되어 ConcurrentHashMap 버킷 락이 30초까지 점유되는 교착 위험이 있었다.
 *
 * 분리 이후:
 *  - 본 클래스는 `public` 메서드로 호출되어 Spring AOP 프록시가 정상 동작한다.
 *  - 호출자([KisApiAdapter]) 는 토큰 캐시 락 *밖* 에서 본 메서드를 호출한다.
 *  - 다중 인스턴스에서는 KIS 발급이 인스턴스 수만큼 발생할 수 있으나 본 PR 범위 외 (Tier 5
 *    Phase 1B: 분산 발급 직렬화).
 */
@Component
class KisTokenIssuer(
    private val tokenRepository: KisTokenJpaRepository,
    private val encryptionServiceLegacy: EncryptionService,
    private val encryptionServiceGcm: EncryptionServiceGcm,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * KIS OAuth 토큰을 새로 발급하고 DB 에 저장한다.
     *
     * 트랜잭션 경계: 본 메서드 전체가 하나의 트랜잭션. `deactivateUserTokens` 와 `save` 가
     * 같은 commit 으로 묶여 부분 실패가 발생하지 않는다.
     */
    @Transactional
    fun issueNewToken(userId: String, kisAccount: UserKisAccountEntity): IssuedToken {
        logger.info("Refreshing KIS access token: user=$userId type=${kisAccount.accountType}")

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
        val expiresIn = (response["expires_in"] as Int).toLong()
        val expirationTime = LocalDateTime.now().plusSeconds(expiresIn)

        val userPk = kisAccount.user.id ?: throw IllegalStateException("User ID is null")
        tokenRepository.deactivateUserTokens(userPk, kisAccount.accountType, LocalDateTime.now())
        tokenRepository.save(
            KisTokenEntity(
                user = kisAccount.user,
                accountType = kisAccount.accountType,
                accessToken = token,
                expirationTime = expirationTime,
            ),
        )

        return IssuedToken(token, expirationTime)
    }

    /**
     * v2(GCM) 우선, v2 가 비어있으면 v1(ECB) legacy fallback.
     * Task 7 이후 신규 등록은 v1 = "" 로 저장되므로 v2 우선이 필수.
     */
    private fun decryptAppSecret(entity: UserKisAccountEntity): String {
        val v2 = entity.appSecretEncryptedV2
        return if (!v2.isNullOrBlank()) {
            encryptionServiceGcm.decrypt(v2)
        } else {
            encryptionServiceLegacy.decrypt(entity.appSecretEncrypted)
        }
    }

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
