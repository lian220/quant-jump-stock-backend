"""
BuyCriteria - 매수 기준 설정 및 Composite Score 계산

모든 임계값/가중치는 RecommendationCriteriaSettings(config/settings.py)에서 관리.
BuyCriteria.from_settings()로 생성하면 환경변수(RECOMMENDATION_*)로 제어 가능.

  composite_score = weight_ai * ai_score + weight_technical * tech_score + weight_sentiment * sentiment_score
  tech_score = golden_cross_score * golden_cross + rsi_below_score * (rsi < rsi_threshold) + macd_buy_score * macd_buy
"""
import logging
from dataclasses import dataclass
from typing import TYPE_CHECKING, Dict, Any, List

if TYPE_CHECKING:
    from config.settings import RecommendationCriteriaSettings

logger = logging.getLogger(__name__)


@dataclass
class BuyCriteria:
    """매수 기준 설정 — RecommendationCriteriaSettings에서 값 주입, 직접 수정 금지"""

    # Composite score 가중치
    weight_ai: float = 0.3
    weight_technical: float = 0.4
    weight_sentiment: float = 0.3

    # 기술적 점수 개별 가중치
    golden_cross_score: float = 1.5
    rsi_below_score: float = 1.0
    macd_buy_score: float = 1.0
    rsi_threshold: float = 70.0

    # 필터 조건
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

    @staticmethod
    def _get_indicators(stock: Dict[str, Any]) -> Dict[str, Any]:
        """
        기술 지표를 추출 (nested/flat 포맷 모두 지원)

        nested: {"technical_indicators": {"golden_cross": True, ...}}
        flat:   {"golden_cross": True, "rsi": 28.0, ...}  (TechnicalResult dataclass)
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

    def calculate_composite_score(
        self,
        indicators: Dict[str, Any],
        ai_score: float = 0.0,
        sentiment_score: float = 0.0,
    ) -> Dict[str, Any]:
        """
        Composite Score 계산

        Returns:
            {tech_score, tech_signals, ai_score, sentiment_score, composite_score}
        """
        tech_score = self.calculate_tech_score(indicators)
        tech_signals = self.count_tech_signals(indicators)
        composite = (
            self.weight_ai * ai_score
            + self.weight_technical * tech_score
            + self.weight_sentiment * sentiment_score
        )
        return {
            "tech_score": tech_score,
            "tech_signals": tech_signals,
            "ai_score": ai_score,
            "sentiment_score": sentiment_score,
            "composite_score": round(composite, 4),
        }

    def passes_filter(self, tech_signals: int, sentiment_score: float = 0.0) -> bool:
        """
        필터 조건 통과 여부

        - 감정 >= 0.15 → 기술 2/3개 충족
        - 감정 < 0.15 또는 없음 → 기술 3/3개 충족
        """
        if sentiment_score >= self.min_sentiment_for_relaxed:
            return tech_signals >= self.min_tech_signals_with_sentiment
        return tech_signals >= self.min_tech_signals_without_sentiment

    def filter_candidates(
        self,
        all_results: List[Dict[str, Any]],
        ai_scores: Dict[str, float] | None = None,
        sentiment_scores: Dict[str, float] | None = None,
    ) -> List[Dict[str, Any]]:
        """
        전체 분석 결과에서 매수 후보 필터링 + 점수 계산

        Args:
            all_results: analyze_stocks()의 전체 종목 결과
            ai_scores: {ticker: score} AI 예측 점수 (미통합 시 None)
            sentiment_scores: {ticker: score} 감정 점수 (미통합 시 None)

        Returns:
            composite_score DESC 정렬된 상위 max_stocks_to_recommend개
        """
        if ai_scores is None:
            ai_scores = {}
        if sentiment_scores is None:
            sentiment_scores = {}

        # AI/감정 데이터 사용 여부 확인
        has_ai_data = bool(ai_scores and any(ai_scores.values()))
        has_sentiment_data = bool(sentiment_scores and any(sentiment_scores.values()))

        # AI 데이터 없을 때 composite threshold 동적 조정
        # AI(0.3) 없으면: 최대 composite = 0.4×3.5 + 0.3×~1.0 ≈ 1.5 → 2.0 달성 불가
        composite_threshold = self.min_composite_score
        if not has_ai_data:
            # AI(0.3) 없으면 최대 도달 가능 composite ~= 1.7 (Tech+Sentiment) 또는 1.4 (Tech only)
            # → min_composite_score(2.0) 달성 불가 → 1.0으로 하향
            composite_threshold = 1.0
            mode = "AI 없음+감정 있음" if has_sentiment_data else "Tech-only"
            logger.info(f"{mode} 모드: composite_threshold를 {composite_threshold}로 조정")

        candidates = []
        for stock in all_results:
            ticker = stock.get("ticker", "")
            indicators = self._get_indicators(stock)

            ai = ai_scores.get(ticker, 0.0)
            sentiment = sentiment_scores.get(ticker, 0.0)

            scores = self.calculate_composite_score(indicators, ai, sentiment)

            if not self.passes_filter(scores["tech_signals"], sentiment):
                continue
            if scores["composite_score"] < composite_threshold:
                continue

            candidates.append({**stock, "scores": scores})

        candidates.sort(key=lambda x: x["scores"]["composite_score"], reverse=True)

        top = candidates[: self.max_stocks_to_recommend]
        logger.info(
            f"매수 후보 필터링: {len(all_results)}개 중 {len(candidates)}개 통과, "
            f"상위 {len(top)}개 선정"
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

        Args:
            all_results: 전체 종목 분석 결과
            excluded_tickers: 이미 추천된 종목 (제외)
            ai_scores: AI 예측 점수
            sentiment_scores: 감정 분석 점수
            top_n: 반환할 near-miss 개수

        Returns:
            composite_score DESC 정렬된 near-miss 후보 (미충족 조건 포함)
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
            ai = ai_scores.get(ticker, 0.0)
            sentiment = sentiment_scores.get(ticker, 0.0)
            scores = self.calculate_composite_score(indicators, ai, sentiment)

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
                "missing_conditions": missing,
                "met_conditions": met,
            })

        near_miss.sort(key=lambda x: x["scores"]["composite_score"], reverse=True)
        return near_miss[:top_n]
