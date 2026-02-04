package com.quantjumpstock.core.application.strategy

import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.domain.strategy.port.output.StrategyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 전략 CRUD 서비스
 * 사용자 전략 생성, 조회, 수정, 삭제 처리
 */
@Service
@Transactional(readOnly = true)
class StrategyService(
    private val strategyRepository: StrategyRepository,
    private val userRepository: UserJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 전략 생성
     */
    @Transactional
    fun createStrategy(userId: String, request: CreateStrategyRequest): StrategyResponse {
        logger.info("전략 생성 시작: userId=$userId, name=${request.name}")

        // 사용자 조회
        val user = userRepository.findByUserId(userId).orElseThrow {
            StrategyException("사용자를 찾을 수 없습니다: $userId")
        }

        // 전략 생성
        val strategy = StrategyEntity(
            name = request.name,
            description = request.description,
            category = request.category,
            owner = user,
            isPublic = request.isPublic,
            isPremium = request.isPremium,
            status = StrategyStatus.DRAFT,
            conditions = request.conditions,
            rebalanceFrequency = request.rebalanceFrequency
        )

        val saved = strategyRepository.save(strategy)
        logger.info("전략 생성 완료: id=${saved.id}, name=${saved.name}")

        return StrategyResponse(
            success = true,
            strategyId = saved.id,
            message = "전략이 생성되었습니다"
        )
    }

    /**
     * 전략 상세 조회
     */
    fun getStrategy(strategyId: Long, userId: String?): StrategyDetailResponse {
        logger.info("전략 조회: strategyId=$strategyId, userId=$userId")

        val strategy = strategyRepository.findByIdWithBacktestResults(strategyId).orElseThrow {
            StrategyException("전략을 찾을 수 없습니다: $strategyId")
        }

        // 비공개 전략은 소유자만 조회 가능
        if (!strategy.isPublic && strategy.owner?.userId != userId) {
            throw StrategyException("이 전략에 접근할 권한이 없습니다")
        }

        return strategy.toDetailResponse()
    }

    /**
     * 전략 수정
     */
    @Transactional
    fun updateStrategy(strategyId: Long, userId: String, request: UpdateStrategyRequest): StrategyResponse {
        logger.info("전략 수정 시작: strategyId=$strategyId, userId=$userId")

        val strategy = strategyRepository.findById(strategyId).orElseThrow {
            StrategyException("전략을 찾을 수 없습니다: $strategyId")
        }

        // 소유자 확인
        if (strategy.owner?.userId != userId) {
            throw StrategyException("이 전략을 수정할 권한이 없습니다")
        }

        // 필드 업데이트 (null이 아닌 값만)
        val updated = strategy.copy(
            name = request.name ?: strategy.name,
            description = request.description ?: strategy.description,
            category = request.category ?: strategy.category,
            isPublic = request.isPublic ?: strategy.isPublic,
            isPremium = request.isPremium ?: strategy.isPremium,
            status = request.status ?: strategy.status,
            conditions = request.conditions ?: strategy.conditions,
            rebalanceFrequency = request.rebalanceFrequency ?: strategy.rebalanceFrequency
        )

        strategyRepository.save(updated)
        logger.info("전략 수정 완료: id=$strategyId")

        return StrategyResponse(
            success = true,
            strategyId = strategyId,
            message = "전략이 수정되었습니다"
        )
    }

    /**
     * 전략 삭제
     */
    @Transactional
    fun deleteStrategy(strategyId: Long, userId: String): StrategyResponse {
        logger.info("전략 삭제 시작: strategyId=$strategyId, userId=$userId")

        val strategy = strategyRepository.findById(strategyId).orElseThrow {
            StrategyException("전략을 찾을 수 없습니다: $strategyId")
        }

        // 소유자 확인
        if (strategy.owner?.userId != userId) {
            throw StrategyException("이 전략을 삭제할 권한이 없습니다")
        }

        // 구독자가 있는 경우 삭제 불가
        if (strategy.subscriberCount > 0) {
            throw StrategyException("구독자가 있는 전략은 삭제할 수 없습니다. 먼저 비공개로 전환하세요.")
        }

        strategyRepository.delete(strategy)
        logger.info("전략 삭제 완료: id=$strategyId")

        return StrategyResponse(
            success = true,
            strategyId = strategyId,
            message = "전략이 삭제되었습니다"
        )
    }

    /**
     * 내 전략 목록 조회
     */
    fun getMyStrategies(userId: String): MyStrategiesResponse {
        logger.info("내 전략 목록 조회: userId=$userId")

        val user = userRepository.findByUserId(userId).orElseThrow {
            StrategyException("사용자를 찾을 수 없습니다: $userId")
        }

        val strategies = strategyRepository.findByOwnerId(user.id!!)

        val summaries = strategies.map { it.toSummary() }

        logger.info("내 전략 목록 조회 완료: userId=$userId, count=${summaries.size}")

        return MyStrategiesResponse(
            strategies = summaries,
            total = summaries.size
        )
    }

    /**
     * StrategyEntity를 StrategyDetailResponse로 변환
     */
    private fun StrategyEntity.toDetailResponse(): StrategyDetailResponse {
        val backtestSummaries = this.backtestResults
            .filter { it.status == BacktestStatus.COMPLETED }
            .sortedByDescending { it.createdAt }
            .map {
                BacktestResultSummary(
                    id = it.id!!,
                    cagr = it.cagr,
                    mdd = it.mdd,
                    sharpeRatio = it.sharpeRatio,
                    totalReturn = it.totalReturn,
                    status = it.status.name,
                    startDate = it.startDate.toString(),
                    endDate = it.endDate.toString()
                )
            }

        return StrategyDetailResponse(
            id = this.id!!,
            name = this.name,
            description = this.description,
            category = this.category,
            ownerId = this.owner?.id,
            ownerName = this.owner?.name,
            isPublic = this.isPublic,
            isPremium = this.isPremium,
            status = this.status,
            conditions = this.conditions,
            rebalanceFrequency = this.rebalanceFrequency,
            subscriberCount = this.subscriberCount,
            averageRating = this.averageRating,
            backtestResults = backtestSummaries,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    /**
     * StrategyEntity를 StrategySummary로 변환
     */
    private fun StrategyEntity.toSummary(): StrategySummary {
        val latestBacktest = this.backtestResults
            .filter { it.status == BacktestStatus.COMPLETED }
            .maxByOrNull { it.createdAt }

        return StrategySummary(
            id = this.id!!,
            name = this.name,
            category = this.category,
            status = this.status,
            isPublic = this.isPublic,
            isPremium = this.isPremium,
            subscriberCount = this.subscriberCount,
            averageRating = this.averageRating,
            latestCagr = latestBacktest?.cagr,
            latestMdd = latestBacktest?.mdd,
            createdAt = this.createdAt
        )
    }
}
