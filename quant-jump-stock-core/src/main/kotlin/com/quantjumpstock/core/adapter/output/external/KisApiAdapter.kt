package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import com.quantjumpstock.core.infrastructure.security.EncryptionService
import com.quantjumpstock.core.infrastructure.security.EncryptionServiceGcm
import com.quantjumpstock.core.config.KisConfig
import com.quantjumpstock.core.domain.trading.port.output.TradingApiPort
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient

/**
 * KIS API Adapter (Output Adapter)
 * TradingApiPort 를 구현하여 한국투자증권 API 와 연동한다.
 *
 * Phase 1A PRE Task 10 변경:
 *  - restClientCache / accessTokenCache 키를 (userId, accountType) 복합키로 분리해
 *    동일 사용자가 MOCK ↔ REAL 전환 시 잘못된 baseURL/토큰을 재사용하지 않도록 한다.
 *  - 토큰 발급은 ConcurrentHashMap.compute 로 race-free 하게 수행한다.
 *  - AppSecret 복호화는 v2(GCM) 우선, v2 가 비어있으면 v1(ECB) legacy fallback.
 *    Task 7 이후 신규 등록은 v1 = "" 로 마킹되므로 v2 우선 처리가 필수.
 */
@Component
class KisApiAdapter(
    private val userKisAccountRepository: UserKisAccountJpaRepository,
    private val encryptionServiceLegacy: EncryptionService,
    private val encryptionServiceGcm: EncryptionServiceGcm,
    private val tokenRepository: KisTokenJpaRepository
) : TradingApiPort {
    private val logger = LoggerFactory.getLogger(KisApiAdapter::class.java)

    private data class CacheKey(val userId: String, val accountType: KisAccountType)
    private data class CachedToken(val accessToken: String, val expirationTime: LocalDateTime) {
        fun isValid(now: LocalDateTime = LocalDateTime.now()): Boolean = now.isBefore(expirationTime)
    }

    private val restClientCache = ConcurrentHashMap<CacheKey, RestClient>()
    private val accessTokenCache = ConcurrentHashMap<CacheKey, CachedToken>()

    private val lastApiCallTime = AtomicLong(0)
    private val minApiIntervalMs = 500L
    private val apiLock = ReentrantLock()

    private fun getActiveKisAccount(userId: String): UserKisAccountEntity {
        return userKisAccountRepository.findActiveByUserUserId(userId)
            .orElseThrow { IllegalStateException("KIS account not found for user: $userId") }
    }

    /**
     * AppSecret 복호화 (v2 우선, v1 legacy fallback).
     * Task 7 이후 신규 등록은 v1 = "" 로 저장되므로 v2 우선 처리해야 KIS 인증이 성립한다.
     * v2 가 null 또는 빈 문자열이면 마이그레이션 미완 row 로 간주하고 legacy ECB 로 fallback.
     */
    internal fun decryptAppSecret(entity: UserKisAccountEntity): String {
        val v2 = entity.appSecretEncryptedV2
        return if (!v2.isNullOrBlank()) {
            encryptionServiceGcm.decrypt(v2)
        } else {
            encryptionServiceLegacy.decrypt(entity.appSecretEncrypted)
        }
    }

    /**
     * (userId, accountType) 별 RestClient 캐시.
     * 동일 사용자라도 모의 ↔ 실전 baseURL 이 다르므로 분리 캐시가 필수.
     */
    internal fun getOrCreateRestClient(userId: String, accountType: KisAccountType): RestClient {
        return restClientCache.computeIfAbsent(CacheKey(userId, accountType)) {
            val baseUrl = KisConfig.getBaseUrlForAccountType(accountType)
            logger.info("Creating RestClient for user=$userId type=$accountType baseUrl=$baseUrl")
            RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(10))
                    setReadTimeout(Duration.ofSeconds(30))
                })
                .build()
        }
    }

    override fun getAccessToken(userId: String): String {
        val kisAccount = getActiveKisAccount(userId)
        return getAccessTokenInternal(userId, kisAccount)
    }

    private fun getAccessTokenInternal(userId: String, kisAccount: UserKisAccountEntity): String {
        val key = CacheKey(userId, kisAccount.accountType)
        val now = LocalDateTime.now()

        accessTokenCache[key]?.takeIf { it.isValid(now) }?.let { return it.accessToken }

        val dbToken = tokenRepository.findLatestTokenByUserIdAndAccountType(userId, kisAccount.accountType)
        if (dbToken.isPresent && dbToken.get().isValid()) {
            val cached = CachedToken(dbToken.get().accessToken, dbToken.get().expirationTime)
            accessTokenCache[key] = cached
            return cached.accessToken
        }

        // compute 블록은 동일 key 에 대해 단일 스레드만 진입하므로 동시 호출 시 KIS 발급은 1회만 수행된다.
        val computed = accessTokenCache.compute(key) { _, existing ->
            if (existing != null && existing.isValid()) existing
            else issueNewToken(userId, kisAccount)
        }!!
        return computed.accessToken
    }

    @Transactional(readOnly = false)
    private fun issueNewToken(userId: String, kisAccount: UserKisAccountEntity): CachedToken {
        logger.info("Refreshing KIS access token: user=$userId type=${kisAccount.accountType}")

        val appSecret = decryptAppSecret(kisAccount)
        val restClient = getOrCreateRestClient(userId, kisAccount.accountType)

        val response = restClient
            .post()
            .uri("/oauth2/tokenP")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "grant_type" to "client_credentials",
                    "appkey" to kisAccount.appKey,
                    "appsecret" to appSecret
                )
            )
            .retrieve()
            .body(Map::class.java)
            ?: throw RuntimeException("Failed to get access token from KIS for user: $userId")

        val token = response["access_token"] as String
        val expiresIn = (response["expires_in"] as Int).toLong()
        val expirationTime = LocalDateTime.now().plusSeconds(expiresIn)

        tokenRepository.deactivateUserTokens(
            kisAccount.user.id ?: throw IllegalStateException("User ID is null"),
            kisAccount.accountType,
            LocalDateTime.now()
        )

        tokenRepository.save(
            KisTokenEntity(
                user = kisAccount.user,
                accountType = kisAccount.accountType,
                accessToken = token,
                expirationTime = expirationTime
            )
        )

        return CachedToken(token, expirationTime)
    }

    private fun waitForRateLimit() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastApiCallTime.get()
        if (elapsed < minApiIntervalMs) {
            Thread.sleep(minApiIntervalMs - elapsed)
        }
        lastApiCallTime.set(System.currentTimeMillis())
    }

    @Suppress("UNCHECKED_CAST")
    override fun getOverseasBalance(userId: String, exchange: String): Map<String, Any> {
        waitForRateLimit()

        val kisAccount = getActiveKisAccount(userId)
        val token = getAccessTokenInternal(userId, kisAccount)
        val restClient = getOrCreateRestClient(userId, kisAccount.accountType)

        val trId = when (kisAccount.accountType) {
            KisAccountType.MOCK -> "VTTS3012R"
            KisAccountType.REAL -> "TTTS3012R"
        }

        val appSecret = decryptAppSecret(kisAccount)

        return restClient
            .get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/uapi/overseas-stock/v1/trading/inquire-balance")
                    .queryParam("CANO", kisAccount.accountNumber)
                    .queryParam("ACNT_PRDT_CD", kisAccount.accountProductCode)
                    .queryParam("OVRS_EXCG_CD", exchange)
                    .queryParam("TR_CRCY_CD", "USD")
                    .queryParam("CTX_AREA_FK200", "")
                    .queryParam("CTX_AREA_NK200", "")
                    .build()
            }
            .header("authorization", "Bearer $token")
            .header("appkey", kisAccount.appKey)
            .header("appsecret", appSecret)
            .header("tr_id", trId)
            .header("Content-Type", "application/json; charset=utf-8")
            .retrieve()
            .body(Map::class.java) as Map<String, Any>? ?: emptyMap()
    }

    @Suppress("UNCHECKED_CAST")
    override fun placeOrder(
        userId: String,
        ticker: String,
        orderType: String,
        quantity: Int,
        price: String
    ): Map<String, Any> {
        waitForRateLimit()

        val kisAccount = getActiveKisAccount(userId)
        val token = getAccessTokenInternal(userId, kisAccount)
        val restClient = getOrCreateRestClient(userId, kisAccount.accountType)

        val trId = when {
            kisAccount.accountType == KisAccountType.MOCK && orderType == "BUY" -> "VTTT1002U"
            kisAccount.accountType == KisAccountType.MOCK && orderType == "SELL" -> "VTTT1001U"
            kisAccount.accountType == KisAccountType.REAL && orderType == "BUY" -> "TTTT1002U"
            kisAccount.accountType == KisAccountType.REAL && orderType == "SELL" -> "TTTT1001U"
            else -> throw IllegalArgumentException("Invalid order type: $orderType")
        }

        val appSecret = decryptAppSecret(kisAccount)

        val orderBody = mapOf(
            "CANO" to kisAccount.accountNumber,
            "ACNT_PRDT_CD" to kisAccount.accountProductCode,
            "OVRS_EXCG_CD" to "NASD",
            "PDNO" to ticker,
            "ORD_QTY" to quantity.toString(),
            "OVRS_ORD_UNPR" to price,
            "ORD_SVR_DVSN_CD" to "0",
            "ORD_DVSN" to if (price == "0") "00" else "00"
        )

        logger.info("Placing $orderType order for $userId: $ticker x$quantity @ $price")

        return try {
            val result = restClient
                .post()
                .uri("/uapi/overseas-stock/v1/trading/order")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", "Bearer $token")
                .header("appkey", kisAccount.appKey)
                .header("appsecret", appSecret)
                .header("tr_id", trId)
                .header("Content-Type", "application/json; charset=utf-8")
                .body(orderBody)
                .retrieve()
                .body(Map::class.java) as Map<String, Any>? ?: emptyMap()

            val rtCd = result["rt_cd"] as? String
            if (rtCd == "0") {
                logger.info("Order placed successfully: $ticker x$quantity")
            } else {
                logger.error("Order failed: ${result["msg1"]}")
            }

            result
        } catch (e: Exception) {
            logger.error("Error placing order for $ticker", e)
            mapOf(
                "rt_cd" to "1",
                "msg1" to "Order execution failed: ${e.message}"
            )
        }
    }
}
