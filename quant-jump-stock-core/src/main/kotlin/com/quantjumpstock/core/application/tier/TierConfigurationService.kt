package com.quantjumpstock.core.application.tier

import com.quantjumpstock.core.domain.model.tier.TierConfiguration
import com.quantjumpstock.core.domain.port.output.TierConfigurationRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class TierConfigurationService(
    private val tierConfigRepository: TierConfigurationRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // 구독 제한 체크
    fun checkSubscriptionLimit(tier: String, currentCount: Long): SubscriptionLimitResult {
        val config = getOrDefault(tier)
        val allowed = config.canSubscribeMore(currentCount)
        return SubscriptionLimitResult(
            allowed = allowed,
            currentCount = currentCount,
            maxCount = if (config.isUnlimitedSubscription) -1 else config.maxSubscriptionCount.toLong(),
            isUnlimited = config.isUnlimitedSubscription,
            message = if (!allowed)
                "구독 제한을 초과했습니다. (현재: ${currentCount}개, 최대: ${config.maxSubscriptionCount}개)"
            else null
        )
    }

    // 일일 백테스트 한도 조회
    fun getBacktestDailyLimit(tier: String): Int {
        val config = getOrDefault(tier)
        return if (config.isUnlimitedBacktest) Int.MAX_VALUE else config.maxBacktestDaily
    }

    // 전략당 백테스트 보관 한도 조회
    fun getBacktestPerStrategyLimit(tier: String): Long {
        val config = getOrDefault(tier)
        return if (config.isUnlimitedBacktest) Long.MAX_VALUE else config.maxBacktestPerStrategy.toLong()
    }

    // 전체 설정 목록
    @Cacheable(value = ["tierConfigurations"])
    fun getAllConfigurations(): List<TierConfigurationDto> {
        return tierConfigRepository.findAll().map { it.toDto() }
    }

    // 설정 변경 (어드민용)
    @Transactional
    @CacheEvict(value = ["tierConfigurations"], allEntries = true)
    fun updateConfiguration(tier: String, request: UpdateTierConfigRequest, updatedBy: String): TierConfigurationDto {
        val existing = tierConfigRepository.findByTier(tier)
            ?: throw IllegalArgumentException("티어 설정을 찾을 수 없습니다: $tier")

        val updated = existing.copy(
            maxSubscriptionCount = request.maxSubscriptionCount ?: existing.maxSubscriptionCount,
            maxBacktestDaily = request.maxBacktestDaily ?: existing.maxBacktestDaily,
            maxBacktestPerStrategy = request.maxBacktestPerStrategy ?: existing.maxBacktestPerStrategy,
            isUnlimitedSubscription = request.isUnlimitedSubscription ?: existing.isUnlimitedSubscription,
            isUnlimitedBacktest = request.isUnlimitedBacktest ?: existing.isUnlimitedBacktest,
            description = request.description ?: existing.description,
            updatedBy = updatedBy,
            updatedAt = LocalDateTime.now()
        )

        logger.info("티어 설정 변경: tier=$tier, updatedBy=$updatedBy, changes=$request")
        return tierConfigRepository.save(updated).toDto()
    }

    private fun getOrDefault(tier: String): TierConfiguration {
        return tierConfigRepository.findByTier(tier)
            ?: TierConfiguration(
                tier = tier,
                maxSubscriptionCount = 3,
                maxBacktestDaily = 3,
                maxBacktestPerStrategy = 5,
                isUnlimitedSubscription = tier != "FREE",
                isUnlimitedBacktest = tier != "FREE",
                description = null,
                updatedAt = null,
                updatedBy = null
            )
    }
}

data class SubscriptionLimitResult(
    val allowed: Boolean,
    val currentCount: Long,
    val maxCount: Long,
    val isUnlimited: Boolean,
    val message: String? = null
)

data class TierConfigurationDto(
    val tier: String,
    val maxSubscriptionCount: Int,
    val maxBacktestDaily: Int,
    val maxBacktestPerStrategy: Int,
    val isUnlimitedSubscription: Boolean,
    val isUnlimitedBacktest: Boolean,
    val description: String?,
    val updatedAt: LocalDateTime?,
    val updatedBy: String?
)

data class UpdateTierConfigRequest(
    val maxSubscriptionCount: Int? = null,
    val maxBacktestDaily: Int? = null,
    val maxBacktestPerStrategy: Int? = null,
    val isUnlimitedSubscription: Boolean? = null,
    val isUnlimitedBacktest: Boolean? = null,
    val description: String? = null
)

fun TierConfiguration.toDto() = TierConfigurationDto(
    tier = tier,
    maxSubscriptionCount = maxSubscriptionCount,
    maxBacktestDaily = maxBacktestDaily,
    maxBacktestPerStrategy = maxBacktestPerStrategy,
    isUnlimitedSubscription = isUnlimitedSubscription,
    isUnlimitedBacktest = isUnlimitedBacktest,
    description = description,
    updatedAt = updatedAt,
    updatedBy = updatedBy
)
