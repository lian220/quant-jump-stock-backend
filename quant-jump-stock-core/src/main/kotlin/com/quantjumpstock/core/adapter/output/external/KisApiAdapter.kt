package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.adapter.output.persistence.jpa.AccountTypeEntityEnum
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserBrokerAccountEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserBrokerAccountJpaRepository
import com.quantjumpstock.core.config.KisConfig
import com.quantjumpstock.core.domain.trading.port.output.TradingApiPort
import com.quantjumpstock.core.infrastructure.security.AppSecretCipher
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * KIS API Adapter (Output Adapter).
 *
 * Phase 1D 마이그 (2026-05-19): 계좌 lookup 을 deprecated `user_kis_accounts` → `user_broker_accounts`
 * (broker='KIS') 로 전환. 동일한 (userId, accountType) 키로 RestClient / 토큰 캐시 유지.
 *
 * 주요 정책:
 *  - 계좌 lookup: `findFirstActiveKisByUserLoginId` — 다중 KIS 계좌 보유 시 첫 번째 (createdAt ASC).
 *    정확한 strategy → account 매핑은 Phase 1B 의 strategy_subscriptions.broker_account_id 가 담당.
 *  - 토큰 캐시 키 (userId, accountType) — 모의/실전 baseURL 분리.
 *  - per-key ReentrantLock + double-check 패턴 — 토큰 발급 직렬화하되 KIS HTTPS 호출은 락 밖.
 *  - rate limit: 단일 인스턴스 내 500ms 간격 (다중 인스턴스 분산 처리는 별도 phase).
 *  - AppSecret 복호화: v2 (GCM) 만 — `user_broker_accounts` 에는 v1 컬럼 없음 (V63).
 */
