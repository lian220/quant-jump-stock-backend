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

    override fun triggerTechnicalAnalysis(targetDate: String?): CompletableFuture<String> {
        return try {
            val dateInfo = targetDate ?: "당일"
            logger.info("기술적 분석 요청 시작 (기준일: $dateInfo)")

            val requestId = UUID.randomUUID().toString()

            // Slack 알림 전송 먼저 (스레드 루트 메시지 생성 → threadTs 반환)
            val threadTs = try {
                notificationSender.notifyTechnicalAnalysisRequest(requestId, targetDate)
            } catch (e: Exception) {
                logger.warn("Slack 알림 전송 실패: ${e.message}")
                null
            }

            // threadTs와 targetDate를 포함한 요청 생성
            val request = AnalysisRequest(
                timestamp = ZonedDateTime.now(kst).toString(),
                source = "quartz_scheduler",
                requestId = requestId,
                threadTs = threadTs,
                analysisType = "TECHNICAL",
                targetDate = targetDate
            )

            // Kafka 이벤트 발행 (threadTs, targetDate 포함)
            messagePublisher.publishAnalysisRequest(
                TOPIC_ANALYSIS_TECHNICAL_REQUEST,
                request
            )

            logger.info("✅ Kafka 이벤트 발행 완료: requestId=$requestId, threadTs=$threadTs, type=TECHNICAL, targetDate=$dateInfo")

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

    override fun triggerSentimentAnalysis(targetDate: String?): CompletableFuture<String> {
        return try {
            val dateInfo = targetDate ?: "당일"
            logger.info("뉴스 감정 분석 요청 시작 (기준일: $dateInfo)")

            val requestId = UUID.randomUUID().toString()

            // Slack 알림 전송 먼저 (스레드 루트 메시지 생성 → threadTs 반환)
            val threadTs = try {
                notificationSender.notifySentimentAnalysisRequest(requestId, targetDate)
            } catch (e: Exception) {
                logger.warn("Slack 알림 전송 실패: ${e.message}")
                null
            }

            // threadTs와 targetDate를 포함한 요청 생성
            val request = AnalysisRequest(
                timestamp = ZonedDateTime.now(kst).toString(),
                source = "quartz_scheduler",
                requestId = requestId,
                threadTs = threadTs,
                analysisType = "SENTIMENT",
                targetDate = targetDate
            )

            // Kafka 이벤트 발행 (threadTs, targetDate 포함)
            messagePublisher.publishAnalysisRequest(
                TOPIC_ANALYSIS_SENTIMENT_REQUEST,
                request
            )

            logger.info("✅ Kafka 이벤트 발행 완료: requestId=$requestId, threadTs=$threadTs, type=SENTIMENT, targetDate=$dateInfo")

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

    override fun triggerCombinedAnalysis(targetDate: String?): CompletableFuture<String> {
        return try {
            val dateInfo = targetDate ?: "당일"
            logger.info("통합 분석 요청 시작 (기준일: $dateInfo)")

            val requestId = UUID.randomUUID().toString()

            // Slack 알림 전송 먼저 (스레드 루트 메시지 생성 → threadTs 반환)
            val threadTs = try {
                notificationSender.notifyCombinedAnalysisRequest(requestId, targetDate)
            } catch (slackError: Exception) {
                logger.warn("Slack 알림 전송 실패: ${slackError.message}")
                null
            }

            // threadTs와 targetDate를 포함한 요청 생성
            val request = AnalysisRequest(
                timestamp = ZonedDateTime.now(kst).toString(),
                source = "quartz_scheduler",
                requestId = requestId,
                threadTs = threadTs,
                analysisType = "COMBINED",
                targetDate = targetDate
            )

            // Kafka 이벤트 발행 (threadTs, targetDate 포함)
            messagePublisher.publishAnalysisRequest(
                TOPIC_ANALYSIS_COMBINED_REQUEST,
                request
            )

            logger.info("✅ Kafka 이벤트 발행 완료: requestId=$requestId, threadTs=$threadTs, type=COMBINED, targetDate=$dateInfo")

            CompletableFuture.completedFuture("통합 분석 요청이 Kafka에 발행되었습니다.")
        } catch (e: Exception) {
            logger.error("❌ 통합 분석 요청 실패", e)

            // Slack 오류 알림
            try {
                notificationSender.notifyAnalysisError("unknown", "COMBINED", e.message ?: "Unknown error")
            } catch (slackError: Exception) {
                logger.warn("Slack 오류 알림 전송 실패")
            }

            CompletableFuture.failedFuture(e)
        }
    }

    companion object {
        const val TOPIC_ANALYSIS_TECHNICAL_REQUEST = "analysis.technical.request"
        const val TOPIC_ANALYSIS_SENTIMENT_REQUEST = "analysis.sentiment.request"
        const val TOPIC_ANALYSIS_COMBINED_REQUEST = "analysis.combined.request"
    }
}
