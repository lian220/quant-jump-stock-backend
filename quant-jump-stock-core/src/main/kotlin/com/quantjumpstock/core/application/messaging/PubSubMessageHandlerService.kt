package com.quantjumpstock.core.application.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.application.trading.AutoTradingService
import com.quantjumpstock.core.application.backtest.BacktestResultSaveService
import com.quantjumpstock.core.application.notification.NotificationService
import com.quantjumpstock.core.domain.notification.model.NotificationPriority
import com.quantjumpstock.core.domain.notification.model.NotificationType
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.events.EventTopics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Pub/Sub 메시지 핸들러 서비스
 * Push 엔드포인트(PubSubPushController)에서 호출합니다.
 */
@Service
class PubSubMessageHandlerService(
    private val objectMapper: ObjectMapper,
    private val autoTradingService: AutoTradingService,
    private val backtestResultSaveService: BacktestResultSaveService,
    private val notificationService: NotificationService,
    private val strategyRepository: StrategyRepository,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 토픽명으로 적절한 핸들러에 메시지를 라우팅합니다.
     * @param topic dot 표기법 토픽명 (예: quantiq.analysis.completed)
     * @param message JSON 메시지 본문
     */
    fun handleMessage(topic: String, message: String) {
        when (topic) {
            EventTopics.ANALYSIS_COMPLETED -> handleAnalysisCompleted(message)
            EventTopics.ECONOMIC_DATA_UPDATED -> handleEconomicDataUpdated(message)
            EventTopics.ECONOMIC_DATA_SYNC_FAILED -> handleEconomicDataSyncFailed(message)
            EventTopics.TRADING_SIGNAL_DETECTED -> handleTradingSignalDetected(message)
            EventTopics.BACKTEST_COMPLETED -> handleBacktestCompleted(message)
            EventTopics.BACKTEST_FAILED -> handleBacktestFailed(message)
            else -> logger.warn("알 수 없는 토픽: $topic")
        }
    }

    fun handleAnalysisCompleted(message: String) {
        logger.info("📥 분석 완료 이벤트 수신")
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val eventType = event.get("eventType")?.asText()
            val payload = event.get("payload")

            logger.info("Event Type: $eventType")

            logger.info("🤖 자동 매매 로직 실행 중...")
            autoTradingService.executeAutoTrading()

            try {
                val userId = payload?.get("userId")?.asText()?.let { resolveNotificationUserId(it, null) }
                if (userId != null) {
                    notificationService.create(
                        userId = userId,
                        type = NotificationType.AI_ANALYSIS_COMPLETE,
                        priority = NotificationPriority.NORMAL,
                        title = "AI 분석이 완료되었습니다",
                        actionUrl = "/recommendations"
                    )
                }
            } catch (e: Exception) {
                logger.warn("분석 완료 알림 생성 실패 (무시): ${e.message}")
            }

            logger.info("✅ 분석 완료 이벤트 처리 완료")
        } catch (e: Exception) {
            logger.error("❌ 분석 완료 이벤트 처리 실패: topic=${EventTopics.ANALYSIS_COMPLETED}, size=${message.length}B", e)
            throw e
        }
    }

    fun handleEconomicDataUpdated(message: String) {
        logger.info("📥 경제 데이터 업데이트 완료 이벤트 수신")
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
                ?: throw IllegalArgumentException("경제 데이터 업데이트 이벤트에 payload가 없습니다: $message")
            val requestId = payload.get("requestId")?.asText() ?: "unknown"
            val status = payload.get("status")?.asText() ?: "unknown"
            val duration = payload.get("duration")?.asDouble() ?: 0.0

            logger.info("✅ 경제 데이터 업데이트 완료 (requestId=$requestId, status=$status, duration=${duration}초)")
        } catch (e: Exception) {
            logger.error("❌ 경제 데이터 업데이트 이벤트 처리 실패: $message", e)
            throw e
        }
    }

    fun handleEconomicDataSyncFailed(message: String) {
        logger.warn("⚠️ 경제 데이터 동기화 실패 이벤트 수신")
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
                ?: throw IllegalArgumentException("경제 데이터 동기화 실패 이벤트에 payload가 없습니다: $message")
            val requestId = payload.get("requestId")?.asText() ?: "unknown"
            val errorCode = payload.get("errorCode")?.asText() ?: "UNKNOWN"
            val errorMessage = payload.get("errorMessage")?.asText() ?: "Unknown error"
            val retryable = payload.get("retryable")?.asBoolean() ?: false

            logger.warn("❌ 경제 데이터 동기화 실패 (requestId=$requestId, errorCode=$errorCode, error=$errorMessage, retryable=$retryable)")

            if (retryable) {
                logger.info("재시도 가능한 오류입니다. 재시도 로직 실행을 고려하세요.")
            }
        } catch (e: Exception) {
            logger.error("❌ 경제 데이터 동기화 실패 이벤트 처리 실패: $message", e)
            throw e
        }
    }

    fun handleTradingSignalDetected(message: String) {
        logger.info("🔔 매매 신호 감지 이벤트 수신")
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
                ?: throw IllegalArgumentException("매매 신호 이벤트에 payload가 없습니다: $message")
            val symbol = payload.get("symbol")?.asText() ?: "unknown"
            val signalType = payload.get("signalType")?.asText() ?: "unknown"
            val confidence = payload.get("confidence")?.asDouble() ?: 0.0

            logger.info("📊 매매 신호 (종목=$symbol, 신호=$signalType, 신뢰도=${confidence * 100}%)")

            try {
                val userId = payload.get("userId")?.asText()?.let { resolveNotificationUserId(it, null) }
                if (userId != null) {
                    val signalTypeKr = when (signalType.uppercase()) {
                        "BUY" -> "매수"
                        "SELL" -> "매도"
                        else -> "미확인($signalType)"
                    }
                    val confidencePct = (confidence * 100).toInt()
                    notificationService.create(
                        userId = userId,
                        type = NotificationType.TRADING_SIGNAL,
                        priority = NotificationPriority.CRITICAL,
                        title = "$symbol $signalTypeKr 시그널 (신뢰도 ${confidencePct}%)",
                        actionUrl = "/stocks/$symbol",
                        metadata = mapOf("symbol" to symbol, "signalType" to signalType, "confidence" to confidence)
                    )
                }
            } catch (e: Exception) {
                logger.warn("매매 시그널 알림 생성 실패 (무시): ${e.message}")
            }
        } catch (e: Exception) {
            logger.error("❌ 매매 신호 이벤트 처리 실패: topic=${EventTopics.TRADING_SIGNAL_DETECTED}, size=${message.length}B", e)
            throw e
        }
    }

    fun handleBacktestCompleted(message: String) {
        logger.info("📥 백테스트 완료 이벤트 수신")
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
                ?: throw IllegalArgumentException("백테스트 완료 이벤트에 payload가 없습니다: $message")

            val requestId = payload.get("requestId")?.asText() ?: "unknown"
            val strategyId = payload.get("strategyId")?.asLong() ?: 0L
            val status = payload.get("status")?.asText() ?: "unknown"

            logger.info("✅ 백테스트 완료 (requestId=$requestId, strategyId=$strategyId, status=$status)")

            backtestResultSaveService.saveBacktestResult(payload)

            logger.info("✅ 백테스트 결과 저장 완료")

            if (status == "success") {
                try {
                    val userId = resolveNotificationUserId(payload.get("userId")?.asText(), strategyId)
                    if (userId != null) {
                        val totalReturn = payload.get("totalReturn")?.asDouble()
                        val strategyName = strategyRepository.findById(strategyId)?.name ?: "전략"
                        val returnStr = totalReturn?.let { String.format(java.util.Locale.US, "%+.1f%%", it) } ?: ""
                        notificationService.create(
                            userId = userId,
                            type = NotificationType.BACKTEST_COMPLETE,
                            priority = NotificationPriority.HIGH,
                            title = "백테스트 완료! $strategyName $returnStr",
                            actionUrl = "/strategies/$strategyId/backtest",
                            metadata = mapOf(
                                "strategyId" to strategyId,
                                "strategyName" to strategyName,
                                "totalReturn" to (totalReturn ?: 0.0),
                                "status" to status
                            )
                        )
                    }
                } catch (e: Exception) {
                    logger.warn("백테스트 완료 알림 생성 실패 (무시): ${e.message}")
                }
            }
        } catch (e: Exception) {
            logger.error("❌ 백테스트 완료 이벤트 처리 실패: topic=${EventTopics.BACKTEST_COMPLETED}, size=${message.length}B", e)
            throw e
        }
    }

    fun handleBacktestFailed(message: String) {
        logger.warn("⚠️ 백테스트 실패 이벤트 수신")
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
                ?: throw IllegalArgumentException("백테스트 실패 이벤트에 payload가 없습니다: $message")

            val requestId = payload.get("requestId")?.asText() ?: "unknown"
            val strategyId = payload.get("strategyId")?.asLong() ?: 0L
            val errorMessage = payload.get("errorMessage")?.asText() ?: "Unknown error"

            logger.warn("❌ 백테스트 실패 (requestId=$requestId, strategyId=$strategyId, error=$errorMessage)")

            backtestResultSaveService.saveBacktestFailure(payload)

            logger.info("✅ 백테스트 실패 결과 저장 완료")
        } catch (e: Exception) {
            logger.error("❌ 백테스트 실패 이벤트 처리 실패: topic=${EventTopics.BACKTEST_FAILED}, size=${message.length}B", e)
            throw e
        }
    }

    private fun resolveNotificationUserId(userIdStr: String?, strategyId: Long?): Long? {
        if (!userIdStr.isNullOrBlank()) {
            userIdStr.toLongOrNull()?.let { return it }
            try {
                userRepository.findByUserId(userIdStr)?.id?.let { return it }
            } catch (e: Exception) {
                logger.warn("userId 조회 실패: $userIdStr", e)
            }
        }
        if (strategyId != null && strategyId > 0) {
            try {
                strategyRepository.findById(strategyId)?.ownerId?.let { return it }
            } catch (e: Exception) {
                logger.warn("전략 소유자 조회 실패: strategyId=$strategyId", e)
            }
        }
        return null
    }
}
