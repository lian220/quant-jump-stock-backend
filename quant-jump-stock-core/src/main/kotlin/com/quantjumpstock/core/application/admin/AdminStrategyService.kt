package com.quantjumpstock.core.application.admin

import com.quantjumpstock.core.domain.model.strategy.Strategy
import com.quantjumpstock.core.domain.model.strategy.StrategyStatus
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자용 전략 관리 서비스
 * 백오피스에서 사용하는 전략 관리 기능을 제공합니다.
 */
@Service
@Transactional(readOnly = true)
class AdminStrategyService(
    private val strategyRepository: StrategyRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 전체 전략 목록 조회 (페이징)
     */
    fun getAllStrategies(
        page: Int,
        size: Int,
        status: StrategyStatus?,
        categoryCode: String?,
        sortBy: String = "createdAt",
        sortDirection: String = "desc"
    ): AdminStrategyListResponse {
        logger.info("관리자 전략 목록 조회: page=$page, size=$size, status=$status, categoryCode=$categoryCode")

        val sort = if (sortDirection.lowercase() == "asc") {
            Sort.by(Sort.Direction.ASC, sortBy)
        } else {
            Sort.by(Sort.Direction.DESC, sortBy)
        }
        val pageable = PageRequest.of(page, size, sort)

        val strategiesPage = when {
            status != null && categoryCode != null -> {
                strategyRepository.findByStatusAndCategoryCode(status, categoryCode, pageable)
            }
            status != null -> {
                strategyRepository.findByStatus(status, pageable)
            }
            categoryCode != null -> {
                strategyRepository.findByCategoryCode(categoryCode, pageable)
            }
            else -> {
                strategyRepository.findAll(pageable)
            }
        }

        val summaries = strategiesPage.content.map { it.toAdminSummary() }

        return AdminStrategyListResponse(
            strategies = summaries,
            total = strategiesPage.totalElements,
            page = page,
            size = size,
            totalPages = strategiesPage.totalPages
        )
    }

    /**
     * 전략 상태 변경
     */
    @Transactional
    fun changeStatus(
        strategyId: Long,
        request: ChangeStrategyStatusRequest
    ): ChangeStrategyStatusResponse {
        logger.info("전략 상태 변경: strategyId=$strategyId, newStatus=${request.status}")

        val strategy = strategyRepository.findById(strategyId)
            ?: throw IllegalArgumentException("전략을 찾을 수 없습니다: $strategyId")

        val previousStatus = strategy.status

        // 상태 전환 유효성 검사
        validateStatusTransition(previousStatus, request.status)

        // 상태 변경 (도메인 모델의 비즈니스 메서드 사용)
        val updatedStrategy = strategy.transitionTo(request.status)
        strategyRepository.save(updatedStrategy)

        logger.info("전략 상태 변경 완료: $strategyId, $previousStatus -> ${request.status}")

        return ChangeStrategyStatusResponse(
            success = true,
            strategyId = strategyId,
            previousStatus = previousStatus,
            newStatus = request.status,
            message = getStatusChangeMessage(request.status, request.reason)
        )
    }

    /**
     * 전략 통계 조회
     */
    fun getStats(): AdminStrategyStatsResponse {
        logger.info("관리자 전략 통계 조회")

        val total = strategyRepository.count()
        val pendingReview = strategyRepository.countByStatus(StrategyStatus.PENDING_REVIEW)
        val published = strategyRepository.countByStatus(StrategyStatus.PUBLISHED)
        val totalSubscribers = strategyRepository.sumSubscriberCount()

        return AdminStrategyStatsResponse(
            total = total,
            pendingReview = pendingReview,
            published = published,
            totalSubscribers = totalSubscribers
        )
    }

    /**
     * 상태 전환 유효성 검사
     */
    private fun validateStatusTransition(from: StrategyStatus, to: StrategyStatus) {
        if (!from.canTransitionTo(to)) {
            throw IllegalStateException("상태 전환이 허용되지 않습니다: $from -> $to")
        }
    }

    /**
     * 상태 변경 메시지 생성
     */
    private fun getStatusChangeMessage(status: StrategyStatus, reason: String?): String {
        return when (status) {
            StrategyStatus.APPROVED -> "전략이 승인되었습니다."
            StrategyStatus.PUBLISHED -> "전략이 발행되었습니다."
            StrategyStatus.REJECTED -> "전략이 반려되었습니다. 사유: ${reason ?: "미지정"}"
            StrategyStatus.ARCHIVED -> "전략이 보관 처리되었습니다."
            else -> "전략 상태가 변경되었습니다."
        }
    }

    /**
     * Strategy 도메인 모델을 AdminStrategySummary로 변환
     *
     * 참고: 도메인 모델에는 category 정보가 categoryId만 있으므로,
     * 상세 정보가 필요한 경우 별도 조회가 필요합니다.
     * 현재는 Admin용 요약 정보만 제공합니다.
     */
    private fun Strategy.toAdminSummary(): AdminStrategySummary {
        return AdminStrategySummary(
            id = this.id!!,
            name = this.name,
            description = this.description,
            categoryCode = this.categoryId.toString(),  // TODO: Category 조회로 개선
            categoryName = "Category ${this.categoryId}",  // TODO: Category 조회로 개선
            ownerId = this.ownerId,
            ownerName = null,  // TODO: User 조회로 개선
            ownerEmail = null,  // TODO: User 조회로 개선
            status = this.status,
            stockSelectionType = this.stockSelectionType,
            isPublic = this.isPublic,
            isPremium = this.isPremium,
            rebalanceFrequency = this.rebalanceFrequency,
            subscriberCount = this.subscriberCount,
            averageRating = this.averageRating,
            latestCagr = null,  // TODO: BacktestResult 조회로 개선
            latestMdd = null,   // TODO: BacktestResult 조회로 개선
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
