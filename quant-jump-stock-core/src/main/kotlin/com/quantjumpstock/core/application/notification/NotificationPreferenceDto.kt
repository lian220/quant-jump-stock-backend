package com.quantjumpstock.core.application.notification

import com.quantjumpstock.core.domain.notification.model.NotificationPreference
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "알림 설정 응답")
data class NotificationPreferenceDto(
    @Schema(description = "뉴스 알림") val newsEnabled: Boolean,
    @Schema(description = "공지사항 알림") val announcementEnabled: Boolean,
    @Schema(description = "매매 신호 알림") val tradingSignalEnabled: Boolean,
    @Schema(description = "백테스트 완료 알림") val backtestEnabled: Boolean,
    @Schema(description = "가격 알림") val priceAlertEnabled: Boolean,
    @Schema(description = "주간 리포트 알림") val weeklyDigestEnabled: Boolean,
    @Schema(description = "Web Push 알림") val pushEnabled: Boolean
) {
    companion object {
        fun from(p: NotificationPreference) = NotificationPreferenceDto(
            newsEnabled = p.newsEnabled,
            announcementEnabled = p.announcementEnabled,
            tradingSignalEnabled = p.tradingSignalEnabled,
            backtestEnabled = p.backtestEnabled,
            priceAlertEnabled = p.priceAlertEnabled,
            weeklyDigestEnabled = p.weeklyDigestEnabled,
            pushEnabled = p.pushEnabled
        )
    }
}
