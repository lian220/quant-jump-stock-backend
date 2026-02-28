package com.quantjumpstock.core.application.economic

import com.quantjumpstock.core.domain.economic.port.input.EconomicDataUseCase
import com.quantjumpstock.core.infrastructure.util.DateRangeFormatter
import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.economic.port.output.NotificationSender
import com.quantjumpstock.core.domain.economic.port.output.RestApiClient
import com.quantjumpstock.core.domain.model.EconomicDataUpdateRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 경제 데이터 업데이트 Use Case 구현체 (Application Layer)
 * 경제 데이터 수집 트리거 비즈니스 로직을 구현합니다.
 */
@Service
class EconomicDataManagementService(
    private val messagePublisher: MessagePublisher,
    private val notificationSender: NotificationSender,
    private val restApiClient: RestApiClient,
    @Value("\${data-engine.base-url:http://localhost:10020}")
    private val dataEngineBaseUrl: String
) : EconomicDataUseCase {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val kst = ZoneId.of("Asia/Seoul")

    override fun triggerEconomicDataUpdate(startDate: String?, endDate: String?, source: String): CompletableFuture<String> {
        return try {
            val dateInfo = formatDateRange(startDate, endDate)
            logger.info("경제 데이터 업데이트 요청 시작 ($dateInfo, source=$source)")

            val requestId = UUID.randomUUID().toString()

            // Slack 알림 전송 먼저 (스레드 루트 메시지 생성 → threadTs 반환)
            val threadTs = try {
                notificationSender.notifyEconomicDataUpdateRequest(requestId, startDate, endDate)
            } catch (e: Exception) {
                logger.warn("Slack 알림 전송 실패: ${e.message}")
                null
            }

            // threadTs와 날짜 범위를 포함한 요청 생성
            val request = EconomicDataUpdateRequest(
                timestamp = ZonedDateTime.now(kst).toString(),
                source = source,
                requestId = requestId,
                threadTs = threadTs,
                startDate = startDate,
                endDate = endDate
            )

            // Pub/Sub 메시지 발행 (날짜 범위 포함)
            messagePublisher.publishEconomicDataUpdateRequest(
                TOPIC_ECONOMIC_DATA_UPDATE_REQUEST,
                request
            )

            logger.info("✅ Pub/Sub 메시지 발행 완료: requestId=$requestId, threadTs=$threadTs, $dateInfo")

            CompletableFuture.completedFuture("경제 데이터 업데이트 요청이 Pub/Sub에 발행되었습니다.")
        } catch (e: Exception) {
            logger.error("❌ 경제 데이터 업데이트 요청 실패", e)

            // Slack 오류 알림 (Output Port 사용)
            try {
                notificationSender.notifyEconomicDataCollectionError(
                    "unknown",
                    e.message ?: "Unknown error"
                )
            } catch (slackError: Exception) {
                logger.warn("Slack 오류 알림 전송 실패")
            }

            CompletableFuture.failedFuture(e)
        }
    }

    override fun triggerEconomicDataUpdateViaRestApi(startDate: String?, endDate: String?): CompletableFuture<String> {
        return try {
            val dateInfo = formatDateRange(startDate, endDate)
            logger.info("REST API를 통해 경제 데이터 업데이트 요청 시작 ($dateInfo)")

            // REST API 호출 (Output Port 사용, startDate 전달)
            restApiClient.callEconomicDataCollectionApi(
                "$dataEngineBaseUrl/api/economic/collect",
                startDate
            )
        } catch (e: Exception) {
            logger.error("REST API 호출 실패", e)
            CompletableFuture.failedFuture(e)
        }
    }

    private fun formatDateRange(startDate: String?, endDate: String?): String =
        DateRangeFormatter.format(startDate, endDate)

    companion object {
        const val TOPIC_ECONOMIC_DATA_UPDATE_REQUEST = "economic.data.update.request"
    }
}
