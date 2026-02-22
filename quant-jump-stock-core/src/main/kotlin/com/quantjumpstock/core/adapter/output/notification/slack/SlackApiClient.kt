package com.quantjumpstock.core.adapter.output.notification.slack

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.quantjumpstock.core.infrastructure.util.DateRangeFormatter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.ZonedDateTime
import java.time.ZoneId

/**
 * Slack API Client (Output Adapter Infrastructure)
 * Slack API와 직접 통신하여 알림을 전송합니다.
 */
@Component
class SlackApiClient(
    private val webClient: WebClient
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val kst = ZoneId.of("Asia/Seoul")

    @Value("\${slack.enabled:true}")
    private var slackEnabled: Boolean = true

    @Value("\${slack.bot-token:}")
    private lateinit var slackBotToken: String

    @Value("\${slack.channel:#trading-alerts}")
    private lateinit var slackChannel: String

    @Value("\${slack.webhook-url:}")
    private lateinit var slackWebhookUrl: String

    @Value("\${slack.webhook-url-error:}")
    private lateinit var slackWebhookUrlError: String

    private fun isDisabled(): Boolean {
        if (!slackEnabled) {
            logger.debug("Slack 비활성화 상태 (slack.enabled=false), 알림 건너뜀")
            return true
        }
        return false
    }

    private fun getCurrentTimeKST(): String = ZonedDateTime.now(kst).toString()

    /**
     * 경제 데이터 업데이트 요청 알림 (스레드 루트 메시지)
     *
     * @param requestId 요청 ID
     * @param startDate 수집 시작 날짜, null이면 자동 결정
     * @param endDate 수집 종료 날짜, null이면 오늘
     * @return Slack 스레드 타임스탬프 (답글용)
     */
    fun notifyEconomicDataUpdateRequest(requestId: String, startDate: String? = null, endDate: String? = null): String? {
        if (isDisabled()) return null
        val dateInfo = formatDateRange(startDate, endDate)

        if (slackBotToken.isBlank()) {
            logger.warn("⚠️ Slack Bot Token 없음 - Webhook으로 fallback")
            notifyViaWebhook(requestId, dateInfo)
            return null
        }

        try {
            val message = SlackApiMessage(
                channel = slackChannel,
                text = "📊 경제 데이터 업데이트 요청",
                attachments = listOf(
                    SlackAttachment(
                        color = "#36a64f",
                        title = "경제 데이터 수집 요청",
                        text = "경제 데이터 업데이트 요청이 발행되었습니다.",
                        fields = listOf(
                            SlackField("Request ID", requestId, true),
                            SlackField("Date Range", "📅 $dateInfo", true),
                            SlackField("Timestamp", getCurrentTimeKST(), true),
                            SlackField("Source", "Quartz Scheduler", true),
                            SlackField("Status", "🔄 Processing", true)
                        )
                    )
                )
            )

            val response = sendToSlackApi(message)
            val threadTs = response?.ts

            if (threadTs != null) {
                logger.info("✅ Slack 스레드 루트 생성: requestId=$requestId, threadTs=$threadTs")
            } else {
                logger.warn("⚠️ Slack 메시지 발송 성공하지만 threadTs 없음")
            }

            return threadTs
        } catch (e: Exception) {
            logger.error("❌ Slack API 알림 발송 실패", e)
            return null
        }
    }

    /**
     * Webhook으로 알림 전송 (Thread 미지원 - Fallback)
     */
    private fun notifyViaWebhook(requestId: String, dateInfo: String) {
        if (slackWebhookUrl.isBlank()) {
            logger.debug("Slack webhook URL not configured, skipping notification")
            return
        }

        try {
            val message = SlackMessage(
                text = "📊 경제 데이터 업데이트 요청",
                attachments = listOf(
                    SlackAttachment(
                        color = "#36a64f",
                        title = "경제 데이터 수집 요청",
                        text = "경제 데이터 업데이트 요청이 발행되었습니다.",
                        fields = listOf(
                            SlackField("Request ID", requestId, true),
                            SlackField("Date Range", "📅 $dateInfo", true),
                            SlackField("Timestamp", getCurrentTimeKST(), true),
                            SlackField("Source", "Quartz Scheduler", true),
                            SlackField("Status", "🔄 Processing", true)
                        )
                    )
                )
            )

            sendToSlackWebhook(message)
            logger.info("✅ Slack 알림 발송 완료 (Webhook): $requestId")
        } catch (e: Exception) {
            logger.error("❌ Slack Webhook 알림 발송 실패", e)
        }
    }

    /**
     * 경제 데이터 수집 오류 알림
     */
    fun notifyEconomicDataCollectionError(requestId: String, error: String) {
        if (isDisabled()) return
        if (slackWebhookUrl.isBlank()) return

        try {
            val message = SlackMessage(
                text = "⚠️ 경제 데이터 수집 오류",
                attachments = listOf(
                    SlackAttachment(
                        color = "#dc3545",
                        title = "경제 데이터 수집 실패",
                        text = "경제 데이터 수집 중 오류가 발생했습니다.",
                        fields = listOf(
                            SlackField("Request ID", requestId, true),
                            SlackField("Error", error, false),
                            SlackField("Timestamp", getCurrentTimeKST(), true),
                            SlackField("Action", "수동으로 재시도해주세요", true)
                        )
                    )
                )
            )

            sendToSlackWebhook(message)
            logger.info("⚠️ 오류 알림 발송 완료")
        } catch (e: Exception) {
            logger.error("❌ 오류 알림 발송 실패", e)
        }
    }

    /**
     * 스케줄러 상태 알림
     */
    fun notifySchedulerStatus(status: String, details: Map<String, Any>) {
        if (isDisabled()) return
        if (slackWebhookUrl.isBlank()) return

        try {
            val color = when (status) {
                "started" -> "#0099cc"
                "stopped" -> "#999999"
                else -> "#666666"
            }

            val message = SlackMessage(
                text = "🔄 스케줄러 상태 업데이트",
                attachments = listOf(
                    SlackAttachment(
                        color = color,
                        title = "Quartz 스케줄러 - $status",
                        text = "스케줄러 상태가 변경되었습니다.",
                        fields = listOf(
                            SlackField("Status", status.uppercase(), true),
                            SlackField("Jobs", details["scheduledJobCount"]?.toString() ?: "0", true),
                            SlackField("Triggers", details["activeTriggerCount"]?.toString() ?: "0", true),
                            SlackField("Timestamp", getCurrentTimeKST(), true)
                        )
                    )
                )
            )

            sendToSlackWebhook(message)
        } catch (e: Exception) {
            logger.error("❌ 상태 알림 발송 실패", e)
        }
    }

    /**
     * Slack API (chat.postMessage)로 메시지 전송
     */
    private fun sendToSlackApi(message: SlackApiMessage): SlackApiResponse? {
        return try {
            webClient.post()
                .uri("https://slack.com/api/chat.postMessage")
                .header("Authorization", "Bearer $slackBotToken")
                .header("Content-Type", "application/json")
                .bodyValue(message)
                .retrieve()
                .bodyToMono(SlackApiResponse::class.java)
                .block()
        } catch (e: Exception) {
            logger.error("❌ Slack API 호출 실패", e)
            null
        }
    }

    /**
     * 기술적 분석 요청 알림 (스레드 루트 메시지)
     *
     * @param requestId 요청 ID
     * @param startDate 분석 시작 날짜, null이면 자동 결정
     * @param endDate 분석 종료 날짜, null이면 오늘
     * @return Slack 스레드 타임스탬프 (답글용)
     */
    fun notifyTechnicalAnalysisRequest(requestId: String, startDate: String? = null, endDate: String? = null): String? {
        if (isDisabled()) return null
        val dateInfo = formatDateRange(startDate, endDate)

        if (slackBotToken.isBlank()) {
            logger.warn("⚠️ Slack Bot Token 없음 - Webhook으로 fallback")
            notifyViaWebhook("📊 기술적 분석 요청", requestId, "기술적 지표 분석", dateInfo)
            return null
        }

        try {
            val message = SlackApiMessage(
                channel = slackChannel,
                text = "📊 기술적 분석 요청",
                attachments = listOf(
                    SlackAttachment(
                        color = "#0099cc",
                        title = "기술적 지표 분석 시작",
                        text = "SMA, RSI, MACD 기술적 지표 분석이 요청되었습니다.",
                        fields = listOf(
                            SlackField("Request ID", requestId, true),
                            SlackField("Date Range", "📅 $dateInfo", true),
                            SlackField("Timestamp", getCurrentTimeKST(), true),
                            SlackField("Source", "Quartz Scheduler", true),
                            SlackField("Status", "🔄 Processing", true)
                        )
                    )
                )
            )

            val response = sendToSlackApi(message)
            val threadTs = response?.ts

            if (threadTs != null) {
                logger.info("✅ Slack 스레드 루트 생성: requestId=$requestId, threadTs=$threadTs")
            } else {
                logger.warn("⚠️ Slack 메시지 발송 성공하지만 threadTs 없음")
            }

            return threadTs
        } catch (e: Exception) {
            logger.error("❌ Slack API 알림 발송 실패", e)
            return null
        }
    }

    /**
     * 뉴스 감정 분석 요청 알림 (스레드 루트 메시지)
     *
     * @param requestId 요청 ID
     * @param startDate 분석 시작 날짜, null이면 자동 결정
     * @param endDate 분석 종료 날짜, null이면 오늘
     * @return Slack 스레드 타임스탬프 (답글용)
     */
    fun notifySentimentAnalysisRequest(requestId: String, startDate: String? = null, endDate: String? = null): String? {
        if (isDisabled()) return null
        val dateInfo = formatDateRange(startDate, endDate)

        if (slackBotToken.isBlank()) {
            logger.warn("⚠️ Slack Bot Token 없음 - Webhook으로 fallback")
            notifyViaWebhook("📰 뉴스 감정 분석 요청", requestId, "뉴스 감정 분석", dateInfo)
            return null
        }

        try {
            val message = SlackApiMessage(
                channel = slackChannel,
                text = "📰 뉴스 감정 분석 요청",
                attachments = listOf(
                    SlackAttachment(
                        color = "#ffa500",
                        title = "뉴스 감정 분석 시작",
                        text = "Alpha Vantage NEWS_SENTIMENT API를 통한 감정 분석이 요청되었습니다.",
                        fields = listOf(
                            SlackField("Request ID", requestId, true),
                            SlackField("Date Range", "📅 $dateInfo", true),
                            SlackField("Timestamp", getCurrentTimeKST(), true),
                            SlackField("Source", "Quartz Scheduler", true),
                            SlackField("Status", "🔄 Processing", true)
                        )
                    )
                )
            )

            val response = sendToSlackApi(message)
            val threadTs = response?.ts

            if (threadTs != null) {
                logger.info("✅ Slack 스레드 루트 생성: requestId=$requestId, threadTs=$threadTs")
            } else {
                logger.warn("⚠️ Slack 메시지 발송 성공하지만 threadTs 없음")
            }

            return threadTs
        } catch (e: Exception) {
            logger.error("❌ Slack API 알림 발송 실패", e)
            return null
        }
    }

    /**
     * 종목 추천 요청 알림 (스레드 루트 메시지)
     *
     * @param requestId 요청 ID
     * @param startDate 분석 시작 날짜, null이면 자동 결정
     * @param endDate 분석 종료 날짜, null이면 오늘
     * @return Slack 스레드 타임스탬프 (답글용)
     */
    fun notifyStockRecommendationRequest(requestId: String, startDate: String? = null, endDate: String? = null): String? {
        if (isDisabled()) return null
        val dateInfo = formatDateRange(startDate, endDate)

        if (slackBotToken.isBlank()) {
            logger.warn("⚠️ Slack Bot Token 없음 - Webhook으로 fallback")
            notifyViaWebhook("🏆 종목 추천 요청", requestId, "종목 추천 (Composite Score)", dateInfo)
            return null
        }

        try {
            val message = SlackApiMessage(
                channel = slackChannel,
                text = "🏆 종목 추천 요청",
                attachments = listOf(
                    SlackAttachment(
                        color = "#7b2d8e",
                        title = "종목 추천 (Composite Score) 시작",
                        text = "AI(30%) + Technical(40%) + Sentiment(30%) 기반 종목 추천이 요청되었습니다.",
                        fields = listOf(
                            SlackField("Request ID", requestId, true),
                            SlackField("Date Range", "📅 $dateInfo", true),
                            SlackField("Timestamp", getCurrentTimeKST(), true),
                            SlackField("Source", "Quartz Scheduler", true),
                            SlackField("Status", "🔄 Processing", true)
                        )
                    )
                )
            )

            val response = sendToSlackApi(message)
            val threadTs = response?.ts

            if (threadTs != null) {
                logger.info("✅ Slack 스레드 루트 생성: requestId=$requestId, threadTs=$threadTs")
            } else {
                logger.warn("⚠️ Slack 메시지 발송 성공하지만 threadTs 없음")
            }

            return threadTs
        } catch (e: Exception) {
            logger.error("❌ Slack API 알림 발송 실패", e)
            return null
        }
    }

    /**
     * 분석 오류 알림
     */
    fun notifyAnalysisError(requestId: String, analysisType: String, error: String) {
        if (isDisabled()) return
        if (slackWebhookUrl.isBlank()) return

        try {
            val message = SlackMessage(
                text = "⚠️ $analysisType 분석 오류",
                attachments = listOf(
                    SlackAttachment(
                        color = "#dc3545",
                        title = "$analysisType 분석 실패",
                        text = "분석 중 오류가 발생했습니다.",
                        fields = listOf(
                            SlackField("Request ID", requestId, true),
                            SlackField("Analysis Type", analysisType, true),
                            SlackField("Error", error, false),
                            SlackField("Timestamp", getCurrentTimeKST(), true)
                        )
                    )
                )
            )

            sendToSlackWebhook(message)
            logger.info("⚠️ 오류 알림 발송 완료")
        } catch (e: Exception) {
            logger.error("❌ 오류 알림 발송 실패", e)
        }
    }

    /**
     * Webhook 일반 알림 (fallback)
     */
    private fun notifyViaWebhook(title: String, requestId: String, description: String, dateInfo: String) {
        if (slackWebhookUrl.isBlank()) {
            logger.debug("Slack webhook URL not configured, skipping notification")
            return
        }

        try {
            val message = SlackMessage(
                text = title,
                attachments = listOf(
                    SlackAttachment(
                        color = "#0099cc",
                        title = description,
                        text = "분석 요청이 발행되었습니다.",
                        fields = listOf(
                            SlackField("Request ID", requestId, true),
                            SlackField("Date Range", "📅 $dateInfo", true),
                            SlackField("Timestamp", getCurrentTimeKST(), true),
                            SlackField("Source", "Quartz Scheduler", true),
                            SlackField("Status", "🔄 Processing", true)
                        )
                    )
                )
            )

            sendToSlackWebhook(message)
            logger.info("✅ Slack 알림 발송 완료 (Webhook): $requestId")
        } catch (e: Exception) {
            logger.error("❌ Slack Webhook 알림 발송 실패", e)
        }
    }

    private fun formatDateRange(startDate: String?, endDate: String?): String =
        DateRangeFormatter.format(startDate, endDate)

    /**
     * Vertex AI Job 시작 알림 (스레드 루트 메시지)
     * @return Slack 스레드 타임스탬프 (답글용)
     */
    fun notifyVertexAIJobStarted(requestId: String, jobName: String): String? {
        if (isDisabled()) return null
        if (slackBotToken.isBlank()) {
            logger.warn("⚠️ Slack Bot Token 없음 - Webhook으로 fallback")
            notifyVertexAIJobStartedViaWebhook(requestId, jobName)
            return null
        }

        try {
            val message = SlackApiMessage(
                channel = slackChannel,
                text = "🚀 Vertex AI 예측 작업 시작",
                attachments = listOf(
                    SlackAttachment(
                        color = "#0099cc",
                        title = "주가 예측 모델 실행",
                        text = "Vertex AI CustomJob이 시작되었습니다.",
                        fields = listOf(
                            SlackField("Request ID", requestId, true),
                            SlackField("Job Name", jobName, true),
                            SlackField("Timestamp", getCurrentTimeKST(), true),
                            SlackField("Status", "🔄 RUNNING", true)
                        )
                    )
                )
            )

            val response = sendToSlackApi(message)
            logger.info("📋 Slack API 응답: ok=${response?.ok}, ts=${response?.ts}, error=${response?.error}")

            if (response?.ok == true && response.ts != null) {
                logger.info("✅ Vertex AI Job 시작 알림 발송 완료: requestId=$requestId, threadTs=${response.ts}")
                return response.ts
            } else {
                logger.warn("⚠️ Slack Bot API 실패 (ok=${response?.ok}, error=${response?.error}) - Webhook으로 fallback")
                notifyVertexAIJobStartedViaWebhook(requestId, jobName)
                return null
            }
        } catch (e: Exception) {
            logger.error("❌ Vertex AI Job 시작 알림 발송 실패 - Webhook으로 fallback", e)
            notifyVertexAIJobStartedViaWebhook(requestId, jobName)
            return null
        }
    }

    /**
     * Vertex AI Job 시작 알림 (Webhook fallback)
     */
    private fun notifyVertexAIJobStartedViaWebhook(requestId: String, jobName: String) {
        if (slackWebhookUrl.isBlank()) {
            logger.debug("Slack webhook URL not configured, skipping notification")
            return
        }

        try {
            val message = SlackMessage(
                text = "🚀 Vertex AI 예측 작업 시작",
                attachments = listOf(
                    SlackAttachment(
                        color = "#0099cc",
                        title = "주가 예측 모델 실행",
                        text = "Vertex AI CustomJob이 시작되었습니다.",
                        fields = listOf(
                            SlackField("Request ID", requestId, true),
                            SlackField("Job Name", jobName, true),
                            SlackField("Timestamp", getCurrentTimeKST(), true),
                            SlackField("Status", "🔄 RUNNING", true)
                        )
                    )
                )
            )

            sendToSlackWebhook(message)
            logger.info("✅ Vertex AI Job 시작 알림 발송 완료 (Webhook): requestId=$requestId")
        } catch (e: Exception) {
            logger.error("❌ Vertex AI Job 시작 Webhook 알림 발송 실패", e)
        }
    }

    /**
     * Vertex AI Job 완료 알림 (스레드 답글)
     */
    fun notifyVertexAIJobCompleted(requestId: String, jobName: String, duration: String, status: String, threadTs: String?) {
        if (isDisabled()) return
        if (slackBotToken.isBlank()) {
            logger.warn("⚠️ Slack Bot Token 없음 - 알림 발송 불가")
            return
        }

        try {
            val message = SlackApiMessage(
                channel = slackChannel,
                text = "✅ Vertex AI 예측 작업 완료",
                attachments = listOf(
                    SlackAttachment(
                        color = "#36a64f",
                        title = "주가 예측 모델 실행 완료",
                        text = "Vertex AI CustomJob이 성공적으로 완료되었습니다.",
                        fields = listOf(
                            SlackField("Request ID", requestId, true),
                            SlackField("Job Name", jobName, true),
                            SlackField("Duration", duration, true),
                            SlackField("Status", "✅ $status", true),
                            SlackField("Completion Time", getCurrentTimeKST(), false)
                        )
                    )
                ),
                threadTs = threadTs
            )

            sendToSlackApi(message)
            logger.info("✅ Vertex AI Job 완료 알림 발송 완료: requestId=$requestId, threadTs=$threadTs")
        } catch (e: Exception) {
            logger.error("❌ Vertex AI Job 완료 알림 발송 실패", e)
        }
    }

    /**
     * Vertex AI Job 실패 알림 (Error Webhook으로 전송)
     */
    fun notifyVertexAIJobFailed(requestId: String, jobName: String, error: String, threadTs: String?) {
        if (isDisabled()) return
        // 1. Error Webhook으로 에러 알림 전송
        if (slackWebhookUrlError.isNotBlank()) {
            try {
                val message = SlackMessage(
                    text = "❌ Vertex AI 예측 작업 실패",
                    attachments = listOf(
                        SlackAttachment(
                            color = "#dc3545",
                            title = "주가 예측 모델 실행 실패",
                            text = "Vertex AI CustomJob 실행 중 오류가 발생했습니다.",
                            fields = listOf(
                                SlackField("Request ID", requestId, true),
                                SlackField("Job Name", jobName, true),
                                SlackField("Error", error, false),
                                SlackField("Timestamp", getCurrentTimeKST(), true),
                                SlackField("Action", "로그 확인 후 재시도 필요", false)
                            )
                        )
                    )
                )
                sendToSlackWebhook(message, slackWebhookUrlError)
                logger.info("❌ Vertex AI Job 실패 알림 발송 완료 (Error Webhook): requestId=$requestId")
            } catch (e: Exception) {
                logger.error("❌ Vertex AI Job 실패 알림 발송 실패 (Error Webhook)", e)
            }
        }

        // 2. 스레드가 있으면 스레드에도 답글 (기존 채널)
        if (slackBotToken.isNotBlank() && threadTs != null) {
            try {
                val message = SlackApiMessage(
                    channel = slackChannel,
                    text = "❌ Vertex AI 예측 작업 실패",
                    attachments = listOf(
                        SlackAttachment(
                            color = "#dc3545",
                            title = "주가 예측 모델 실행 실패",
                            text = "Vertex AI CustomJob 실행 중 오류가 발생했습니다.",
                            fields = listOf(
                                SlackField("Request ID", requestId, true),
                                SlackField("Job Name", jobName, true),
                                SlackField("Error", error, false),
                                SlackField("Timestamp", getCurrentTimeKST(), true),
                                SlackField("Action", "로그 확인 후 재시도 필요", false)
                            )
                        )
                    ),
                    threadTs = threadTs
                )
                sendToSlackApi(message)
                logger.info("❌ Vertex AI Job 실패 알림 발송 완료 (Thread): requestId=$requestId, threadTs=$threadTs")
            } catch (e: Exception) {
                logger.error("❌ Vertex AI Job 실패 알림 발송 실패 (Thread)", e)
            }
        }
    }

    /**
     * API 에러 알림 (GlobalExceptionHandler용)
     * Error Webhook으로 전송
     */
    fun notifyApiError(
        errorType: String,
        errorMessage: String,
        requestPath: String?,
        stackTrace: String?
    ) {
        if (isDisabled()) return
        if (slackWebhookUrlError.isBlank()) {
            logger.debug("Slack error webhook URL not configured, skipping error notification")
            return
        }

        try {
            val truncatedStackTrace = stackTrace?.take(500)?.let {
                if (stackTrace.length > 500) "$it..." else it
            }

            val message = SlackMessage(
                text = "🚨 API 에러 발생",
                attachments = listOf(
                    SlackAttachment(
                        color = "#dc3545",
                        title = "서버 에러 알림",
                        text = "API 호출 중 예기치 않은 에러가 발생했습니다.",
                        fields = listOfNotNull(
                            SlackField("Error Type", errorType, true),
                            SlackField("Timestamp", getCurrentTimeKST(), true),
                            requestPath?.let { SlackField("Request Path", it, false) },
                            SlackField("Error Message", errorMessage.take(200), false),
                            truncatedStackTrace?.let { SlackField("Stack Trace", "```$it```", false) }
                        )
                    )
                )
            )

            sendToSlackWebhook(message, slackWebhookUrlError)
            logger.info("🚨 API 에러 알림 발송 완료: $errorType")
        } catch (e: Exception) {
            logger.error("❌ API 에러 알림 발송 실패", e)
        }
    }

    /**
     * Slack Webhook으로 메시지 전송 (기본 Webhook)
     */
    private fun sendToSlackWebhook(message: SlackMessage) {
        sendToSlackWebhook(message, slackWebhookUrl)
    }

    /**
     * Slack Webhook으로 메시지 전송 (지정된 URL)
     */
    private fun sendToSlackWebhook(message: SlackMessage, webhookUrl: String) {
        webClient.post()
            .uri(webhookUrl)
            .bodyValue(message)
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
    }
}

/**
 * Slack API 메시지 데이터 클래스 (chat.postMessage)
 */
data class SlackApiMessage(
    val channel: String,
    val text: String,
    val attachments: List<SlackAttachment>,
    @JsonProperty("thread_ts")
    val threadTs: String? = null
)

/**
 * Slack API 응답 데이터 클래스
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SlackApiResponse(
    val ok: Boolean = false,
    val ts: String? = null,
    val error: String? = null
)

/**
 * Slack Webhook 메시지 데이터 클래스
 */
data class SlackMessage(
    val text: String,
    val attachments: List<SlackAttachment>? = null
)

/**
 * Slack 첨부 파일 데이터 클래스
 */
data class SlackAttachment(
    val color: String,
    val title: String,
    val text: String,
    val fields: List<SlackField>,
    val footer: String = "Quantiq Economic Data Scheduler",
    val ts: Long = System.currentTimeMillis() / 1000
)

/**
 * Slack 필드 데이터 클래스
 */
data class SlackField(
    val title: String,
    val value: String,
    val short: Boolean
)
