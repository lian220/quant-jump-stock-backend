package com.quantjumpstock.core.application.subscription

import com.quantjumpstock.core.application.tier.TierConfigurationService
import com.quantjumpstock.core.domain.model.backtest.UniverseType
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import com.quantjumpstock.core.domain.port.output.StrategySubscriptionRepository
import com.quantjumpstock.core.domain.port.output.SubscriptionDetail
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.domain.port.output.UserTierRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Phase 1C — 계좌-구독 1:1 제약 (ACCOUNT_ALREADY_MAPPED) 검증.
 *
 * 핵심 시나리오:
 *   - 자기 자신 구독에 같은 brokerAccountId 재매핑 → conflict 아님 (정상)
 *   - 다른 구독이 이미 점유 → 412 ACCOUNT_ALREADY_MAPPED + extra 에 conflict 정보
 *   - brokerAccountId=null 매핑(해제) → 충돌 검사 스킵
 */
class StrategySubscriptionServiceTest {

    private val subscriptionRepository = mockk<StrategySubscriptionRepository>()
    private val userRepository = mockk<UserRepository>()
    private val strategyRepository = mockk<StrategyRepository>(relaxed = true)
    private val tierConfigService = mockk<TierConfigurationService>(relaxed = true)
    private val userTierRepository = mockk<UserTierRepository>(relaxed = true)

    private val service = StrategySubscriptionService(
        subscriptionRepository,
        userRepository,
        strategyRepository,
        tierConfigService,
        userTierRepository,
    )

    private val userId = "user-1"
    private val userDbId = 100L
    private val subscriptionId = 10L
    private val brokerAccountId = 50L

    private fun stubUser() {
        every { userRepository.findByUserId(userId) } returns User(
            id = userDbId, userId = userId, email = "u@example.com"
        )
    }

    private fun stubOwnedSubscription() {
        every {
            subscriptionRepository.findByIdAndUserId(subscriptionId, userDbId)
        } returns SubscriptionDetail(
            subscriptionId = subscriptionId,
            strategyId = 1L,
            strategyName = "전략 A",
            strategyDescription = null,
            isPremiumStrategy = false,
            alertEnabled = true,
            preferredUniverseType = UniverseType.MARKET,
            subscribedAt = LocalDateTime.now(),
            brokerAccountId = null,
        )
    }

    @Test
    fun `다른 ACTIVE 구독이 같은 계좌 점유 시 412 ACCOUNT_ALREADY_MAPPED 발생`() {
        stubUser()
        stubOwnedSubscription()
        every {
            subscriptionRepository.findActiveSubscriptionByBrokerAccountId(brokerAccountId, subscriptionId)
        } returns SubscriptionDetail(
            subscriptionId = 99L,
            strategyId = 7L,
            strategyName = "전략 B",
            strategyDescription = null,
            isPremiumStrategy = false,
            alertEnabled = true,
            preferredUniverseType = UniverseType.MARKET,
            subscribedAt = LocalDateTime.now(),
            brokerAccountId = brokerAccountId,
        )

        assertThatThrownBy {
            service.updateBrokerAccount(userId, subscriptionId, brokerAccountId)
        }.isInstanceOfSatisfying(SubscriptionException::class.java) { e ->
            assertThat(e.errorCode).isEqualTo("ACCOUNT_ALREADY_MAPPED")
            assertThat(e.httpStatus).isEqualTo(412)
            assertThat(e.extra["conflictSubscriptionId"]).isEqualTo(99L)
            assertThat(e.extra["conflictStrategyId"]).isEqualTo(7L)
            assertThat(e.extra["conflictStrategyName"]).isEqualTo("전략 B")
        }

        verify(exactly = 0) { subscriptionRepository.updateBrokerAccountId(any(), any()) }
    }

    @Test
    fun `자기 자신 구독에 같은 계좌 재매핑은 conflict 가 아니다`() {
        stubUser()
        stubOwnedSubscription()
        every {
            subscriptionRepository.findActiveSubscriptionByBrokerAccountId(brokerAccountId, subscriptionId)
        } returns null
        every { subscriptionRepository.updateBrokerAccountId(subscriptionId, brokerAccountId) } returns true

        val result = service.updateBrokerAccount(userId, subscriptionId, brokerAccountId)

        assertThat(result.brokerAccountId).isEqualTo(brokerAccountId)
        verify { subscriptionRepository.updateBrokerAccountId(subscriptionId, brokerAccountId) }
    }

    @Test
    fun `brokerAccountId 가 null 이면 충돌 검사를 스킵하고 매핑 해제`() {
        stubUser()
        stubOwnedSubscription()
        every { subscriptionRepository.updateBrokerAccountId(subscriptionId, null) } returns true

        val result = service.updateBrokerAccount(userId, subscriptionId, null)

        assertThat(result.brokerAccountId).isNull()
        verify(exactly = 0) {
            subscriptionRepository.findActiveSubscriptionByBrokerAccountId(any(), any())
        }
        verify { subscriptionRepository.updateBrokerAccountId(subscriptionId, null) }
    }
}
