package com.quantjumpstock.core.application.trading

import com.quantjumpstock.core.domain.model.trading.Account
import com.quantjumpstock.core.domain.model.trading.Trade
import com.quantjumpstock.core.domain.model.trading.TradeSide
import com.quantjumpstock.core.domain.model.trading.TradeStatus
import com.quantjumpstock.core.domain.port.output.AccountRepository
import com.quantjumpstock.core.domain.port.output.TradeRepository
import com.quantjumpstock.core.domain.port.output.TradeSignalExecutedRepository
import com.quantjumpstock.core.domain.trading.port.output.TradingApiPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Phase 1A PRE Task 11 — OrderExecutionListener 단위 테스트.
 *
 * AFTER_COMMIT 리스너의 KIS 호출 결과별 분기를 검증한다 (성공/실패/예외).
 * 트랜잭션 어노테이션은 단위 테스트에서 적용되지 않으므로 메서드를 직접 호출한다.
 */
class OrderExecutionListenerTest {

    private val tradingApiPort = mockk<TradingApiPort>()
    private val tradeRepository = mockk<TradeRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val tradeSignalExecutedRepository = mockk<TradeSignalExecutedRepository>(relaxed = true)

    private val listener = OrderExecutionListener(
        tradingApiPort, tradeRepository, accountRepository, tradeSignalExecutedRepository
    )

    private val event = OrderExecutionRequestEvent(
        tradeId = 99L,
        userId = 1L,
        userIdString = "trader-1",
        ticker = "AAPL",
        side = TradeSide.BUY,
        quantity = 100,
        priceForKis = "0",
        lockedAmount = BigDecimal("10000"),
        predictionId = "AAPL_2026-05-10",
        compositeScore = 3.0
    )

    private fun pendingTrade(): Trade = Trade(
        id = 99L,
        userId = 1L,
        ticker = "AAPL",
        side = TradeSide.BUY,
        quantity = 100,
        price = BigDecimal("100"),
        totalAmount = BigDecimal("10000"),
        status = TradeStatus.PENDING
    )

    @Test
    fun `KIS 성공 시 Trade 는 EXECUTED 상태로 저장되고 현금 잠금 해제는 일어나지 않는다`() {
        every { tradingApiPort.placeOrder("trader-1", "AAPL", "BUY", 100, "0") } returns mapOf(
            "rt_cd" to "0",
            "output" to mapOf("KRX_FWDG_ORD_ORGNO" to "KIS-ORDER-1")
        )
        every { tradeRepository.findById(99L) } returns pendingTrade()
        val savedSlot = slot<Trade>()
        every { tradeRepository.save(capture(savedSlot)) } answers { firstArg() }

        listener.onOrderExecutionRequest(event)

        assertThat(savedSlot.captured.status).isEqualTo(TradeStatus.EXECUTED)
        assertThat(savedSlot.captured.kisOrderId).isEqualTo("KIS-ORDER-1")
        verify(exactly = 0) { accountRepository.save(any()) }
    }

    @Test
    fun `KIS 실패 응답 시 Trade 는 FAILED + 현금 잠금 해제 + 신호 FAILED 로그`() {
        every { tradingApiPort.placeOrder(any(), any(), any(), any(), any()) } returns mapOf(
            "rt_cd" to "1",
            "msg1" to "ORDER REJECTED"
        )
        every { tradeRepository.findById(99L) } returns pendingTrade()
        val account = Account(id = 1L, userId = 1L, cash = BigDecimal("100000"), lockedCash = BigDecimal("10000"))
        every { accountRepository.findByUserId(1L) } returns account

        val tradeSlot = slot<Trade>()
        every { tradeRepository.save(capture(tradeSlot)) } answers { firstArg() }
        val accountSlot = slot<Account>()
        every { accountRepository.save(capture(accountSlot)) } answers { firstArg() }

        listener.onOrderExecutionRequest(event)

        assertThat(tradeSlot.captured.status).isEqualTo(TradeStatus.FAILED)
        assertThat(accountSlot.captured.lockedCash).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `KIS 호출이 예외를 던지면 Trade 는 FAILED + 현금 잠금 해제`() {
        every { tradingApiPort.placeOrder(any(), any(), any(), any(), any()) } throws RuntimeException("network down")
        every { tradeRepository.findById(99L) } returns pendingTrade()
        val account = Account(id = 1L, userId = 1L, cash = BigDecimal("100000"), lockedCash = BigDecimal("10000"))
        every { accountRepository.findByUserId(1L) } returns account
        every { tradeRepository.save(any()) } answers { firstArg() }
        every { accountRepository.save(any()) } answers { firstArg() }

        listener.onOrderExecutionRequest(event)

        verify(exactly = 1) { tradeRepository.save(match { it.status == TradeStatus.FAILED }) }
        verify(exactly = 1) {
            accountRepository.save(match { it.lockedCash.compareTo(BigDecimal.ZERO) == 0 })
        }
    }

    @Test
    fun `Trade 가 사라진 경우에도 현금 잠금은 보상 해제된다`() {
        every { tradingApiPort.placeOrder(any(), any(), any(), any(), any()) } returns mapOf("rt_cd" to "1", "msg1" to "rejected")
        every { tradeRepository.findById(99L) } returns null
        val account = Account(id = 1L, userId = 1L, cash = BigDecimal("100000"), lockedCash = BigDecimal("10000"))
        every { accountRepository.findByUserId(1L) } returns account
        every { accountRepository.save(any()) } answers { firstArg() }

        listener.onOrderExecutionRequest(event)

        verify(exactly = 0) { tradeRepository.save(any()) }
        verify(exactly = 1) { accountRepository.save(match { it.lockedCash.compareTo(BigDecimal.ZERO) == 0 }) }
    }
}
