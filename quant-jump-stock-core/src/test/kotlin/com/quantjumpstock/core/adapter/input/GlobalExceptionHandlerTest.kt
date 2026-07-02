package com.quantjumpstock.core.adapter.input

import com.quantjumpstock.core.application.backtest.BacktestException
import com.quantjumpstock.core.application.backtest.BacktestNotFoundException
import com.quantjumpstock.core.application.portfolio.PortfolioAccessDeniedException
import com.quantjumpstock.core.application.portfolio.PortfolioException
import com.quantjumpstock.core.application.portfolio.PortfolioNotFoundException
import com.quantjumpstock.core.application.stock.StockException
import com.quantjumpstock.core.application.stock.StockNotFoundException
import com.quantjumpstock.core.application.strategy.StrategyException
import com.quantjumpstock.core.application.strategy.StrategyNotFoundException
import com.quantjumpstock.core.application.subscription.SubscriptionException
import com.quantjumpstock.core.domain.port.output.ErrorNotifier
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import com.quantjumpstock.core.application.marketplace.StrategyNotFoundException as MarketplaceStrategyNotFoundException

/**
 * 도메인 예외 → HTTP 응답 매핑 단위 테스트.
 *
 * 컨트롤러 개별 try/catch 를 GlobalExceptionHandler 로 이관하면서
 * 기존 상태코드 계약(404/403/400/500, Subscription 동적 status)이 유지되는지 검증한다.
 */
@DisplayName("GlobalExceptionHandler 도메인 예외 매핑")
class GlobalExceptionHandlerTest {

    private val errorNotifier = mockk<ErrorNotifier>(relaxed = true)
    private val handler = GlobalExceptionHandler(errorNotifier)

    // ===== Portfolio =====

    @Test
    fun `PortfolioNotFoundException 은 404 와 메시지를 반환한다`() {
        val response = handler.handlePortfolioNotFoundException(
            PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다: 1")
        )

        response.statusCode shouldBe HttpStatus.NOT_FOUND
        response.body?.message shouldBe "포트폴리오를 찾을 수 없습니다: 1"
        response.body?.status shouldBe 404
    }

    @Test
    fun `PortfolioAccessDeniedException 은 403 을 반환한다`() {
        val response = handler.handlePortfolioAccessDeniedException(
            PortfolioAccessDeniedException("포트폴리오에 대한 접근 권한이 없습니다")
        )

        response.statusCode shouldBe HttpStatus.FORBIDDEN
        response.body?.message shouldBe "포트폴리오에 대한 접근 권한이 없습니다"
    }

    @Test
    fun `PortfolioException 은 400 을 반환한다`() {
        val response = handler.handlePortfolioException(
            PortfolioException("비중 합계가 100%를 초과할 수 없습니다")
        )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.body?.message shouldBe "비중 합계가 100%를 초과할 수 없습니다"
    }

    // ===== Stock =====

    @Test
    fun `StockNotFoundException 은 404 를 반환한다`() {
        val response = handler.handleStockNotFoundException(
            StockNotFoundException("종목을 찾을 수 없습니다: 99")
        )

        response.statusCode shouldBe HttpStatus.NOT_FOUND
        response.body?.message shouldBe "종목을 찾을 수 없습니다: 99"
    }

    @Test
    fun `StockException 은 400 을 반환한다`() {
        val response = handler.handleStockException(
            StockException("이미 등록된 종목 코드입니다: AAPL")
        )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.body?.message shouldBe "이미 등록된 종목 코드입니다: AAPL"
    }

    // ===== Strategy =====

    @Test
    fun `strategy 패키지 StrategyNotFoundException 은 404 를 반환한다`() {
        val response = handler.handleStrategyNotFoundException(
            StrategyNotFoundException("전략을 찾을 수 없습니다: 7")
        )

        response.statusCode shouldBe HttpStatus.NOT_FOUND
        response.body?.message shouldBe "전략을 찾을 수 없습니다: 7"
    }

    @Test
    fun `marketplace 패키지 StrategyNotFoundException 도 기존대로 404 를 반환한다`() {
        val response = handler.handleStrategyNotFoundException(
            MarketplaceStrategyNotFoundException("전략을 찾을 수 없습니다: 8")
        )

        response.statusCode shouldBe HttpStatus.NOT_FOUND
    }

    @Test
    fun `StrategyException 은 400 을 반환한다`() {
        val response = handler.handleStrategyException(
            StrategyException("구독자가 있는 전략은 삭제할 수 없습니다. 먼저 비공개로 전환하세요.")
        )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.body?.message shouldBe "구독자가 있는 전략은 삭제할 수 없습니다. 먼저 비공개로 전환하세요."
    }

    // ===== Backtest =====

    @Test
    fun `BacktestNotFoundException 은 404 를 반환한다`() {
        val response = handler.handleBacktestNotFoundException(
            BacktestNotFoundException("백테스트 결과를 찾을 수 없습니다: id=3")
        )

        response.statusCode shouldBe HttpStatus.NOT_FOUND
        response.body?.message shouldBe "백테스트 결과를 찾을 수 없습니다: id=3"
    }

    @Test
    fun `BacktestException 은 500 과 메시지를 반환한다`() {
        val response = handler.handleBacktestException(
            BacktestException("백테스트 요청에 실패했습니다: pubsub down")
        )

        response.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
        response.body?.message shouldBe "백테스트 요청에 실패했습니다: pubsub down"
    }

    // ===== Subscription (동적 status + errorCode + extra 계약 보존) =====

    @Test
    fun `SubscriptionException 은 예외에 담긴 httpStatus 와 errorCode 를 그대로 반환한다`() {
        val response = handler.handleSubscriptionException(
            SubscriptionException("STRATEGY_NOT_FOUND", "전략을 찾을 수 없습니다: 5", 404)
        )

        response.statusCode.value() shouldBe 404
        response.body?.get("error") shouldBe "STRATEGY_NOT_FOUND"
        response.body?.get("message") shouldBe "전략을 찾을 수 없습니다: 5"
    }

    @Test
    fun `SubscriptionException 의 extra 필드는 응답 바디에 병합된다`() {
        val response = handler.handleSubscriptionException(
            SubscriptionException(
                errorCode = "SUBSCRIPTION_LIMIT_EXCEEDED",
                message = "구독 제한을 초과했습니다.",
                httpStatus = 403,
                extra = mapOf("limit" to 3, "current" to 3)
            )
        )

        response.statusCode.value() shouldBe 403
        response.body?.get("error") shouldBe "SUBSCRIPTION_LIMIT_EXCEEDED"
        response.body?.get("limit") shouldBe 3
        response.body?.get("current") shouldBe 3
    }
}
