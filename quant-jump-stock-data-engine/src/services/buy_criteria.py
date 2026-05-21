"""
BuyCriteria - 매수 기준 설정 및 Composite Score 계산 (v2: 다층 추천 등급)

모든 임계값/가중치는 RecommendationCriteriaSettings(config/settings.py)에서 관리.
BuyCriteria.from_settings()로 생성하면 환경변수(RECOMMENDATION_*)로 제어 가능.

v2 변경사항:
- passes_filter 하드 게이트 제거 → 다층 추천 등급으로 대체
- 동적 가중치 재분배 (데이터 소스 부재 시 나머지에 가중치 분배)
- confidence(신뢰도) 기반 등급 결정

PR 1 (점수 모델 SSoT) 변경사항:
- 점수 산식은 ScoringPolicy public API 로 위임 (분모 7.4 통일)
- ScoreScale.CRITERIA_MAX_* 의존 제거 (composite_grade.py 의 deprecated 상수)
- calculate_composite_score 의 ai_score / sentiment_score 는 0~10 normalized 스케일
  (sync_service 와 동일). filter_candidates 는 레거시 caller 호환을 위해 내부에서
  0-3.5 / 0-1 입력을 0-10 로 rescale 후 호출. confidence 는 동일 (max 와 비례 확대됨).
"""
from __future__ import annotations
import logging
from dataclasses import dataclass, field
from decimal import Decimal
from typing import TYPE_CHECKING, Dict, Any, List, Optional

if TYPE_CHECKING:
    from config.settings import RecommendationCriteriaSettings

from domain.recommendation.composite_grade import RecommendationGrade
from domain.recommendation.scoring_policy import ScoringPolicy

logger = logging.getLogger(__name__)


