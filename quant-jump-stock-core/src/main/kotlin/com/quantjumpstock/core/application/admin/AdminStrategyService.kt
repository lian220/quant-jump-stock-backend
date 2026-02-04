package com.quantjumpstock.core.application.admin

import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyStatus
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
    private val strategyJpaRepository: StrategyJpaRepository
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
                strategyJpaRepository.findByStatusAndCategoryCode(status, categoryCode, pageable)
            }
            status != null -> {
                strategyJpaRepository.findByStatus(status, pageable)
            }
            categoryCode != null -> {
                strategyJpaRepository.findByCategory_Code(categoryCode, pageable)
            }
            else -> {
                strategyJpaRepository.findAll(pageable)
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

        val strategy = strategyJpaRepository.findById(strategyId).orElseThrow {
            IllegalArgumentException("전략을 찾을 수 없습니다: $strategyId")
        }

        val previousStatus = strategy.status

        // 상태 전환 유효성 검사
        validateStatusTransition(previousStatus, request.status)

        // 상태 변경
        strategy.status = request.status

        // 발행 시 공개 설정
        if (request.status == StrategyStatus.PUBLISHED) {
            // isPublic은 val이므로 새 엔티티로 복사 필요할 수 있음
            // 현재 구조에서는 status만 변경
        }

        strategyJpaRepository.save(strategy)

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

        val total = strategyJpaRepository.count()
        val pendingReview = strategyJpaRepository.countByStatus(StrategyStatus.PENDING_REVIEW)
        val published = strategyJpaRepository.countByStatus(StrategyStatus.PUBLISHED)
        val totalSubscribers = strategyJpaRepository.sumSubscriberCount() ?: 0L

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
        val validTransitions = mapOf(
            StrategyStatus.DRAFT to listOf(StrategyStatus.PENDING_REVIEW, StrategyStatus.ARCHIVED),
            StrategyStatus.PENDING_REVIEW to listOf(StrategyStatus.APPROVED, StrategyStatus.REJECTED),
            StrategyStatus.APPROVED to listOf(StrategyStatus.PUBLISHED, StrategyStatus.REJECTED, StrategyStatus.ARCHIVED),
            StrategyStatus.PUBLISHED to listOf(StrategyStatus.ARCHIVED),
            StrategyStatus.REJECTED to listOf(StrategyStatus.DRAFT, StrategyStatus.PENDING_REVIEW),
            StrategyStatus.ACTIVE to listOf(StrategyStatus.ARCHIVED, StrategyStatus.PUBLISHED), // 레거시 호환
            StrategyStatus.ARCHIVED to listOf(StrategyStatus.DRAFT)
        )

        val allowed = validTransitions[from] ?: emptyList()
        if (to !in allowed) {
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
     * StrategyEntity를 AdminStrategySummary로 변환
     */
    private fun StrategyEntity.toAdminSummary(): AdminStrategySummary {
        val latestBacktest = this.backtestResults
            .filter { it.status == BacktestStatus.COMPLETED }
            .maxByOrNull { it.createdAt }

        return AdminStrategySummary(
            id = this.id!!,
            name = this.name,
            description = this.description,
            categoryCode = this.category.code,
            categoryName = this.category.name,
            ownerId = this.owner?.id,
            ownerName = this.owner?.name,
            ownerEmail = this.owner?.email,
            status = this.status,
            isPublic = this.isPublic,
            isPremium = this.isPremium,
            rebalanceFrequency = this.rebalanceFrequency,
            subscriberCount = this.subscriberCount,
            averageRating = this.averageRating,
            latestCagr = latestBacktest?.cagr,
            latestMdd = latestBacktest?.mdd,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
