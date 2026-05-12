package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import com.quantjumpstock.core.config.KisConfig
import com.quantjumpstock.core.domain.trading.port.output.TradingApiPort
import com.quantjumpstock.core.infrastructure.security.EncryptionService
import com.quantjumpstock.core.infrastructure.security.EncryptionServiceGcm
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
 * KIS API Adapter (Output Adapter)
 * TradingApiPort 를 구현하여 한국투자증권 API 와 연동한다.
 *
 * Phase 1A PRE Task 10 + 패널 리뷰 반영:
 *  - restClientCache / accessTokenCache 키를 (userId, accountType) 복합키로 분리해
 *    동일 사용자가 MOCK ↔ REAL 전환 시 잘못된 baseURL/토큰을 재사용하지 않도록 한다.
 *  - 토큰 발급은 [KisTokenIssuer] 별도 빈으로 분리해 Spring AOP 트랜잭션이 정상 동작.
 *  - 토큰 캐시 동기화는 per-key [ReentrantLock] + double-check 패턴 — KIS HTTPS(30s) 호출이
 *    ConcurrentHashMap 버킷 락을 점유하지 않도록 한다.
 *  - rate limit 은 [apiLock] 으로 단일 인스턴스 내 race 차단 (다중 인스턴스는 Phase 1B 분산 처리).
 *  - AppSecret 복호화는 v2(GCM) 우선, v2 가 비어있으면 v1(ECB) legacy fallback.
 *    Task 7 이후 신규 등록은 v1 = "" 로 마킹되므로 v2 우선 처리가 필수.
 */
@Component
class KisApiAdapter(
    private val userKisAccountRepository: UserKisAccountJpaRepository,
    private val encryptionServiceLegacy: EncryptionService,
    private val encryptionServiceGcm: EncryptionServiceGcm,
    private val tokenRepository: KisTokenJpaRepository,
    private val tokenIssuer: KisTokenIssuer,
) : TradingApiPort {
    private val logger = LoggerFactory.getLogger(KisApiAdapter::class.java)

    private data class CacheKey(val userId: String, val accountType: KisAccountType)
    private data class CachedToken(val accessToken: String, val expirationTime: LocalDateTime) {
        fun isValid(now: LocalDateTime = LocalDateTime.now()): Boolean = now.isBefore(expirationTime)
    }

    private val restClientCache = ConcurrentHashMap<CacheKey, RestClient>()
    private val accessTokenCache = ConcurrentHashMap<CacheKey, CachedToken>()
    // per-key 락. token 발급 동시성 직렬화를 ConcurrentHashMap 버킷 락 *밖* 에서 수행하기 위한 보조 자료구조.
    private val keyLocks = ConcurrentHashMap<CacheKey, ReentrantLock>()

    private val apiLock = ReentrantLock()
    private var lastApiCallNanos = 0L
    private val minApiIntervalMs = 500L

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

    private fun getAccessTokenInternal(userId: String, kisAccount: UserKisAccountEntity): String {
        val key = CacheKey(userId, kisAccount.accountType)
        val now = LocalDateTime.now()

        // 1. 메모리 캐시 확인 (락 없음)
        accessTokenCache[key]?.takeIf { it.isValid(now) }?.let { return it.accessToken }

        // 2. DB 조회 (이전 인스턴스가 발급한 토큰이 살아있는 경우)
        val dbToken = tokenRepository.findLatestTokenByUserIdAndAccountType(userId, kisAccount.accountType)
        if (dbToken.isPresent && dbToken.get().isValid()) {
            val cached = CachedToken(dbToken.get().accessToken, dbToken.get().expirationTime)
            accessTokenCache[key] = cached
            return cached.accessToken
        }

        // 3. per-key 락 안에서 double-check 후 발급. KIS 외부 호출은 ConcurrentHashMap
        //    버킷 락 *밖* 에서 수행되므로 다른 key 의 토큰 캐시 접근에 영향 없음.
        val lock = keyLocks.computeIfAbsent(key) { ReentrantLock() }
        lock.lock()
        try {
            accessTokenCache[key]?.takeIf { it.isValid(now) }?.let { return it.accessToken }

            val issued = tokenIssuer.issueNewToken(userId, kisAccount)
            val cached = CachedToken(issued.accessToken, issued.expirationTime)
            accessTokenCache[key] = cached
            return cached.accessToken
        } finally {
            lock.unlock()
        }
    }

    /**
     * 단일 인스턴스 내 KIS API 호출 간격 보장. 다중 인스턴스 분산 rate limit 은 Phase 1B 범위.
     */
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

    /**
     * KIS API 공통 헤더(인증/앱키/TR 식별자/Content-Type) 적용.
     * placeOrder/getOverseasBalance 양쪽에서 동일한 5줄을 중복하던 것을 추출.
     */
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
            kisAccount.accountType == KisAccountType.MOCK && orderType == "BUY" -> "VTTT1002U"
            kisAccount.accountType == KisAccountType.MOCK && orderType == "SELL" -> "VTTT1001U"
            kisAccount.accountType == KisAccountType.REAL && orderType == "BUY" -> "TTTT1002U"
            kisAccount.accountType == KisAccountType.REAL && orderType == "SELL" -> "TTTT1001U"
            else -> throw IllegalArgumentException("Invalid order type: $orderType")
        }

        val appSecret = decryptAppSecret(kisAccount)

        // ORD_DVSN: KIS 해외주식 주문 구분 — 현재 모든 주문을 지정가("00") 로 처리한다.
        // 시장가 진입(price="0") 분기는 KIS 명세 재확인 후 별도 PR 에서 활성화 예정.
        // 기존 `if (price == "0") "00" else "00"` 코드는 양쪽 동일 값으로 분기 무효였음 → 명시화.
        val ordDvsn = "00"

        val orderBody = mapOf(
            "CANO" to kisAccount.accountNumber,
            "ACNT_PRDT_CD" to kisAccount.accountProductCode,
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
            // 응답 메시지에 e.message 노출 시 내부 식별자가 새어나갈 수 있어 일반화 (security M-1).
            logger.error("Error placing order for $ticker", e)
            mapOf(
                "rt_cd" to "1",
                "msg1" to "Order execution failed",
            )
        }
    }
}
