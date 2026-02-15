package com.quantjumpstock.core.application.strategy

import com.quantjumpstock.core.domain.model.strategy.Strategy
import com.quantjumpstock.core.domain.model.strategy.StockSelectionType
import com.quantjumpstock.core.domain.model.strategy.StrategyStatus
import com.quantjumpstock.core.domain.model.strategy.RebalanceFrequency
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import com.quantjumpstock.core.domain.port.output.StrategyCategoryRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 전략 CRUD 서비스 - Hexagonal Architecture 준수
 *
 * 특징:
 * - 도메인 포트(Repository)만 의존
 * - JPA Entity 직접 참조 없음
 * - 도메인 모델(Strategy)로 비즈니스 로직 처리
 */
@Service
@Transactional(readOnly = true)
class StrategyService(
    @Qualifier("strategyPersistenceAdapterV2")
    private val strategyRepository: StrategyRepository,
    private val userRepository: UserRepository,
    private val categoryRepository: StrategyCategoryRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 전략 생성
     */
    @Transactional
    fun createStrategy(userId: String, request: CreateStrategyRequest): StrategyResponse {
        logger.info("전략 생성 시작: userId=$userId, name=${request.name}")

        // 사용자 조회 (도메인 포트 사용)
        val user = userRepository.findByUserId(userId)
            ?: throw StrategyException("사용자를 찾을 수 없습니다: $userId")

        // 카테고리 조회 (도메인 포트 사용)
        val category = categoryRepository.findByCode(request.categoryCode)
            ?: throw StrategyException("유효하지 않은 카테고리입니다: ${request.categoryCode}")

        // 도메인 모델 생성
        val strategy = Strategy(
            name = request.name,
            description = request.description,
            categoryId = category.id ?: throw StrategyException("카테고리 ID가 없습니다"),
            ownerId = user.id,
            isPublic = request.isPublic,
            isPremium = request.isPremium,
            status = StrategyStatus.DRAFT,
            stockSelectionType = request.stockSelectionType,
            investmentPhilosophy = request.investmentPhilosophy,
            conditions = request.conditions,
            riskSettings = request.riskSettings,
            positionSizing = request.positionSizing,
            tradingCosts = request.tradingCosts,
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

        val strategy = strategyRepository.findByIdWithBacktestResults(strategyId)
            ?: throw StrategyException("전략을 찾을 수 없습니다: $strategyId")

        // 비공개 전략은 소유자만 조회 가능
        if (!strategy.isPublic) {
            val user = userId?.let { userRepository.findByUserId(it) }
            if (user == null || strategy.ownerId != user.id) {
                throw StrategyException("이 전략에 접근할 권한이 없습니다")
            }
        }

        return strategy.toDetailResponse()
    }

    /**
     * 전략 수정
     */
    @Transactional
    fun updateStrategy(strategyId: Long, userId: String, request: UpdateStrategyRequest): StrategyResponse {
        logger.info("전략 수정 시작: strategyId=$strategyId, userId=$userId")

        val strategy = strategyRepository.findById(strategyId)
            ?: throw StrategyException("전략을 찾을 수 없습니다: $strategyId")

        // 소유자 확인
        val user = userRepository.findByUserId(userId)
            ?: throw StrategyException("사용자를 찾을 수 없습니다: $userId")

        if (strategy.ownerId != user.id) {
            throw StrategyException("이 전략을 수정할 권한이 없습니다")
        }

        // 카테고리 변경 시 새 카테고리 조회
        val newCategoryId = request.categoryCode?.let { code ->
            val category = categoryRepository.findByCode(code)
                ?: throw StrategyException("유효하지 않은 카테고리입니다: $code")
            category.id ?: throw StrategyException("카테고리 ID가 없습니다")
        }

        // 필드 업데이트 (null이 아닌 값만)
        val updated = strategy.copy(
            name = request.name ?: strategy.name,
            description = request.description ?: strategy.description,
            categoryId = newCategoryId ?: strategy.categoryId,
            isPublic = request.isPublic ?: strategy.isPublic,
            isPremium = request.isPremium ?: strategy.isPremium,
            status = request.status ?: strategy.status,
            stockSelectionType = request.stockSelectionType ?: strategy.stockSelectionType,
            investmentPhilosophy = request.investmentPhilosophy ?: strategy.investmentPhilosophy,
            conditions = request.conditions ?: strategy.conditions,
            riskSettings = request.riskSettings ?: strategy.riskSettings,
            positionSizing = request.positionSizing ?: strategy.positionSizing,
            tradingCosts = request.tradingCosts ?: strategy.tradingCosts,
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

        val strategy = strategyRepository.findById(strategyId)
            ?: throw StrategyException("전략을 찾을 수 없습니다: $strategyId")

        // 소유자 확인
        val user = userRepository.findByUserId(userId)
            ?: throw StrategyException("사용자를 찾을 수 없습니다: $userId")

        if (strategy.ownerId != user.id) {
            throw StrategyException("이 전략을 삭제할 권한이 없습니다")
        }

        // 구독자가 있는 경우 삭제 불가
        if (strategy.subscriberCount > 0) {
            throw StrategyException("구독자가 있는 전략은 삭제할 수 없습니다. 먼저 비공개로 전환하세요.")
        }

        strategyRepository.deleteById(strategyId)
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

        val user = userRepository.findByUserId(userId)
            ?: throw StrategyException("사용자를 찾을 수 없습니다: $userId")

        val strategies = strategyRepository.findByOwnerId(user.id!!)
        val summaries = strategies.map { it.toSummary() }

        logger.info("내 전략 목록 조회 완료: userId=$userId, count=${summaries.size}")

        return MyStrategiesResponse(
            strategies = summaries,
            total = summaries.size
        )
    }

    /**
     * Strategy 도메인 모델을 StrategyDetailResponse로 변환
     */
    private fun Strategy.toDetailResponse(): StrategyDetailResponse {
        val category = categoryRepository.findById(this.categoryId)
        val owner = this.ownerId?.let { userRepository.findById(it) }

        return StrategyDetailResponse(
            id = this.id!!,
            name = this.name,
            description = this.description,
            category = CategoryInfo(
                id = category?.id ?: 0,
                code = category?.code ?: "",
                name = category?.name ?: ""
            ),
            ownerId = owner?.id,
            ownerName = owner?.name,
            isPublic = this.isPublic,
            isPremium = this.isPremium,
            status = this.status,
            stockSelectionType = this.stockSelectionType,
            investmentPhilosophy = this.investmentPhilosophy,
            conditions = this.conditions,
            riskSettings = this.riskSettings,
            positionSizing = this.positionSizing,
            tradingCosts = this.tradingCosts,
            rebalanceFrequency = this.rebalanceFrequency,
            subscriberCount = this.subscriberCount,
            averageRating = this.averageRating,
            backtestResults = emptyList(), // 백테스트 결과는 별도 조회 필요
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    /**
     * Strategy 도메인 모델을 StrategySummary로 변환
     */
    private fun Strategy.toSummary(): StrategySummary {
        val category = categoryRepository.findById(this.categoryId)

        return StrategySummary(
            id = this.id!!,
            name = this.name,
            category = CategoryInfo(
                id = category?.id ?: 0,
                code = category?.code ?: "",
                name = category?.name ?: ""
            ),
            status = this.status,
            stockSelectionType = this.stockSelectionType,
            isPublic = this.isPublic,
            isPremium = this.isPremium,
            subscriberCount = this.subscriberCount,
            averageRating = this.averageRating,
            latestCagr = null, // 백테스트 결과는 별도 조회
            latestMdd = null,
            createdAt = this.createdAt
        )
    }
}
