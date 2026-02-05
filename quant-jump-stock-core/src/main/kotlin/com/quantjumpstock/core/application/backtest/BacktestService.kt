package com.quantjumpstock.core.application.backtest

import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.model.BacktestRequest
import com.quantjumpstock.core.events.EventTopics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * 백테스트 Application Service
 * 백테스트 요청을 처리하고 Kafka로 이벤트를 발행합니다.
 */
@Service
class BacktestService(
    private val messagePublisher: MessagePublisher
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 백테스트 요청
     * @param request 백테스트 요청 DTO
     * @param userId 사용자 ID (선택)
     * @return 백테스트 요청 응답
     */
    fun requestBacktest(request: BacktestRequestDto, userId: String?): BacktestResponse {
        val requestId = UUID.randomUUID().toString()
        val timestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toString()

        logger.info("백테스트 요청 시작: requestId=$requestId, strategyId=${request.strategyId}")

        val backtestRequest = BacktestRequest(
            requestId = requestId,
            strategyId = request.strategyId,
            startDate = request.startDate,
            endDate = request.endDate,
            initialCapital = request.initialCapital,
            timestamp = timestamp,
            source = "quantiq-core",
            userId = userId
        )

        try {
            messagePublisher.publishBacktestRequest(
                topic = EventTopics.BACKTEST_REQUEST,
                request = backtestRequest
            )

            logger.info("백테스트 요청 성공: requestId=$requestId")

            return BacktestResponse(
                success = true,
                requestId = requestId,
                message = "백테스트 요청이 성공적으로 접수되었습니다."
            )
        } catch (e: Exception) {
            logger.error("백테스트 요청 실패: requestId=$requestId", e)

            return BacktestResponse(
                success = false,
                requestId = requestId,
                message = "백테스트 요청에 실패했습니다: ${e.message}"
            )
        }
    }
}

/**
 * 백테스트 요청 DTO
 */
data class BacktestRequestDto(
    val strategyId: Long,
    val startDate: String,          // yyyy-MM-dd
    val endDate: String,            // yyyy-MM-dd
    val initialCapital: BigDecimal
)

/**
 * 백테스트 응답 DTO
 */
data class BacktestResponse(
    val success: Boolean,
    val requestId: String,
    val message: String? = null
)
