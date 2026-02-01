package com.quantjumpstock.core.application.economic

import com.quantjumpstock.core.domain.economic.port.input.EconomicDataUseCase
import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.economic.port.output.NotificationSender
import com.quantjumpstock.core.domain.economic.port.output.RestApiClient
import com.quantjumpstock.core.domain.model.EconomicDataUpdateRequest
import org.slf4j.LoggerFactory
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
    private val restApiClient: RestApiClient
) : EconomicDataUseCase {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val kst = ZoneId.of("Asia/Seoul")

    override fun triggerEconomicDataUpdate(targetDate: String?): CompletableFuture<String> {
        return try {
            val dateInfo = targetDate ?: "당일"
            logger.info("경제 데이터 업데이트 요청 시작 (기준일: $dateInfo)")

            val requestId = UUID.randomUUID().toString()

            // Slack 알림 전송 먼저 (스레드 루트 메시지 생성 → threadTs 반환)
            val threadTs = try {
                notificationSender.notifyEconomicDataUpdateRequest(requestId, targetDate)
            } catch (e: Exception) {
                logger.warn("Slack 알림 전송 실패: ${e.message}")
                null
            }

            // threadTs와 targetDate를 포함한 요청 생성
            val request = EconomicDataUpdateRequest(
                timestamp = ZonedDateTime.now(kst).toString(),
                source = "quartz_scheduler",
                requestId = requestId,
                threadTs = threadTs,
                targetDate = targetDate
            )

            // Kafka 이벤트 발행 (threadTs, targetDate 포함)
            messagePublisher.publishEconomicDataUpdateRequest(
                TOPIC_ECONOMIC_DATA_UPDATE_REQUEST,
                request
            )

            logger.info("✅ Kafka 이벤트 발행 완료: requestId=$requestId, threadTs=$threadTs, targetDate=$dateInfo")

            CompletableFuture.completedFuture("경제 데이터 업데이트 요청이 Kafka에 발행되었습니다.")
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

    override fun triggerEconomicDataUpdateViaRestApi(targetDate: String?): CompletableFuture<String> {
        return try {
            val dateInfo = targetDate ?: "당일"
            logger.info("REST API를 통해 경제 데이터 업데이트 요청 시작 (기준일: $dateInfo)")

            // REST API 호출 (Output Port 사용, targetDate 전달)
            restApiClient.callEconomicDataCollectionApi(
                "http://localhost:10020/api/economic/collect",
                targetDate
            )
        } catch (e: Exception) {
            logger.error("REST API 호출 실패", e)
            CompletableFuture.failedFuture(e)
        }
    }

    companion object {
        const val TOPIC_ECONOMIC_DATA_UPDATE_REQUEST = "economic.data.update.request"
    }
}
