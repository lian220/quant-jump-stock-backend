"""
ComprehensiveReportService - Quantiq 종합 분석 리포트

기술적 분석 결과에 AI 예측 + 감정 분석 데이터를 결합하여
Composite Score 기반 종합 리포트를 생성한다.
"""
import logging
from typing import Dict, Any, List, Optional
from datetime import datetime

from core.database import MongoDB
from services.buy_criteria import BuyCriteria

logger = logging.getLogger(__name__)


class ComprehensiveReportService:
    """기술적 분석 + AI 예측 + 감정 분석 종합 리포트"""

    def __init__(self, buy_criteria: Optional[BuyCriteria] = None):
        self.buy_criteria = buy_criteria or BuyCriteria()

    def load_sentiment_scores(self, analysis_date: str) -> Dict[str, float]:
        """
        MongoDB에서 감정 분석 점수 로드

        Returns:
            {ticker: average_sentiment_score}
        """
        db = MongoDB.get_db()
        docs = db.sentiment_analysis.find(
            {"date": analysis_date},
            {"_id": 0, "ticker": 1, "average_sentiment_score": 1},
        )
        scores = {}
        for doc in docs:
            ticker = doc.get("ticker")
            score = doc.get("average_sentiment_score", 0)
            if ticker and score is not None:
                scores[ticker] = float(score)

        logger.info(f"감정 분석 로드: {analysis_date} → {len(scores)}개 종목")
        return scores

    def load_ai_predictions(self, analysis_date: str) -> Dict[str, Dict[str, float]]:
        """
        MongoDB에서 AI 예측 데이터 로드.
        analysis_date의 종가 대비 predicted_price로 상승 확률 계산.

        Returns:
            {ticker: {"predicted_price": ..., "current_price": ..., "rise_probability": ...}}
        """
        db = MongoDB.get_db()

        # 1. 분석일의 종가 로드
        daily_doc = db.daily_stock_data.find_one({"date": analysis_date})
        current_prices = {}
        if daily_doc and daily_doc.get("stocks"):
            for ticker, val in daily_doc["stocks"].items():
                price = val if isinstance(val, (int, float)) else val.get("close_price")
                if price:
                    current_prices[ticker] = float(price)

        # 2. 최신 AI 예측 로드 (가장 최근 date 기준)
        latest_pred = db.stock_predictions.find_one(
            {}, {"_id": 0, "date": 1}, sort=[("date", -1)]
        )
        if not latest_pred:
            logger.info("AI 예측 데이터 없음")
            return {}

        pred_date = latest_pred["date"]
        preds = db.stock_predictions.find(
            {"date": pred_date},
            {"_id": 0, "ticker": 1, "predicted_price": 1, "actual_price": 1},
        )

        result = {}
        for doc in preds:
            ticker = doc.get("ticker")
            predicted = doc.get("predicted_price")
            if not ticker or not predicted:
                continue

            current = current_prices.get(ticker, doc.get("actual_price", 0))
            if current and current > 0:
                rise_prob = (predicted - current) / current * 100
            else:
                rise_prob = 0.0

            result[ticker] = {
                "predicted_price": float(predicted),
                "current_price": float(current) if current else 0.0,
                "rise_probability": round(rise_prob, 2),
            }

        logger.info(f"AI 예측 로드: pred_date={pred_date} → {len(result)}개 종목")
        return result

    def generate_report(
        self,
        technical_results: List[Dict[str, Any]],
        analysis_date: str,
    ) -> Dict[str, Any]:
        """
        Quantiq 종합 분석 리포트 생성

        Args:
            technical_results: analyze_stocks()의 전체 결과 (flat or nested)
            analysis_date: 분석 기준 날짜

        Returns:
            {
                "analysis_date": ...,
                "total_analyzed": ...,
                "sentiment_count": ...,
                "prediction_count": ...,
                "buy_candidates": [...],
                "summary": {...}
            }
        """
        # 1. 보조 데이터 로드
        sentiment_scores = self.load_sentiment_scores(analysis_date)
        ai_predictions = self.load_ai_predictions(analysis_date)

        # 2. Composite Score 계산 + 필터링
        ai_scores = {
            ticker: data["rise_probability"]
            for ticker, data in ai_predictions.items()
        }

        buy_candidates = self.buy_criteria.filter_candidates(
            technical_results,
            ai_scores=ai_scores,
            sentiment_scores=sentiment_scores,
        )

        # 3. 후보에 상세 데이터 추가
        for candidate in buy_candidates:
            ticker = candidate.get("ticker", "")
            # AI 예측 상세
            ai_data = ai_predictions.get(ticker, {})
            candidate["ai_prediction"] = {
                "rise_probability": ai_data.get("rise_probability", 0.0),
                "predicted_price": ai_data.get("predicted_price"),
                "current_price": ai_data.get("current_price"),
            }
            # 감정 점수
            candidate["sentiment_score"] = sentiment_scores.get(ticker, 0.0)

        # 4. 요약 통계
        if buy_candidates:
            avg_composite = sum(
                c["scores"]["composite_score"] for c in buy_candidates
            ) / len(buy_candidates)
            avg_rise = sum(
                c["ai_prediction"]["rise_probability"] for c in buy_candidates
            ) / len(buy_candidates)
        else:
            avg_composite = 0.0
            avg_rise = 0.0

        # 5. 분석별 추천 종목 분류
        tech_recommended = [
            c for c in buy_candidates if c["scores"]["tech_signals"] >= 2
        ]
        ai_recommended = [
            c for c in buy_candidates if c["ai_prediction"]["rise_probability"] > 0
        ]
        sentiment_recommended = [
            c for c in buy_candidates if c["sentiment_score"] >= 0.15
        ]

        report = {
            "analysis_date": analysis_date,
            "total_analyzed": len(technical_results),
            "sentiment_count": len(sentiment_scores),
            "prediction_count": len(ai_predictions),
            "candidate_count": len(buy_candidates),
            "buy_candidates": buy_candidates,
            "breakdown": {
                "technical": {
                    "count": len(tech_recommended),
                    "tickers": [c["ticker"] for c in tech_recommended[:5]],
                },
                "ai_prediction": {
                    "count": len(ai_recommended),
                    "tickers": [c["ticker"] for c in ai_recommended[:5]],
                    "avg_rise": round(avg_rise, 2),
                },
                "sentiment": {
                    "count": len(sentiment_recommended),
                    "tickers": [c["ticker"] for c in sentiment_recommended[:5]],
                },
            },
            "summary": {
                "avg_composite_score": round(avg_composite, 2),
                "avg_rise_probability": round(avg_rise, 2),
            },
        }

        logger.info(
            f"종합 리포트: {len(technical_results)}개 분석, "
            f"{len(buy_candidates)}개 추천, "
            f"avg_composite={avg_composite:.2f}"
        )
        return report
