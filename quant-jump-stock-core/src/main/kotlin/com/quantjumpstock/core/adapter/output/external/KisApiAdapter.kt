package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import com.quantjumpstock.core.infrastructure.security.EncryptionService
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
 * TradingApiPort를 구현하여 한국투자증권 API와 연동합니다.
 *
 * ⚠️ 사용자별 KIS 계정 정보를 DB에서 조회하여 사용합니다
 */
@Component
class KisApiAdapter(
    private val userKisAccountRepository: UserKisAccountJpaRepository,
    private val encryptionService: EncryptionService,
    private val tokenRepository: KisTokenJpaRepository
) : TradingApiPort {
    private val logger = LoggerFactory.getLogger(KisApiAdapter::class.java)

    // 사용자별 RestClient 캐시
    private val restClientCache = ConcurrentHashMap<String, RestClient>()

    private val lastApiCallTime = AtomicLong(0)
    private val minApiIntervalMs = 500L
    private val apiLock = ReentrantLock()

    // 사용자별 액세스 토큰 캐시
    private val accessTokenCache = ConcurrentHashMap<String, Pair<String, LocalDateTime>>()

    /**
     * 사용자별 활성화된 KIS 계정 조회
     */
    private fun getActiveKisAccount(userId: String): UserKisAccountEntity {
        return userKisAccountRepository.findActiveByUserUserId(userId)
            .orElseThrow { IllegalStateException("KIS account not found for user: $userId") }
    }

    /**
     * 사용자 ID로 RestClient 생성 또는 캐시에서 반환
     */
    private fun getRestClientForUser(userId: String): RestClient {
        return restClientCache.computeIfAbsent(userId) {
            val kisAccount = getActiveKisAccount(userId)
            val baseUrl = KisConfig.getBaseUrlForAccountType(kisAccount.accountType)
            logger.info("Creating RestClient for user $userId with ${kisAccount.accountType} account: $baseUrl")

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
        val now = LocalDateTime.now()

        // 1. Memory cache
        val cached = accessTokenCache[userId]
        if (cached != null && now.isBefore(cached.second)) {
            return cached.first
        }

        // 2. DB cache (PostgreSQL)
        val kisAccount = getActiveKisAccount(userId)
        val tokenEntity = tokenRepository.findLatestTokenByUserIdAndAccountType(userId, kisAccount.accountType)

        if (tokenEntity.isPresent && tokenEntity.get().isValid()) {
            val token = tokenEntity.get()
            accessTokenCache[userId] = Pair(token.accessToken, token.expirationTime)
            return token.accessToken
        }

        // 3. New token from KIS
        return refreshToken(userId, kisAccount)
    }

    @Transactional(readOnly = false)
    private fun refreshToken(userId: String, kisAccount: UserKisAccountEntity): String {
        logger.info("Refreshing KIS access token for user: $userId, type: ${kisAccount.accountType}")

        val appSecret = encryptionService.decrypt(kisAccount.appSecretEncrypted)
        val restClient = getRestClientForUser(userId)

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

        // 기존 토큰 비활성화
        tokenRepository.deactivateUserTokens(
            kisAccount.user.id ?: throw IllegalStateException("User ID is null"),
            kisAccount.accountType,
            LocalDateTime.now()
        )

        // 새 토큰 저장 (PostgreSQL)
        val kisTokenEntity = KisTokenEntity(
            user = kisAccount.user,
            accountType = kisAccount.accountType,
            accessToken = token,
            expirationTime = expirationTime
        )
        tokenRepository.save(kisTokenEntity)

        accessTokenCache[userId] = Pair(token, expirationTime)

        return token
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
        val token = getAccessToken(userId)
        val restClient = getRestClientForUser(userId)

        // TR_ID: 모의투자(VTTS3012R), 실전투자(TTTS3012R)
        val trId = when (kisAccount.accountType) {
            KisAccountType.MOCK -> "VTTS3012R"
            KisAccountType.REAL -> "TTTS3012R"
        }

        val appSecret = encryptionService.decrypt(kisAccount.appSecretEncrypted)

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

    /**
     * 해외 주식 주문 실행
     * @param userId 사용자 ID
     * @param ticker 종목 코드 (예: AAPL)
     * @param orderType 주문 유형 (BUY/SELL)
     * @param quantity 수량
     * @param price 가격 (시장가 주문: "0")
     */
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
        val token = getAccessToken(userId)
        val restClient = getRestClientForUser(userId)

        // TR_ID: 모의투자 매수(VTTT1002U), 매도(VTTT1001U), 실전투자 매수(TTTT1002U), 매도(TTTT1001U)
        val trId = when {
            kisAccount.accountType == KisAccountType.MOCK && orderType == "BUY" -> "VTTT1002U"
            kisAccount.accountType == KisAccountType.MOCK && orderType == "SELL" -> "VTTT1001U"
            kisAccount.accountType == KisAccountType.REAL && orderType == "BUY" -> "TTTT1002U"
            kisAccount.accountType == KisAccountType.REAL && orderType == "SELL" -> "TTTT1001U"
            else -> throw IllegalArgumentException("Invalid order type: $orderType")
        }

        val appSecret = encryptionService.decrypt(kisAccount.appSecretEncrypted)

        // KIS API 주문 요청 Body
        val orderBody = mapOf(
            "CANO" to kisAccount.accountNumber,
            "ACNT_PRDT_CD" to kisAccount.accountProductCode,
            "OVRS_EXCG_CD" to "NASD", // NASD, NYSE, AMEX 등
            "PDNO" to ticker,
            "ORD_QTY" to quantity.toString(),
            "OVRS_ORD_UNPR" to price, // 0: 시장가, 지정가: 가격
            "ORD_SVR_DVSN_CD" to "0", // 0: 해외주식
            "ORD_DVSN" to if (price == "0") "00" else "00" // 00: 지정가, 01: 시장가
        )

        logger.info("🔄 Placing $orderType order for $userId: $ticker x$quantity @ $price")

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
                logger.info("✅ Order placed successfully: $ticker x$quantity")
            } else {
                logger.error("❌ Order failed: ${result["msg1"]}")
            }

            result
        } catch (e: Exception) {
            logger.error("❌ Error placing order for $ticker", e)
            mapOf(
                "rt_cd" to "1",
                "msg1" to "Order execution failed: ${e.message}"
            )
        }
    }
}
