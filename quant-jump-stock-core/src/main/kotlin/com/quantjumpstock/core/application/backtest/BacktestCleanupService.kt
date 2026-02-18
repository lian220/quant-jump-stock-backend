package com.quantjumpstock.core.application.backtest

import com.quantjumpstock.core.domain.model.backtest.BacktestResult
import com.quantjumpstock.core.domain.model.backtest.BacktestStatus
import com.quantjumpstock.core.domain.model.backtest.BacktestType
import com.quantjumpstock.core.domain.port.output.BacktestResultRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * SCRUM-344: 백테스트 데이터 정리 서비스
 *
 * - RUNNING 24시간 이상: 자동 FAILED 처리
 * - USER_CUSTOM: 사용자당 전략당 10개 초과 시 오래된 것 아카이브
 * - CANONICAL: 전략당 최신 2개만 유지
 */
@Service
class BacktestCleanupService(
    private val backtestResultRepository: BacktestResultRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        const val MAX_USER_CUSTOM_PER_STRATEGY = 10
        const val MAX_CANONICAL_PER_STRATEGY = 2
        const val RUNNING_TIMEOUT_HOURS = 24L
    }

    /**
     * 전체 정리 작업 실행
     */
    @Transactional
    fun runCleanup() {
        logger.info("백테스트 데이터 정리 시작")

        val staleRunningCount = cleanupStaleRunning()
        logger.info("RUNNING 타임아웃 처리: {} 건", staleRunningCount)

        val userCustomCount = cleanupExcessUserCustom()
        logger.info("USER_CUSTOM 초과 정리: {} 건", userCustomCount)

        val canonicalCount = cleanupExcessCanonical()
        logger.info("CANONICAL 초과 정리: {} 건", canonicalCount)

        logger.info("백테스트 데이터 정리 완료")
    }

    /**
     * USER_CUSTOM: 사용자-전략 조합당 MAX_USER_CUSTOM_PER_STRATEGY 초과분 삭제 (오래된 순)
     */
    private fun cleanupExcessUserCustom(): Int {
        val allResults = backtestResultRepository.findAll(PageRequest.of(0, 5000))
        val userCustom = allResults.content.filter {
            it.backtestType == BacktestType.USER_CUSTOM && it.userId != null
        }

        var count = 0
        userCustom
            .groupBy { Pair(it.userId!!, it.strategyId) }
            .forEach { (_, results) ->
                if (results.size > MAX_USER_CUSTOM_PER_STRATEGY) {
                    val toDelete = results
                        .sortedByDescending { it.createdAt }
                        .drop(MAX_USER_CUSTOM_PER_STRATEGY)
                    toDelete.forEach { result ->
                        result.id?.let { id ->
                            backtestResultRepository.deleteById(id)
                            count++
                            logger.info("USER_CUSTOM 초과 삭제: id={}, userId={}, strategyId={}", id, result.userId, result.strategyId)
                        }
                    }
                }
            }
        return count
    }

    /**
     * CANONICAL: 전략당 MAX_CANONICAL_PER_STRATEGY 초과분 삭제 (오래된 순)
     */
    private fun cleanupExcessCanonical(): Int {
        val allResults = backtestResultRepository.findAll(PageRequest.of(0, 5000))
        val canonical = allResults.content.filter {
            it.backtestType == BacktestType.CANONICAL
        }

        var count = 0
        canonical
            .groupBy { it.strategyId }
            .forEach { (strategyId, results) ->
                if (results.size > MAX_CANONICAL_PER_STRATEGY) {
                    val toDelete = results
                        .sortedByDescending { it.createdAt }
                        .drop(MAX_CANONICAL_PER_STRATEGY)
                    toDelete.forEach { result ->
                        result.id?.let { id ->
                            backtestResultRepository.deleteById(id)
                            count++
                            logger.info("CANONICAL 초과 삭제: id={}, strategyId={}", id, strategyId)
                        }
                    }
                }
            }
        return count
    }

    /**
     * RUNNING 상태 24시간 초과 → FAILED 처리
     */
    private fun cleanupStaleRunning(): Int {
        val cutoff = LocalDateTime.now().minusHours(RUNNING_TIMEOUT_HOURS)
        // 전체 백테스트 중 RUNNING 상태인 것을 조회하여 타임아웃 처리
        val allResults = backtestResultRepository.findAll(PageRequest.of(0, 1000))
        var count = 0

        allResults.content
            .filter { it.status == BacktestStatus.RUNNING && it.createdAt.isBefore(cutoff) }
            .forEach { result ->
                val updated = result.copy(
                    status = BacktestStatus.FAILED,
                    errorMessage = "백테스트 실행 시간 초과 (${RUNNING_TIMEOUT_HOURS}시간)",
                    completedAt = LocalDateTime.now()
                )
                backtestResultRepository.save(updated)
                count++
                logger.info("RUNNING 타임아웃 처리: id={}, strategyId={}", result.id, result.strategyId)
            }

        return count
    }
}