@Component
class KisApiAdapter(
    private val userBrokerAccountRepository: UserBrokerAccountJpaRepository,
    private val appSecretCipher: AppSecretCipher,
    private val tokenRepository: KisTokenJpaRepository,
    private val tokenIssuer: KisTokenIssuer,
) : TradingApiPort {
    private val logger = LoggerFactory.getLogger(KisApiAdapter::class.java)

    private data class CacheKey(val userId: String, val accountType: AccountTypeEntityEnum)
    private data class CachedToken(val accessToken: String, val expirationTime: LocalDateTime) {
        fun isValid(now: LocalDateTime = LocalDateTime.now()): Boolean = now.isBefore(expirationTime)
    }

    private val restClientCache = ConcurrentHashMap<CacheKey, RestClient>()
    private val accessTokenCache = ConcurrentHashMap<CacheKey, CachedToken>()
    private val keyLocks = ConcurrentHashMap<CacheKey, ReentrantLock>()

    private val apiLock = ReentrantLock()
    private var lastApiCallNanos = 0L
    private val minApiIntervalMs = 500L

    private fun getActiveKisAccount(userId: String): UserBrokerAccountEntity {
        return userBrokerAccountRepository.findFirstActiveKisByUserLoginId(userId)
            .orElseThrow { IllegalStateException("KIS broker account not found for user: $userId") }
    }

    internal fun decryptAppSecret(entity: UserBrokerAccountEntity): String =
        appSecretCipher.decrypt(entity.appSecretEncrypted, "")

    internal fun getOrCreateRestClient(userId: String, accountType: AccountTypeEntityEnum): RestClient {
        return restClientCache.computeIfAbsent(CacheKey(userId, accountType)) {
            val baseUrl = KisConfig.getBaseUrlForAccountType(accountType)
            logger.info("Creating RestClient for user=$userId type=$accountType baseUrl=$baseUrl")
            RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(
                    SimpleClientHttpRequestFactory().apply {
                        setConnectTimeout(Duration.ofSeconds(10))
                        setReadTimeout(Duration.ofSeconds(30))
                    },
                )
                .build()
        }
    }

    override fun getAccessToken(userId: String): String {
        val kisAccount = getActiveKisAccount(userId)
        return getAccessTokenInternal(userId, kisAccount)
    }

    private fun getAccessTokenInternal(userId: String, kisAccount: UserBrokerAccountEntity): String {
        val key = CacheKey(userId, kisAccount.accountType)
        val now = LocalDateTime.now()

        accessTokenCache[key]?.takeIf { it.isValid(now) }?.let { return it.accessToken }

        val dbToken = tokenRepository.findLatestTokenByUserIdAndAccountType(userId, kisAccount.accountType)
        if (dbToken.isPresent && dbToken.get().isValid()) {
            val cached = CachedToken(dbToken.get().accessToken, dbToken.get().expirationTime)
            accessTokenCache[key] = cached
            return cached.accessToken
        }

        val lock = keyLocks.computeIfAbsent(key) { ReentrantLock() }
        lock.lock()
        try {
            accessTokenCache[key]?.takeIf { it.isValid(LocalDateTime.now()) }?.let { return it.accessToken }

            val issued = tokenIssuer.issueNewToken(userId, kisAccount)
            val cached = CachedToken(issued.accessToken, issued.expirationTime)
            accessTokenCache[key] = cached
            return cached.accessToken
        } finally {
            lock.unlock()
        }
    }

    private fun waitForRateLimit() {
        apiLock.lock()
        try {
            val now = System.nanoTime()
            val elapsedMs = (now - lastApiCallNanos) / 1_000_000
            if (lastApiCallNanos != 0L && elapsedMs < minApiIntervalMs) {
                Thread.sleep(minApiIntervalMs - elapsedMs)
            }
            lastApiCallNanos = System.nanoTime()
        } finally {
            apiLock.unlock()
        }
    }

    private fun <S : RestClient.RequestHeadersSpec<S>> RestClient.RequestHeadersSpec<S>.kisHeaders(
        token: String,
        appKey: String,
        appSecret: String,
        trId: String,
    ): RestClient.RequestHeadersSpec<S> = this
        .header("authorization", "Bearer $token")
        .header("appkey", appKey)
        .header("appsecret", appSecret)
        .header("tr_id", trId)
        .header("Content-Type", "application/json; charset=utf-8")

    @Suppress("UNCHECKED_CAST")
    override fun getOverseasBalance(userId: String, exchange: String): Map<String, Any> {
        waitForRateLimit()

        val kisAccount = getActiveKisAccount(userId)
        val token = getAccessTokenInternal(userId, kisAccount)
        val restClient = getOrCreateRestClient(userId, kisAccount.accountType)

        val trId = when (kisAccount.accountType) {
            AccountTypeEntityEnum.MOCK -> "VTTS3012R"
            AccountTypeEntityEnum.REAL -> "TTTS3012R"
        }

        val appSecret = decryptAppSecret(kisAccount)

        return restClient
            .get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/uapi/overseas-stock/v1/trading/inquire-balance")
                    .queryParam("CANO", kisAccount.accountNumber)
                    .queryParam("ACNT_PRDT_CD", kisAccount.accountProductCode ?: "01")
                    .queryParam("OVRS_EXCG_CD", exchange)
                    .queryParam("TR_CRCY_CD", "USD")
                    .queryParam("CTX_AREA_FK200", "")
                    .queryParam("CTX_AREA_NK200", "")
                    .build()
            }
            .kisHeaders(token, kisAccount.appKey, appSecret, trId)
            .retrieve()
            .body(Map::class.java) as Map<String, Any>? ?: emptyMap()
    }

    @Suppress("UNCHECKED_CAST")
    override fun placeOrder(
        userId: String,
        ticker: String,
        orderType: String,
        quantity: Int,
        price: String,
    ): Map<String, Any> {
        waitForRateLimit()

        val kisAccount = getActiveKisAccount(userId)
        val token = getAccessTokenInternal(userId, kisAccount)
        val restClient = getOrCreateRestClient(userId, kisAccount.accountType)

        val trId = when {
            kisAccount.accountType == AccountTypeEntityEnum.MOCK && orderType == "BUY" -> "VTTT1002U"
            kisAccount.accountType == AccountTypeEntityEnum.MOCK && orderType == "SELL" -> "VTTT1001U"
            kisAccount.accountType == AccountTypeEntityEnum.REAL && orderType == "BUY" -> "TTTT1002U"
            kisAccount.accountType == AccountTypeEntityEnum.REAL && orderType == "SELL" -> "TTTT1001U"
            else -> throw IllegalArgumentException("Invalid order type: $orderType")
        }

        val appSecret = decryptAppSecret(kisAccount)

        val ordDvsn = "00"

        val orderBody = mapOf(
            "CANO" to kisAccount.accountNumber,
            "ACNT_PRDT_CD" to (kisAccount.accountProductCode ?: "01"),
            "OVRS_EXCG_CD" to "NASD",
            "PDNO" to ticker,
            "ORD_QTY" to quantity.toString(),
            "OVRS_ORD_UNPR" to price,
            "ORD_SVR_DVSN_CD" to "0",
            "ORD_DVSN" to ordDvsn,
        )

        logger.info("Placing $orderType order for $userId: $ticker x$quantity @ $price")

        return try {
            val result = restClient
                .post()
                .uri("/uapi/overseas-stock/v1/trading/order")
                .contentType(MediaType.APPLICATION_JSON)
                .body(orderBody)
                .kisHeaders(token, kisAccount.appKey, appSecret, trId)
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
                "msg1" to "Order execution failed",
            )
        }
    }
}
