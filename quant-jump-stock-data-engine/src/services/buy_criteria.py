"""
BuyCriteria - 매수 기준 설정 및 Composite Score 계산

banbu-stocktrading 매수 기준 객체화:
  composite_score = 0.3 * ai_score + 0.4 * tech_score + 0.3 * sentiment_score
  tech_score = 1.5 * golden_cross + 1.0 * (rsi < threshold) + 1.0 * macd_buy  (max 3.5)

현재 AI예측/감정분석 미통합 → ai_score=0, sentiment_score=0
"""
import logging
from dataclasses import dataclass, field
from typing import Dict, Any, List

logger = logging.getLogger(__name__)


# TODO: admin에서 BuyCriteria 설정값 DB 관리 (가중치, 임계값, max_stocks 등)
@dataclass
class BuyCriteria:
    """매수 기준 설정 (향후 admin에서 DB로 관리 예정)"""

    # Composite score 가중치
    weight_ai: float = 0.3
    weight_technical: float = 0.4
    weight_sentiment: float = 0.3

    # 기술적 점수 개별 가중치
    golden_cross_score: float = 1.5
    rsi_below_score: float = 1.0
    macd_buy_score: float = 1.0
    rsi_threshold: float = 50.0

    # 필터 조건
    min_composite_score: float = 2.0
    min_sentiment_for_relaxed: float = 0.15  # 감정 >= 0.15이면 기술 2개만 필요
    min_tech_signals_with_sentiment: int = 2
    min_tech_signals_without_sentiment: int = 3

    # 추천 설정
    max_stocks_to_recommend: int = 5

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
        ai_scores: Dict[str, float] = None,
        sentiment_scores: Dict[str, float] = None,
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
