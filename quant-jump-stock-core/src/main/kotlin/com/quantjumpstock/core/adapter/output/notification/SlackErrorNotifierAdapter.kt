package com.quantjumpstock.core.adapter.output.notification

import com.quantjumpstock.core.adapter.output.notification.slack.SlackApiClient
import com.quantjumpstock.core.domain.port.output.ErrorNotifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [ErrorNotifier] 의 Slack 구현체. [SlackApiClient.notifyApiError] 에 위임하되,
 * 포트 계약상의 책임 — 폭주 방지(rate-limit), 민감정보 마스킹, 전송 실패 흡수 — 을 본 어댑터에서 처리.
 *
 * 도입 배경:
 *  - application 레이어가 SlackApiClient 를 직접 의존하지 않도록 포트/어댑터 분리
 *  - 알림 폭주 / 시크릿 누출 방어선을 한 곳(어댑터)에 집중
 */
@Component
class SlackErrorNotifierAdapter(
    private val slackApiClient: SlackApiClient,
) : ErrorNotifier {

    private val logger = LoggerFactory.getLogger(javaClass)

    // 동일 (context, errorType) 5분 쿨다운 — Slack 채널 폭주 방지
    private val lastNotifiedAt = ConcurrentHashMap<String, Long>()

    override fun notify(
        context: String,
        errorType: String,
        errorMessage: String,
        stackTrace: String?,
    ) {
        val key = "$context::$errorType"
        val now = System.currentTimeMillis()

        // 원자적 check-then-set — 동시 호출에서 첫 알림이 중복 발송되는 race 차단
        // compute() 는 키별 단일 락을 보장
        val accepted = AtomicBoolean(false)
        lastNotifiedAt.compute(key) { _, prev ->
            if (prev != null && (now - prev) < COOLDOWN_MS) {
                logger.info(
                    "⏳ 에러 알림 쿨다운 중 (스킵): key={} 남은={}s",
                    key, (COOLDOWN_MS - (now - prev)) / 1000,
                )
                prev
            } else {
                accepted.set(true)
                now
            }
        }
        if (!accepted.get()) return

        // 메모리 누수 방지: 만료 항목 정리 (O(n) 이지만 cap 도달 시에만)
        if (lastNotifiedAt.size > MAX_HISTORY_SIZE) {
            val cutoff = now - COOLDOWN_MS
            lastNotifiedAt.entries.removeIf { it.value < cutoff }
        }

        val safeMessage = maskSecrets(errorMessage).take(MESSAGE_MAX_LEN)
        val safeStackTrace = stackTrace?.let { maskSecrets(it).take(STACKTRACE_MAX_LEN) }

        try {
            slackApiClient.notifyApiError(
                errorType = errorType,
                errorMessage = safeMessage,
                requestPath = context,
                stackTrace = safeStackTrace,
            )
        } catch (e: Exception) {
            logger.warn("Slack 에러 알림 전송 실패 (흡수): context=$context", e)
        }
    }

    /**
     * 에러 메시지에 포함될 수 있는 시크릿 평문을 *** 로 치환.
     * 2026-05-14 docker compose config / MongoDB URI 노출 사고 후속 방어선.
     *
     * 커버 패턴:
     *  1. KEY=VALUE / KEY: VALUE (대소문자 무관)
     *  2. JSON  "key": "value"
     *  3. URI   scheme://user:password@host
     */
    private fun maskSecrets(text: String): String {
        var masked = SECRET_KV_PATTERN.replace(text) { m -> "${m.groupValues[1]}=***" }
        masked = SECRET_JSON_PATTERN.replace(masked) { m -> "\"${m.groupValues[1]}\":\"***\"" }
        masked = URI_CREDENTIAL_PATTERN.replace(masked) { m -> "${m.groupValues[1]}:***@" }
        masked = BEARER_TOKEN_PATTERN.replace(masked) { m -> "${m.groupValues[1]} ***" }
        return masked
    }

    companion object {
        private const val COOLDOWN_MS = 5 * 60 * 1000L
        private const val MAX_HISTORY_SIZE = 200
        private const val MESSAGE_MAX_LEN = 500
        private const val STACKTRACE_MAX_LEN = 1500

        // KEY=VALUE / KEY: VALUE 형태
        private val SECRET_KV_PATTERN = Regex(
            "(?i)\\b(password|passwd|secret|token|api[-_]?key|bearer|authorization|" +
                "mongodb_uri|db_password|gcp_credentials)\\s*[=:]\\s*\\S+"
        )
        // JSON {"password": "xxx"} 형태
        private val SECRET_JSON_PATTERN = Regex(
            "(?i)\"(password|passwd|secret|token|api[-_]?key|bearer|authorization|" +
                "mongodb_uri|db_password|gcp_credentials)\"\\s*:\\s*\"[^\"]*\""
        )
        // URI scheme://user:password@host 형태 (MongoDB Atlas, Postgres connection string 등)
        private val URI_CREDENTIAL_PATTERN = Regex(
            "([a-zA-Z][a-zA-Z0-9+\\-.]*://[^:/?#@\\s]+):[^@\\s]+@"
        )
        // "Bearer <token>" 형태 (HTTP Authorization 헤더)
        private val BEARER_TOKEN_PATTERN = Regex("(?i)\\b(Bearer)\\s+[A-Za-z0-9._\\-]+")
    }
}
