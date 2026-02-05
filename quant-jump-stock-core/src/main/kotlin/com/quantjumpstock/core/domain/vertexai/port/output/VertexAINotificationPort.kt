package com.quantjumpstock.core.domain.vertexai.port.output

/**
 * Vertex AI 알림 Port (Output Port)
 *
 * Vertex AI Job 관련 알림 전송을 위한 추상 인터페이스.
 * Application Service에서 이 Port를 통해 알림 시스템에 접근.
 */
interface VertexAINotificationPort {

    /**
     * Vertex AI Job 시작 알림
     *
     * @param requestId 요청 ID
     * @param jobName Job 이름
     * @return 스레드 타임스탬프 (후속 알림용)
     */
    fun notifyJobStarted(requestId: String, jobName: String): String?

    /**
     * Vertex AI Job 완료 알림
     *
     * @param requestId 요청 ID
     * @param jobName Job 이름
     * @param duration 실행 시간
     * @param status 상태
     * @param threadTs 스레드 타임스탬프
     */
    fun notifyJobCompleted(
        requestId: String,
        jobName: String,
        duration: String,
        status: String,
        threadTs: String?
    )

    /**
     * Vertex AI Job 실패 알림
     *
     * @param requestId 요청 ID
     * @param jobName Job 이름
     * @param error 에러 메시지
     * @param threadTs 스레드 타임스탬프
     */
    fun notifyJobFailed(
        requestId: String,
        jobName: String,
        error: String,
        threadTs: String?
    )
}
