package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.Optional

@Repository
interface StrategyJpaRepository : JpaRepository<StrategyEntity, Long> {

    fun findByStatus(status: StrategyStatus): List<StrategyEntity>

    fun findByCategoryId(categoryId: Long): List<StrategyEntity>

    fun findByCategory_Code(categoryCode: String): List<StrategyEntity>

    fun findByOwnerId(ownerId: Long): List<StrategyEntity>

    fun findByOwnerIdAndStatus(ownerId: Long, status: StrategyStatus): List<StrategyEntity>

    @Query("""
        SELECT s FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE')
        ORDER BY s.subscriberCount DESC
    """)
    fun findPopularStrategies(pageable: Pageable): Page<StrategyEntity>

    @Query("""
        SELECT s FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE')
        ORDER BY s.averageRating DESC
    """)
    fun findTopRatedStrategies(pageable: Pageable): Page<StrategyEntity>

    @Query("""
        SELECT s FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE')
        ORDER BY s.createdAt DESC
    """)
    fun findNewestStrategies(pageable: Pageable): Page<StrategyEntity>

    // 카테고리별 공개 전략
    @Query("""
        SELECT s FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE') AND s.category.code = :categoryCode
        ORDER BY s.subscriberCount DESC
    """)
    fun findPublicByCategoryCode(categoryCode: String, pageable: Pageable): Page<StrategyEntity>

    // 무료 전략만
    @Query("""
        SELECT s FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE') AND s.isPremium = false
        ORDER BY s.subscriberCount DESC
    """)
    fun findFreeStrategies(pageable: Pageable): Page<StrategyEntity>

    // 프리미엄 전략만
    @Query("""
        SELECT s FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE') AND s.isPremium = true
        ORDER BY s.subscriberCount DESC
    """)
    fun findPremiumStrategies(pageable: Pageable): Page<StrategyEntity>

    // 검색
    @Query("""
        SELECT s FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE')
        AND (LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY s.subscriberCount DESC
    """)
    fun searchByKeyword(keyword: String, pageable: Pageable): Page<StrategyEntity>

    // 상세 조회 (백테스트 결과 포함)
    @Query("""
        SELECT s FROM StrategyEntity s
        LEFT JOIN FETCH s.backtestResults
        WHERE s.id = :id
    """)
    fun findByIdWithBacktestResults(id: Long): Optional<StrategyEntity>

    // 공개 전략 상세 조회 (백테스트 결과 포함)
    @Query("""
        SELECT s FROM StrategyEntity s
        LEFT JOIN FETCH s.backtestResults
        LEFT JOIN FETCH s.category
        WHERE s.id = :id AND s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE')
    """)
    fun findPublicByIdWithBacktestResults(id: Long): Optional<StrategyEntity>

    // 구독자 수 업데이트
    @Modifying
    @Query("UPDATE StrategyEntity s SET s.subscriberCount = s.subscriberCount + 1 WHERE s.id = :strategyId")
    fun incrementSubscriberCount(strategyId: Long): Int

    @Modifying
    @Query("UPDATE StrategyEntity s SET s.subscriberCount = s.subscriberCount - 1 WHERE s.id = :strategyId AND s.subscriberCount > 0")
    fun decrementSubscriberCount(strategyId: Long): Int

    @Modifying
    @Query("UPDATE StrategyEntity s SET s.averageRating = :rating WHERE s.id = :strategyId")
    fun updateAverageRating(strategyId: Long, rating: BigDecimal): Int

    // 통계
    @Query("SELECT COUNT(s) FROM StrategyEntity s WHERE s.owner.id = :ownerId")
    fun countByOwnerId(ownerId: Long): Long

    @Query("SELECT COUNT(s) FROM StrategyEntity s WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE')")
    fun countActivePublicStrategies(): Long

    @Query("SELECT COUNT(s) FROM StrategyEntity s WHERE s.status IN ('PUBLISHED', 'ACTIVE')")
    fun countActiveStrategies(): Long

    // === 관리자용 쿼리 ===

    // 상태별 페이징 조회
    fun findByStatus(status: StrategyStatus, pageable: Pageable): Page<StrategyEntity>

    // 카테고리별 페이징 조회
    fun findByCategory_Code(categoryCode: String, pageable: Pageable): Page<StrategyEntity>

    // 상태 + 카테고리 필터 페이징 조회
    @Query("""
        SELECT s FROM StrategyEntity s
        WHERE s.status = :status AND s.category.code = :categoryCode
    """)
    fun findByStatusAndCategoryCode(status: StrategyStatus, categoryCode: String, pageable: Pageable): Page<StrategyEntity>

    // 상태별 개수 조회
    fun countByStatus(status: StrategyStatus): Long

