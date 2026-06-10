package com.quantjumpstock.core.domain.model.prediction

import com.quantjumpstock.core.domain.recommendation.model.RecommendationCriteria
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * scoring_spec.yaml ↔ Kotlin 상수 드리프트 가드 (ADR 0006 §2.8).
 *
 * SSoT 는 backend repo 루트의 scoring_spec.yaml 이다. Kotlin 의 grade fallback
 * (PredictionResult.determineGrade)과 RecommendationCriteria 상수는 그 복사본이므로,
 * yaml 변경 시 함께 갱신하지 않으면 본 테스트가 실패한다 — 과거 7.5 vs 7.4 /
 * 5.18 vs 2.0 드리프트(ADR 0006 결함 #6)의 재발 방지 장치.
 */
@DisplayName("scoring_spec.yaml ↔ Kotlin 정합 (드리프트 가드)")
class ScoringSpecParityTest {

    private val spec: Map<String, Any> by lazy {
        val path = findSpecFile()
        // "spec 미발견(환경 문제)" 과 "드리프트(실패)" 를 구분한다 (패널 권고).
        // core 모듈만 단독 체크아웃/COPY 된 빌드 컨텍스트에선 skip — backend repo CI 에선 항상 발견됨.
        Assumptions.assumeTrue(path != null, "scoring_spec.yaml 미발견 — parity 검증 skip (SCORING_SPEC_PATH 로 지정 가능)")
        Files.newInputStream(path!!).use { Yaml().load(it) }
    }

    /** SCORING_SPEC_PATH env 우선, 없으면 작업 디렉토리(core 모듈)에서 상위로 올라가며 탐색. */
    private fun findSpecFile(): Path? {
        System.getenv("SCORING_SPEC_PATH")?.let {
            val p = Paths.get(it)
            if (Files.exists(p)) return p
        }
        var dir: Path? = Paths.get("").toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve("scoring_spec.yaml")
            if (Files.exists(candidate)) return candidate
            dir = dir.parent
        }
        return null
    }

    /** yaml 경로 탐색 — 누락/타입 불일치 시 "드리프트"가 아닌 명확한 경로 오류로 실패 (리뷰 M3). */
    @Suppress("UNCHECKED_CAST")
    private fun section(vararg keys: String): Map<String, Any> {
        var cur: Any = spec
        for (k in keys) {
            cur = (cur as? Map<*, *>)?.get(k)
                ?: error("yaml 경로 [${keys.joinToString(".")}] 누락 또는 map 아님 (key=$k)")
        }
        return cur as? Map<String, Any>
            ?: error("yaml 경로 [${keys.joinToString(".")}] 의 값이 map 이 아님")
    }

    private fun decimalOf(section: Map<String, Any>, key: String): BigDecimal =
        BigDecimal((section[key] ?: error("$key missing")).toString())

    @Test
    fun `grade fallback 임계가 yaml grades 와 일치한다`() {
        val grades = section("grades")
        for (g in listOf("S", "A", "B", "C")) {
            @Suppress("UNCHECKED_CAST")
            val min = decimalOf(grades[g] as Map<String, Any>, "min")
            // 경계값은 해당 등급, 경계 직전(-0.01)은 하위 등급이어야 한다
            assertEquals(
                g, PredictionResult.determineGrade(min).name,
                "yaml grades.$g.min=$min 인데 determineGrade($min) 불일치 — fallback 갱신 필요"
            )
            val below = PredictionResult.determineGrade(min.subtract(BigDecimal("0.01"))).name
            assertNotNull(below)
            assert(below != g) { "determineGrade(${min}-0.01) 가 여전히 $g — 경계 드리프트" }
        }
    }

    @Test
    fun `MIN_RECOMMEND_SCORE 가 yaml recommendation_filter 와 일치한다`() {
        val filter = section("recommendation_filter")
        assertEquals(
            0, RecommendationCriteria.MIN_RECOMMEND_SCORE.compareTo(decimalOf(filter, "min_composite_score")),
            "RecommendationCriteria.MIN_RECOMMEND_SCORE ↔ yaml min_composite_score 드리프트"
        )
    }

    @Test
    fun `DEFAULT_MIN_CONFIDENCE × 100 이 MIN_RECOMMEND_SCORE 와 정합한다`() {
        val asScore = BigDecimal(RecommendationCriteria.DEFAULT_MIN_CONFIDENCE.toString())
            .multiply(RecommendationCriteria.MAX_SCORE)
        assertEquals(
            0, RecommendationCriteria.MIN_RECOMMEND_SCORE.compareTo(asScore),
            "DEFAULT_MIN_CONFIDENCE(${RecommendationCriteria.DEFAULT_MIN_CONFIDENCE})×100 ≠ MIN_RECOMMEND_SCORE"
        )
    }

    @Test
    fun `참고용 weight 상수가 yaml axes weight 와 일치한다`() {
        val axes = section("axes")
        @Suppress("UNCHECKED_CAST")
        fun w(axis: String) = decimalOf(axes[axis] as Map<String, Any>, "weight")
        assertEquals(0, RecommendationCriteria.WEIGHT_AI.compareTo(w("ai")), "ai weight 드리프트")
        assertEquals(0, RecommendationCriteria.WEIGHT_TECH.compareTo(w("technical")), "tech weight 드리프트")
        assertEquals(0, RecommendationCriteria.WEIGHT_SENTIMENT.compareTo(w("sentiment")), "sentiment weight 드리프트")
    }
}
