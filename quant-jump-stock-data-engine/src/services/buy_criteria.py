"""
BuyCriteria - 매수 기준 설정 및 Composite Score 계산 (v2: 다층 추천 등급)

모든 임계값/가중치는 RecommendationCriteriaSettings(config/settings.py)에서 관리.
BuyCriteria.from_settings()로 생성하면 환경변수(RECOMMENDATION_*)로 제어 가능.

v2 변경사항:
- passes_filter 하드 게이트 제거 → 다층 추천 등급으로 대체
- 동적 가중치 재분배 (데이터 소스 부재 시 나머지에 가중치 분배)
- confidence(신뢰도) 기반 등급 결정

  composite_score = w_ai * ai_score + w_tech * tech_score + w_sent * sentiment_score
  가중치는 가용 데이터에 따라 동적 재분배 (합계 항상 1.0)
"""
from __future__ import annotations
import logging
from dataclasses import dataclass
from typing import TYPE_CHECKING, Dict, Any, List

if TYPE_CHECKING:
    from config.settings import RecommendationCriteriaSettings

from domain.recommendation.composite_grade import RecommendationGrade, ScoreScale

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

    @classmethod
    def from_settings(cls, settings: "RecommendationCriteriaSettings") -> "BuyCriteria":
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

    def calculate_tech_score(self, indicators: Dict[str, Any]) -> float:
        """기술적 점수 계산 (max 3.5)"""
        score = 0.0
        if indicators.get("golden_cross"):
            score += self.golden_cross_score
        if indicators.get("rsi", 100) < self.rsi_threshold:
            score += self.rsi_below_score
        if indicators.get("macd_buy_signal"):
            score += self.macd_buy_score
        return score

    def count_tech_signals(self, indicators: Dict[str, Any]) -> int:
        """충족된 기술적 신호 개수 (max 3)"""
        count = 0
        if indicators.get("golden_cross"):
            count += 1
        if indicators.get("rsi", 100) < self.rsi_threshold:
            count += 1
        if indicators.get("macd_buy_signal"):
            count += 1
        return count

    # ── Composite Score (ADR 0001 진정성 규칙) ──────────────

    # 정적 가중치 = 0.3 AI + 0.4 Tech + 0.3 Sent, max = 7.4 (고정)
    # 동적 가중치 재분배는 폐기 — 부분 점수를 100% 만점으로 둔갑시킨 결함의 원천.
    # 부재 축은 0으로 처리 → 점수 자연 하락 → grade 임계에 자동 컷오프.

    # ── Composite Score ──────────────────────────────────

    def calculate_composite_score(
        self,
        indicators: Dict[str, Any],
        ai_score: float = 0.0,
        sentiment_score: float = 0.0,
        has_ai: bool = True,
        has_sentiment: bool = True,
        has_tech: bool = True,
    ) -> Dict[str, Any]:
        """
        Composite Score 계산 (ADR 0001 진정성 규칙)

        정적 가중치 0.3/0.4/0.3 적용, max = 7.4 고정.
        부재 축은 0으로 처리 (부분 점수도 그대로 산출, 자연히 낮은 점수).
        - 옛 _dynamic_weights 폐기: 부재 축의 가중치를 다른 축에 재분배해 "100% 강력 추천"
          같은 인플레 거짓을 만들던 결함의 원천.
        - missing_axes 정보는 보존 (UI/디버깅 용도).

        Args:
            has_ai/has_sentiment/has_tech: 데이터 *부재* 여부 (sync_service와 동일 의미).
              True = 데이터 가용, False = 데이터 부재. tech_score가 0이어도 *분석은 정상 수행*
              한 경우는 has_tech=True (신호 없음 ≠ 데이터 부재).

        Returns:
            {tech_score, tech_signals, ai_score, sentiment_score,
             composite_score, max_possible, confidence, missing_axes}
        """
        tech_score = self.calculate_tech_score(indicators)
        tech_signals = self.count_tech_signals(indicators)

        # 부재 축은 0으로 처리 (그 축의 가중치 × 0 = 0 기여)
        effective_ai = ai_score if has_ai else 0.0
        effective_tech = tech_score if has_tech else 0.0
        effective_sent = sentiment_score if has_sentiment else 0.0

        composite = (
            self.weight_ai * effective_ai
            + self.weight_technical * effective_tech
            + self.weight_sentiment * effective_sent
        )
        max_possible = (
            self.weight_ai * ScoreScale.CRITERIA_MAX_AI
            + self.weight_technical * ScoreScale.CRITERIA_MAX_TECH
            + self.weight_sentiment * ScoreScale.CRITERIA_MAX_SENTIMENT
        )
        confidence = composite / max_possible if max_possible > 0 else 0.0

        return {
            "tech_score": tech_score,
            "tech_signals": tech_signals,
            "ai_score": effective_ai,
            "sentiment_score": effective_sent,
            "composite_score": round(composite, 4),
            "max_possible": round(max_possible, 4),
            "confidence": round(confidence, 4),
            "missing_axes": [
                name for name, present in [
                    ("ai", has_ai), ("tech", has_tech), ("sentiment", has_sentiment)
                ] if not present
            ],
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

            ai = ai_scores.get(ticker, 0.0)
            sentiment = sentiment_scores.get(ticker, 0.0)

            scores = self.calculate_composite_score(
                indicators, ai, sentiment,
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
            ai = ai_scores.get(ticker, 0.0)
            sentiment = sentiment_scores.get(ticker, 0.0)
            scores = self.calculate_composite_score(
                indicators, ai, sentiment,
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