    // 총 구독자 수 합계
    @Query("SELECT COALESCE(SUM(s.subscriberCount), 0) FROM StrategyEntity s")
    fun sumSubscriberCount(): Long?

    // Marketplace: 필터링 및 정렬이 적용된 공개 전략 조회
    @EntityGraph(value = "Strategy.withBacktestResults", type = EntityGraph.EntityGraphType.LOAD)
    @Query("""
        SELECT DISTINCT s FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE')
        AND (:categoryCode IS NULL OR s.category.code = :categoryCode)
        AND (:minCagr IS NULL OR EXISTS (
            SELECT 1 FROM BacktestResultEntity br2
            WHERE br2.strategy = s AND br2.status = 'COMPLETED' AND br2.cagr >= :minCagr
        ))
        AND (:maxMdd IS NULL OR EXISTS (
            SELECT 1 FROM BacktestResultEntity br3
            WHERE br3.strategy = s AND br3.status = 'COMPLETED' AND br3.mdd <= :maxMdd
        ))
        ORDER BY s.subscriberCount DESC
    """,
    countQuery = """
        SELECT COUNT(DISTINCT s) FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE')
        AND (:categoryCode IS NULL OR s.category.code = :categoryCode)
        AND (:minCagr IS NULL OR EXISTS (
            SELECT 1 FROM BacktestResultEntity br
            WHERE br.strategy = s AND br.status = 'COMPLETED' AND br.cagr >= :minCagr
        ))
        AND (:maxMdd IS NULL OR EXISTS (
            SELECT 1 FROM BacktestResultEntity br
            WHERE br.strategy = s AND br.status = 'COMPLETED' AND br.mdd <= :maxMdd
        ))
    """)
    fun findMarketplaceStrategies(
        categoryCode: String?,
        minCagr: BigDecimal?,
        maxMdd: BigDecimal?,
        pageable: Pageable
    ): Page<StrategyEntity>

    // Marketplace: CAGR로 정렬된 전략 조회
    @EntityGraph(value = "Strategy.withBacktestResults", type = EntityGraph.EntityGraphType.LOAD)
    @Query("""
        SELECT DISTINCT s FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE')
        AND (:categoryCode IS NULL OR s.category.code = :categoryCode)
        AND EXISTS (
            SELECT 1 FROM BacktestResultEntity br2
            WHERE br2.strategy = s AND br2.status = 'COMPLETED'
            AND (:minCagr IS NULL OR br2.cagr >= :minCagr)
            AND (:maxMdd IS NULL OR br2.mdd <= :maxMdd)
        )
    """,
    countQuery = """
        SELECT COUNT(DISTINCT s) FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE')
        AND (:categoryCode IS NULL OR s.category.code = :categoryCode)
        AND EXISTS (
            SELECT 1 FROM BacktestResultEntity br
            WHERE br.strategy = s AND br.status = 'COMPLETED'
            AND (:minCagr IS NULL OR br.cagr >= :minCagr)
            AND (:maxMdd IS NULL OR br.mdd <= :maxMdd)
        )
    """)
    fun findMarketplaceStrategiesByCagr(
        categoryCode: String?,
        minCagr: BigDecimal?,
        maxMdd: BigDecimal?,
        pageable: Pageable
    ): Page<StrategyEntity>

    // Marketplace: Sharpe Ratio로 정렬된 전략 조회
    @EntityGraph(value = "Strategy.withBacktestResults", type = EntityGraph.EntityGraphType.LOAD)
    @Query("""
        SELECT DISTINCT s FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE')
        AND (:categoryCode IS NULL OR s.category.code = :categoryCode)
        AND EXISTS (
            SELECT 1 FROM BacktestResultEntity br2
            WHERE br2.strategy = s AND br2.status = 'COMPLETED'
            AND br2.sharpeRatio IS NOT NULL
            AND (:minCagr IS NULL OR br2.cagr >= :minCagr)
            AND (:maxMdd IS NULL OR br2.mdd <= :maxMdd)
        )
    """,
    countQuery = """
        SELECT COUNT(DISTINCT s) FROM StrategyEntity s
        WHERE s.isPublic = true AND s.status IN ('PUBLISHED', 'ACTIVE')
        AND (:categoryCode IS NULL OR s.category.code = :categoryCode)
        AND EXISTS (
            SELECT 1 FROM BacktestResultEntity br
            WHERE br.strategy = s AND br.status = 'COMPLETED'
            AND br.sharpeRatio IS NOT NULL
            AND (:minCagr IS NULL OR br.cagr >= :minCagr)
            AND (:maxMdd IS NULL OR br.mdd <= :maxMdd)
        )
    """)
    fun findMarketplaceStrategiesBySharpe(
        categoryCode: String?,
        minCagr: BigDecimal?,
        maxMdd: BigDecimal?,
        pageable: Pageable
    ): Page<StrategyEntity>
}
