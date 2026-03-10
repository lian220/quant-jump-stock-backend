package com.quantjumpstock.core.application.dashboard

import com.quantjumpstock.core.application.notification.NotificationService
import com.quantjumpstock.core.application.tier.TierConfigurationService
import com.quantjumpstock.core.domain.port.output.StockPriceDataPort
import com.quantjumpstock.core.domain.port.output.StrategySubscriptionRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.domain.port.output.UserTierRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

@Service
@Transactional(readOnly = true)
class DashboardService(
    private val userRepository: UserRepository,
    private val userTierRepository: UserTierRepository,
    private val subscriptionRepository: StrategySubscriptionRepository,
    private val tierConfigService: TierConfigurationService,
    private val notificationService: NotificationService,
    private val stockPriceDataPort: StockPriceDataPort
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 대시보드에 표시할 주요 시장 지수 */
    companion object {
        val DASHBOARD_INDICES = listOf("^GSPC", "^NDX", "^VIX", "^KS11")
    }

    fun getDashboard(userId: String, userDbId: Long): DashboardResponse {
        // 1) 사용자 정보
        val user = userRepository.findByUserId(userId)
        val tierInfo = userTierRepository.checkBacktestLimit(userId)

        val userDto = DashboardUserDto(
            nickname = user?.name,
            tier = tierInfo.tier,
            joinDate = user?.createdAt?.format(dateFormatter) ?: ""
        )

        // 2) 구독 정보
        val subscriptionDetails = subscriptionRepository.findDetailsByUserId(userDbId)
        val currentCount = subscriptionDetails.size
        val limitResult = tierConfigService.checkSubscriptionLimit(tierInfo.tier, currentCount.toLong())

        val subscriptionsDto = DashboardSubscriptionsDto(
            count = currentCount,
            maxCount = limitResult.maxCount,
            strategies = subscriptionDetails.map { detail ->
                DashboardStrategyDto(
                    id = detail.strategyId,
                    name = detail.strategyName,
                    description = detail.strategyDescription
                )
            }
        )

        // 3) 알림/신호 정보
        val signalsDto = buildSignalsDto(userDbId)

        // 4) 시장 지표
        val marketDto = buildMarketDto()

        // 5) AI 사용량 (백테스트 일일 한도)
        val aiUsageDto = buildAiUsageDto(tierInfo.tier, tierInfo.remaining, tierInfo.dailyLimit)

        return DashboardResponse(
            user = userDto,
            subscriptions = subscriptionsDto,
            signals = signalsDto,
            market = marketDto,
            aiUsage = aiUsageDto
        )
    }

    private fun buildSignalsDto(userDbId: Long): DashboardSignalsDto {
        val unreadCount = notificationService.getUnreadCount(userDbId)
        val todayCount = notificationService.countTodayNotifications(userDbId)
        val recentNotifications = notificationService.getUserNotifications(userDbId, 3)

        return DashboardSignalsDto(
            unreadCount = unreadCount,
            todayCount = todayCount,
            recent = recentNotifications.mapNotNull { n ->
                val id = n.id ?: return@mapNotNull null
                DashboardNotificationDto.from(
                    id = id,
                    type = n.type.name,
                    title = n.title,
                    createdAt = n.createdAt
                )
            }
        )
    }

    private fun buildAiUsageDto(tier: String, remaining: Int, dailyLimit: Int): DashboardAiUsageDto {
        val used = if (dailyLimit <= 0) 0 else dailyLimit - remaining
        return DashboardAiUsageDto(
            backtestUsed = used.coerceAtLeast(0),
            backtestLimit = dailyLimit,
            backtestRemaining = remaining
        )
    }

    private fun buildMarketDto(): DashboardMarketDto {
        return try {
            val indices = stockPriceDataPort.getLatestMarketIndices(DASHBOARD_INDICES)
            DashboardMarketDto(
                indices = DASHBOARD_INDICES.mapNotNull { ticker ->
                    indices[ticker]?.let { snapshot ->
                        DashboardIndexDto(
                            symbol = snapshot.ticker,
                            name = snapshot.name,
                            price = snapshot.price,
                            changePercent = snapshot.changePercent
                        )
                    }
                }
            )
        } catch (e: Exception) {
            logger.warn("시장 지표 조회 실패: {}", e.message)
            DashboardMarketDto(indices = emptyList())
        }
    }
}
