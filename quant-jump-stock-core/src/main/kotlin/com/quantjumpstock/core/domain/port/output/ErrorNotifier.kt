package com.quantjumpstock.core.domain.port.output

/**
 * 에러 알림 outbound 포트.
 *
 * application 레이어(예: OrderExecutionListener)나 adapter/input(예: GlobalExceptionHandler)이
 * 운영자 통지가 필요한 에러를 발생시킬 때 사용. 구체 구현(Slack/Pager/Email 등)은 adapter/output
 * 에서 결정한다.
 *
 * 도입 배경 (2026-05-15):
 *   기존에 application 레이어가 adapter.output.notification.slack.SlackApiClient 를 직접 import
 *   하던 헥사고날 위반을 제거. 포트 추상화로 ArchUnit 규칙 (application → adapter 금지) 충족.
 */
interface ErrorNotifier {
    /**
     * 에러 알림 전송.
     *
     * 구현체는 다음을 책임진다:
     *  - 알림 전송 실패를 호출 측으로 전파하지 않음 (try-catch 흡수)
     *  - 동일 (context, errorType) 폭주 방지 (rate-limit/dedup)
     *  - 민감정보(password/token/secret) 마스킹
     *
     * @param context 에러 발생 위치 식별자 (e.g. "REST POST /api/v1/foo", "OrderExecutionListener", "KIS BUY AAPL")
     * @param errorType 예외 클래스 단순명 또는 도메인 에러 카테고리
     * @param errorMessage 에러 메시지 본문 (구현체에서 길이 truncation/masking 가능)
     * @param stackTrace 스택트레이스 (선택). null 이면 표시 생략.
     */
    fun notify(
        context: String,
        errorType: String,
        errorMessage: String,
        stackTrace: String? = null,
    )
}