@dataclass
class BuyCriteria:
    """매수 기준 설정 — RecommendationCriteriaSettings에서 값 주입"""

    # Composite score 가중치 (기본 - 3개 모두 가용 시)
    weight_ai: float = 0.3
    weight_technical: float = 0.4
    weight_sentiment: float = 0.3

    # 기술적 점수 개별 가중치
    golden_cross_score: float = 1.5
    rsi_below_score: float = 1.0
    macd_buy_score: float = 1.0
    rsi_threshold: float = 70.0

    # 레거시 필터 조건 (from_settings 호환용, 실제 필터에서 미사용)
    min_composite_score: float = 2.0
    min_sentiment_for_relaxed: float = 0.15
    min_tech_signals_with_sentiment: int = 2
    min_tech_signals_without_sentiment: int = 3

    # 추천 설정
    max_stocks_to_recommend: int = 5
    near_miss_top_n: int = 3

    # ScoringPolicy (PR 1: SSoT 위임)
    policy: Optional[ScoringPolicy] = field(default=None, repr=False, compare=False)

    def __post_init__(self) -> None:
        if self.policy is None:
            self.policy = ScoringPolicy.load_default()

    @classmethod
    def from_settings(
        cls,
        settings: "RecommendationCriteriaSettings",
        policy: Optional[ScoringPolicy] = None,
    ) -> "BuyCriteria":
        """RecommendationCriteriaSettings에서 BuyCriteria 생성 (환경변수로 제어)"""
        return cls(
            weight_ai=settings.weight_ai,
            weight_technical=settings.weight_technical,
            weight_sentiment=settings.weight_sentiment,
            golden_cross_score=settings.golden_cross_score,
            rsi_below_score=settings.rsi_below_score,
            macd_buy_score=settings.macd_buy_score,
            rsi_threshold=settings.rsi_threshold,
            min_composite_score=settings.min_composite_score,
            min_sentiment_for_relaxed=settings.min_sentiment_for_relaxed,
            min_tech_signals_with_sentiment=settings.min_tech_signals_with_sentiment,
            min_tech_signals_without_sentiment=settings.min_tech_signals_without_sentiment,
            max_stocks_to_recommend=settings.max_stocks_to_recommend,
            near_miss_top_n=settings.near_miss_top_n,
            policy=policy,
        )

    # ── 기술 지표 ──────────────────────────────────────

    @staticmethod
    def _get_indicators(stock: Dict[str, Any]) -> Dict[str, Any]:
        """
        기술 지표를 추출 (nested/flat 포맷 모두 지원)

        nested: {"technical_indicators": {"golden_cross": True, ...}}
        flat:   {"golden_cross": True, "rsi": 28.0, ...}
        """
        nested = stock.get("technical_indicators")
        if nested:
            return nested
        return {
            "golden_cross": stock.get("golden_cross", False),
            "rsi": stock.get("rsi", 100.0),
            "macd_buy_signal": stock.get("macd_buy_signal", False),
        }

    # ── Composite Score (ADR 0001 진정성 규칙) ──────────────

    # 정적 가중치 = 0.3 AI + 0.4 Tech + 0.3 Sent, max = 7.4 (고정)
    # 동적 가중치 재분배는 폐기 — 부분 점수를 100% 만점으로 둔갑시킨 결함의 원천.
    # 부재 축은 0으로 처리 → 점수 자연 하락 → grade 임계에 자동 컷오프.

    # ── Composite Score ──────────────────────────────────

    def calculate_composite_score(
        self,
        indicators: Dict[str, Any],
        ai_score: float = 0.0,        # 이미 normalized (0~10, sync_service 와 동일 스케일)
        sentiment_score: float = 0.0, # 이미 normalized (0~10)
        has_ai: bool = True,
        has_sentiment: bool = True,
        has_tech: bool = True,
    ) -> Dict[str, Any]:
        """
        Composite Score 계산 — ScoringPolicy public API 위임.

        분모 7.4 통일 (현행 buy_criteria 2.75 스케일 → sync_service 와 동일 7.4).
        부재 축은 0으로 처리 (재분배 없음, 자연히 낮은 점수).
        - 옛 _dynamic_weights 폐기: 부재 축의 가중치를 다른 축에 재분배해 "100% 강력 추천"
          같은 인플레 거짓을 만들던 결함의 원천.
        - missing_axes 정보는 보존 (UI/디버깅 용도).

        Args:
            indicators: 기술 지표 dict (golden_cross, rsi, macd_buy_signal)
            ai_score: 이미 0~10 normalized AI score
            sentiment_score: 이미 0~10 normalized sentiment score
            has_ai/has_sentiment/has_tech: 데이터 *부재* 여부 (sync_service와 동일 의미).
              True = 데이터 가용, False = 데이터 부재. tech_score가 0이어도 *분석은 정상 수행*
              한 경우는 has_tech=True (신호 없음 ≠ 데이터 부재).

        Returns:
            {tech_score, tech_signals, ai_score, sentiment_score,
             composite_score, max_possible, confidence, missing_axes}
        """
        policy = self.policy  # __post_init__ 보장
        assert policy is not None  # mypy/runtime safety

        tech_score = policy.tech_score_from_indicators(indicators) if has_tech else Decimal("0")
        tech_signals = policy.count_tech_signals(indicators) if has_tech else 0

        score = policy.compose_components(
            ai_score=Decimal(str(ai_score)) if has_ai else Decimal("0"),
            tech_score=tech_score,
            sentiment_score=Decimal(str(sentiment_score)) if has_sentiment else Decimal("0"),
            has_ai=has_ai,
            has_tech=has_tech,
            has_sentiment=has_sentiment,
            tech_signal_count=tech_signals,
        )

        return {
            "tech_score": float(score.tech_score),
            "tech_signals": tech_signals,
            "ai_score": float(score.ai_score),
            "sentiment_score": float(score.sentiment_score),
            "composite_score": round(float(score.composite_score), 4),
            "max_possible": round(float(score.composite_max), 4),
            "confidence": round(float(score.confidence), 4),
            "missing_axes": list(score.missing_axes),
        }

    # ── 등급 결정 ──────────────────────────────────────

    @staticmethod
    def determine_grade(scores: Dict[str, Any]) -> RecommendationGrade:
        """
        신뢰도(confidence) + 기술 신호 수 기반 추천 등급 결정.
        RecommendationGrade enum 반환 (domain/recommendation/composite_grade.py).
        """
        return RecommendationGrade.from_scores(scores)

    # ── 필터링 ──────────────────────────────────────

    def _rescale_legacy_inputs(self, ai_legacy: float, sentiment_legacy: float) -> tuple[float, float]:
        """레거시 caller (comprehensive_report) 가 0-3.5 / 0-1 스케일로 ai/sentiment 를 전달.
        본 함수는 SSoT 통일 분모(7.4)에 맞춰 0-10 스케일로 변환.
        confidence = composite / max 는 max 도 함께 확대되므로 동일 → grade 컷오프 보존.
        """
        # AI: legacy max 3.5 → unified max 10 (linear rescale)
        ai_normalized = (ai_legacy / 3.5) * 10.0 if ai_legacy else 0.0
        # Sentiment: legacy 0~1 → unified 0~10
        sent_normalized = sentiment_legacy * 10.0 if sentiment_legacy else 0.0
        return ai_normalized, sent_normalized

    def filter_candidates(
        self,
        all_results: List[Dict[str, Any]],
        ai_scores: Dict[str, float] | None = None,
        sentiment_scores: Dict[str, float] | None = None,
    ) -> List[Dict[str, Any]]:
        """
        전체 분석 결과에서 등급 기반 매수 후보 필터링.

        v3 (ADR 0001 진정성 규칙):
        - 정적 가중치 + 부재 축 0 처리 (calculate_composite_score)
        - composite_score 기반 grade 임계로 자연 컷오프
        - 부분 데이터 종목도 점수 그대로 산출. 점수 낮으면 grade 자동 NONE으로 떨어짐
        - max_stocks_to_recommend 개수 제한 (상위 N개)

        PR 1: ai_scores (0-3.5) / sentiment_scores (0-1) 레거시 스케일을
        0-10 로 변환하여 calculate_composite_score 호출. confidence 는 보존됨.
        """
        if ai_scores is None:
            ai_scores = {}
        if sentiment_scores is None:
            sentiment_scores = {}

        candidates = []
        for stock in all_results:
            ticker = stock.get("ticker", "")
            indicators = self._get_indicators(stock)

            # 데이터 부재 여부 — dict/indicators 존재 기준 (신호 0과 다름)
            has_ai = ticker in ai_scores
            has_sentiment = ticker in sentiment_scores
            has_tech = bool(indicators)  # 기술 분석이 수행됐는가 (신호 부재 ≠ 데이터 부재)

            ai_legacy = ai_scores.get(ticker, 0.0)
            sentiment_legacy = sentiment_scores.get(ticker, 0.0)
            ai_norm, sent_norm = self._rescale_legacy_inputs(ai_legacy, sentiment_legacy)

            scores = self.calculate_composite_score(
                indicators, ai_norm, sent_norm,
                has_ai=has_ai, has_sentiment=has_sentiment, has_tech=has_tech,
            )

            grade = self.determine_grade(scores)
            if grade == RecommendationGrade.NONE:
                continue

            candidates.append({**stock, "scores": scores, "grade": grade})

        # 등급 우선 → 같은 등급 내에서 composite_score 내림차순
        candidates.sort(
            key=lambda x: (x["grade"].priority, -x["scores"]["composite_score"])
        )

        top = candidates[: self.max_stocks_to_recommend]

        # 등급별 통계 로깅
        grade_counts = {}
        for c in candidates:
            g = c["grade"].label
            grade_counts[g] = grade_counts.get(g, 0) + 1

        grade_str = ", ".join(f"{g}={cnt}" for g, cnt in grade_counts.items())
        logger.info(
            f"매수 후보 필터링: {len(all_results)}개 중 {len(candidates)}개 통과 "
            f"({grade_str}), 상위 {len(top)}개 선정"
        )
        return top

    def get_near_miss_candidates(
        self,
        all_results: List[Dict[str, Any]],
        excluded_tickers: set,
        ai_scores: Dict[str, float] | None = None,
        sentiment_scores: Dict[str, float] | None = None,
        top_n: int | None = None,
    ) -> List[Dict[str, Any]]:
        """
        필터 미통과 종목 중 composite_score 상위 near-miss 후보 반환.
        """
        if ai_scores is None:
            ai_scores = {}
        if sentiment_scores is None:
            sentiment_scores = {}
        if top_n is None:
            top_n = self.near_miss_top_n

        near_miss = []
        for stock in all_results:
            ticker = stock.get("ticker", "")
            if ticker in excluded_tickers:
                continue

            indicators = self._get_indicators(stock)
            has_ai = ticker in ai_scores
            has_sentiment = ticker in sentiment_scores
            has_tech = bool(indicators)
            ai_legacy = ai_scores.get(ticker, 0.0)
            sentiment_legacy = sentiment_scores.get(ticker, 0.0)
            ai_norm, sent_norm = self._rescale_legacy_inputs(ai_legacy, sentiment_legacy)
            scores = self.calculate_composite_score(
                indicators, ai_norm, sent_norm,
                has_ai=has_ai, has_sentiment=has_sentiment, has_tech=has_tech,
            )
            grade = self.determine_grade(scores)

            # 미충족 조건 목록
            rsi_val = indicators.get("rsi", 100)
            missing = []
            if not indicators.get("golden_cross"):
                missing.append("골든크로스")
            if rsi_val >= self.rsi_threshold:
                missing.append(f"RSI({rsi_val:.0f}≥{self.rsi_threshold:.0f})")
            if not indicators.get("macd_buy_signal"):
                missing.append("MACD매수")

            # 충족 조건 목록
            met = []
            if indicators.get("golden_cross"):
                met.append("골든크로스✓")
            if rsi_val < self.rsi_threshold:
                met.append(f"RSI({rsi_val:.0f})✓")
            if indicators.get("macd_buy_signal"):
                met.append("MACD매수✓")

            near_miss.append({
                **stock,
                "scores": scores,
                "grade": grade,
                "missing_conditions": missing,
                "met_conditions": met,
            })

        near_miss.sort(key=lambda x: x["scores"]["composite_score"], reverse=True)
        return near_miss[:top_n]
