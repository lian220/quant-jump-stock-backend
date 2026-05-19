package com.quantjumpstock.core.application.balance

import com.quantjumpstock.core.domain.trading.port.output.TradingApiPort
import com.quantjumpstock.core.domain.model.BalanceWithProfitResponse
import com.quantjumpstock.core.domain.model.HoldingPosition
import com.quantjumpstock.core.domain.model.AccountSummary
import com.quantjumpstock.core.domain.model.trading.Account
import com.quantjumpstock.core.domain.port.output.AccountRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.domain.port.output.UserBrokerAccountRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BalanceService(
    private val tradingApiPort: TradingApiPort,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val userBrokerAccountRepository: UserBrokerAccountRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * KIS API를 통해 해외 잔고 조회
     * @param userId 사용자 ID
     */
    @Suppress("UNCHECKED_CAST")
    fun getOverseasBalance(userId: String): Map<String, Any> {
        val result = tradingApiPort.getOverseasBalance(userId)

        val output1 = result["output1"] as? List<Map<String, Any>> ?: emptyList()
        val output2 = result["output2"] as? List<Any> ?: emptyList()

        return mapOf(
            "success" to (result["rt_cd"] == "0"),
            "holdings" to output1,
            "summary" to output2,
            "message" to (result["msg1"] ?: "")
        )
    }

    /**
     * 사용자 ID로 사용 가능한 현금 조회 (PostgreSQL)
     */
    @Transactional(readOnly = true)
    fun getAvailableCash(userId: Long): BigDecimal {
        return accountRepository.findByUserId(userId)?.availableCash() ?: BigDecimal.ZERO
    }

    /**
     * 사용자 userId(String)로 사용 가능한 현금 조회
     */
    @Transactional(readOnly = true)
    fun getAvailableCashByUserId(userId: String): BigDecimal {
        val user = userRepository.findByUserId(userId) ?: return BigDecimal.ZERO
        return user.id?.let { accountRepository.findByUserId(it)?.availableCash() } ?: BigDecimal.ZERO
    }

    /**
     * 사용자 잔액 전체 조회
     */
    @Transactional(readOnly = true)
    fun getAccountBalance(userId: Long): Account? {
        return accountRepository.findByUserId(userId)
    }

    /**
     * 사용자 잔액 전체 조회 (userId: String)
     */
    @Transactional(readOnly = true)
    fun getAccountBalanceByUserId(userId: String): Account? {
        val user = userRepository.findByUserId(userId) ?: return null
        return user.id?.let { accountRepository.findByUserId(it) }
    }

    /**
     * 현금 추가 (입금)
     */
    @Transactional
    fun addCash(userId: Long, amount: BigDecimal): Boolean {
        if (amount <= BigDecimal.ZERO) {
            logger.warn("Invalid amount for addCash: $amount")
            return false
        }
        val account = accountRepository.findByUserId(userId) ?: return false
        accountRepository.save(account.deposit(amount))
        logger.info("Added $amount cash to user $userId")
        return true
    }

    /**
     * 현금 잠금 (주문 시)
     */
    @Transactional
    fun lockCash(userId: Long, amount: BigDecimal): Boolean {
        if (amount <= BigDecimal.ZERO) {
            logger.warn("Invalid amount for lockCash: $amount")
            return false
        }
        val account = accountRepository.findByUserId(userId) ?: return false
        if (!account.canPlaceOrder(amount)) {
            logger.warn("Failed to lock cash for user $userId (insufficient funds?)")
            return false
        }
        accountRepository.save(account.lockCash(amount))
        logger.info("Locked $amount cash for user $userId")
        return true
    }

    /**
     * 현금 잠금 해제 (주문 취소 또는 체결 실패 시)
     */
    @Transactional
    fun unlockCash(userId: Long, amount: BigDecimal): Boolean {
        if (amount <= BigDecimal.ZERO) {
            logger.warn("Invalid amount for unlockCash: $amount")
            return false
        }
        val account = accountRepository.findByUserId(userId) ?: return false
        if (account.lockedCash < amount) {
            logger.warn("Failed to unlock cash for user $userId")
            return false
        }
        accountRepository.save(account.unlockCash(amount))
        logger.info("Unlocked $amount cash for user $userId")
        return true
    }

    /**
     * 주문 체결 시 잔액 업데이트
     * - 매수: lockedCash 차감, cash 차감
     * - 매도: cash 증가
     */
    @Transactional
    fun executeTradeBalance(userId: Long, amount: BigDecimal, isBuy: Boolean): Boolean {
        val account = accountRepository.findByUserId(userId) ?: return false

        val updatedAccount = if (isBuy) {
            if (account.lockedCash < amount) {
                logger.warn("Insufficient locked cash for trade execution")
                return false
            }
            account.executeBuy(amount)
        } else {
            account.executeSell(amount)
        }

        accountRepository.save(updatedAccount)
        logger.info("Trade balance updated for user $userId: isBuy=$isBuy, amount=$amount")
        return true
    }

    /**
     * 신규 사용자 잔액 초기화
     */
    @Transactional
    fun initializeBalance(userId: Long, initialCash: BigDecimal = BigDecimal("1000000")): Account {
        val account = Account.createNew(userId, initialCash)
        return accountRepository.save(account)
    }

    /**
     * 수익 정보 조회
     */
    fun getTotalProfit(): Map<String, Any> {
        return mapOf(
            "success" to true,
            "total_profit_usd" to 0.0,
            "total_assets_usd" to 0.0,
            "exchange_rate" to 0.0
        )
    }

    /**
     * User 기준 잔고 및 수익률 조회 (KIS API)
     * @param userId 사용자 ID (String)
     * @return 보유 종목, 수익률, 계좌 요약 정보
     */
    @Suppress("UNCHECKED_CAST")
    @Transactional(readOnly = false)
    fun getBalanceWithProfit(userId: String): BalanceWithProfitResponse {
        logger.info("💰 Fetching balance and profit for user: $userId")

        // 1. 사용자 KIS broker 계좌 정보 조회 (Phase 1D: user_broker_accounts 사용)
        val kisAccount = userBrokerAccountRepository.findFirstActiveKisByUserLoginId(userId)
            ?: throw IllegalArgumentException("User KIS broker account not found or not active: $userId")

        // 2. KIS API 호출 (사용자별 인증 정보 사용)
        val kisResponse = tradingApiPort.getOverseasBalance(userId)

        // 3. KIS API 응답 파싱
        val output1 = kisResponse["output1"] as? List<Map<String, Any>> ?: emptyList()
        val output2 = kisResponse["output2"] as? Map<String, Any> ?: emptyMap()

        // 4. 보유 종목 리스트 변환
        val holdings = output1.map { item ->
            HoldingPosition(
                ticker = item["pdno"] as? String ?: "",
                name = item["prdt_name"] as? String ?: "",
                quantity = (item["ovrs_cblc_qty"] as? String)?.toIntOrNull() ?: 0,
                averagePrice = (item["pchs_avg_pric"] as? String)?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                currentPrice = (item["now_pric2"] as? String)?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                evaluationAmount = (item["ovrs_stck_evlu_amt"] as? String)?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                profitAmount = (item["evlu_pfls_amt"] as? String)?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                profitRate = (item["evlu_pfls_rt"] as? String)?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                currency = item["crcy_cd"] as? String ?: "USD",
                exchange = item["ovrs_excg_cd"] as? String ?: "NASD"
            )
        }

        // 5. 계좌 전체 요약 정보
        val totalPurchaseAmount = (output2["frcr_pchs_amt1"] as? String)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val totalEvaluationAmount = (output2["tot_evlu_amt"] as? String)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val realizedProfit = (output2["ovrs_rlzt_pfls_amt"] as? String)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val unrealizedProfit = (output2["evlu_pfls_smtl_amt"] as? String)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val totalProfit = (output2["ovrs_tot_pfls"] as? String)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val totalProfitRate = (output2["rlzt_erng_rt"] as? String)?.toBigDecimalOrNull() ?: BigDecimal.ZERO

        val summary = AccountSummary(
            totalPurchaseAmount = totalPurchaseAmount,
            totalEvaluationAmount = totalEvaluationAmount,
            realizedProfit = realizedProfit,
            unrealizedProfit = unrealizedProfit,
            totalProfit = totalProfit,
            totalProfitRate = totalProfitRate,
            currency = "USD"
        )

        // 6. 현금 잔고 (PostgreSQL)
        val cashBalance = getAvailableCashByUserId(userId)

        // 7. 총 자산
        val totalAssets = cashBalance + totalEvaluationAmount

        logger.info("✅ Balance fetched: holdings=${holdings.size}, totalProfit=$totalProfit, profitRate=$totalProfitRate%")

        return BalanceWithProfitResponse(
            userId = userId,
            accountNumber = kisAccount.accountNumber,
            holdings = holdings,
            summary = summary,
            cashBalance = cashBalance,
            totalAssets = totalAssets,
            timestamp = LocalDateTime.now().toString()
        )
    }
}
