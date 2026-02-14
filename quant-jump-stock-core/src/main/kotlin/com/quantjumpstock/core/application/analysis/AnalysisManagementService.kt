package com.quantjumpstock.core.application.analysis

import com.quantjumpstock.core.domain.analysis.port.input.AnalysisUseCase
import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.economic.port.output.NotificationSender
import com.quantjumpstock.core.domain.model.AnalysisRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 분석 관리 Use Case 구현체 (Application Layer)
 * 기술적 분석, 감정 분석, 통합 분석 비즈니스 로직을 구현합니다.
 */
@Service
class AnalysisManagementService(
    private val messagePublisher: MessagePublisher,
    private val notificationSender: NotificationSender
) : AnalysisUseCase {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val kst = ZoneId.of("Asia/Seoul")

    override fun triggerTechnicalAnalysis(startDate: String?, endDate: String?): CompletableFuture<String> {
        return try {
            val dateInfo = formatDateRange(startDate, endDate)
            logger.info("기술적 분석 요청 시작 ($dateInfo)")

            val requestId = UUID.randomUUID().toString()

            // Slack 알림 전송 먼저 (스레드 루트 메시지 생성 → threadTs 반환)
            val threadTs = try {
                notificationSender.notifyTechnicalAnalysisRequest(requestId, startDate, endDate)
            } catch (e: Exception) {
                logger.warn("Slack 알림 전송 실패: ${e.message}")
                null
            }

            // threadTs와 날짜 범위를 포함한 요청 생성
            val request = AnalysisRequest(
                timestamp = ZonedDateTime.now(kst).toString(),
                source = "quartz_scheduler",
                requestId = requestId,
                threadTs = threadTs,
                analysisType = "TECHNICAL",
                startDate = startDate,
                endDate = endDate
            )

            // Kafka 이벤트 1개 발행 (날짜 범위 포함)
            messagePublisher.publishAnalysisRequest(
                TOPIC_ANALYSIS_TECHNICAL_REQUEST,
                request
            )

            logger.info("✅ Kafka 이벤트 발행 완료: requestId=$requestId, threadTs=$threadTs, type=TECHNICAL, $dateInfo")

            CompletableFuture.completedFuture("기술적 분석 요청이 Kafka에 발행되었습니다.")
        } catch (e: Exception) {
            logger.error("❌ 기술적 분석 요청 실패", e)

            // Slack 오류 알림
            try {
                notificationSender.notifyAnalysisError("unknown", "TECHNICAL", e.message ?: "Unknown error")
            } catch (slackError: Exception) {
                logger.warn("Slack 오류 알림 전송 실패")
            }

            CompletableFuture.failedFuture(e)
        }
    }

    override fun triggerSentimentAnalysis(startDate: String?, endDate: String?): CompletableFuture<String> {
        return try {
            val dateInfo = formatDateRange(startDate, endDate)
            logger.info("뉴스 감정 분석 요청 시작 ($dateInfo)")

            val requestId = UUID.randomUUID().toString()

            // Slack 알림 전송 먼저 (스레드 루트 메시지 생성 → threadTs 반환)
            val threadTs = try {
                notificationSender.notifySentimentAnalysisRequest(requestId, startDate, endDate)
            } catch (e: Exception) {
                logger.warn("Slack 알림 전송 실패: ${e.message}")
                null
            }

            // threadTs와 날짜 범위를 포함한 요청 생성
            val request = AnalysisRequest(
                timestamp = ZonedDateTime.now(kst).toString(),
                source = "quartz_scheduler",
                requestId = requestId,
                threadTs = threadTs,
                analysisType = "SENTIMENT",
                startDate = startDate,
                endDate = endDate
            )

            // Kafka 이벤트 1개 발행 (날짜 범위 포함)
            messagePublisher.publishAnalysisRequest(
                TOPIC_ANALYSIS_SENTIMENT_REQUEST,
                request
            )

            logger.info("✅ Kafka 이벤트 발행 완료: requestId=$requestId, threadTs=$threadTs, type=SENTIMENT, $dateInfo")

            CompletableFuture.completedFuture("뉴스 감정 분석 요청이 Kafka에 발행되었습니다.")
        } catch (e: Exception) {
            logger.error("❌ 뉴스 감정 분석 요청 실패", e)

            // Slack 오류 알림
            try {
                notificationSender.notifyAnalysisError("unknown", "SENTIMENT", e.message ?: "Unknown error")
            } catch (slackError: Exception) {
                logger.warn("Slack 오류 알림 전송 실패")
            }

            CompletableFuture.failedFuture(e)
        }
    }

    override fun triggerStockRecommendation(startDate: String?, endDate: String?): CompletableFuture<String> {
        return try {
            val dateInfo = formatDateRange(startDate, endDate)
            logger.info("종목 추천 요청 시작 ($dateInfo)")

            val requestId = UUID.randomUUID().toString()

            // Slack 알림 전송 먼저 (스레드 루트 메시지 생성 → threadTs 반환)
            val threadTs = try {
                notificationSender.notifyStockRecommendationRequest(requestId, startDate, endDate)
            } catch (e: Exception) {
                logger.warn("Slack 알림 전송 실패: ${e.message}")
                null
            }

            // threadTs와 날짜 범위를 포함한 요청 생성
            val request = AnalysisRequest(
                timestamp = ZonedDateTime.now(kst).toString(),
                source = "quartz_scheduler",
                requestId = requestId,
                threadTs = threadTs,
                analysisType = "RECOMMENDATION",
                startDate = startDate,
                endDate = endDate
            )

            // Pub/Sub 이벤트 발행
            messagePublisher.publishAnalysisRequest(
                TOPIC_STOCK_RECOMMENDATION_REQUEST,
                request
            )

            logger.info("✅ Pub/Sub 이벤트 발행 완료: requestId=$requestId, threadTs=$threadTs, type=RECOMMENDATION, $dateInfo")

            CompletableFuture.completedFuture("종목 추천 요청이 발행되었습니다.")
        } catch (e: Exception) {
            logger.error("❌ 종목 추천 요청 실패", e)

            // Slack 오류 알림
            try {
                notificationSender.notifyAnalysisError("unknown", "RECOMMENDATION", e.message ?: "Unknown error")
            } catch (slackError: Exception) {
                logger.warn("Slack 오류 알림 전송 실패")
            }

            CompletableFuture.failedFuture(e)
        }
    }

    private fun formatDateRange(startDate: String?, endDate: String?): String {
        return when {
            startDate != null && endDate != null -> "기간: $startDate ~ $endDate"
            startDate != null -> "시작일: $startDate ~ 오늘"
            else -> "자동 (마지막 수집일+1 ~ 오늘)"
        }
    }

    companion object {
        const val TOPIC_ANALYSIS_TECHNICAL_REQUEST = "analysis.technical.request"
        const val TOPIC_ANALYSIS_SENTIMENT_REQUEST = "analysis.sentiment.request"
        const val TOPIC_STOCK_RECOMMENDATION_REQUEST = "analysis.recommendation.request"
    }
}
