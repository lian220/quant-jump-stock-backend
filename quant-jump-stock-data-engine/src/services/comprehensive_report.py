"""
ComprehensiveReportService - Quantiq 종합 분석 리포트

기술적 분석 결과에 AI 예측 + 감정 분석 데이터를 결합하여
Composite Score 기반 종합 리포트를 생성한다.
"""
import logging
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone, timedelta

from core.database import MongoDB
from config.settings import get_settings
from services.buy_criteria import BuyCriteria

logger = logging.getLogger(__name__)

# Slack 리포트에 표시되는 AI 예측 데이터의 최대 허용 지연일 (14일 예측 horizon의 절반)
_MAX_PREDICTION_LOOKBACK_DAYS = 7


class DailyDataNotCollectedError(Exception):
    """분석 날짜에 해당하는 daily_stock_data가 수집되지 않았을 때 발생"""

    def __init__(self, analysis_date: str):
        self.analysis_date = analysis_date
        super().__init__(
            f"daily_stock_data 없음 (date={analysis_date}). "
            f"경제 데이터 수집이 선행되어야 합니다."
        )


def _query_stock_predictions_by_date(db, analysis_date: str, target_date: datetime):
    """
    stock_predictions 컬렉션에서 해당 분석일 문서 조회.

    2026-05-20: date 는 NYSE 거래일 string 통일. 단순 매칭.
    ADR 0001 진정성 규칙: 정확한 날짜만 매칭. fallback 없음.
    """
    projection = {
        "_id": 0, "ticker": 1,
        "predicted_price": 1, "actual_price": 1, "rise_probability": 1,
    }

    preds = list(db.stock_predictions.find({"date": analysis_date}, projection))
    if preds:
        return preds

    logger.warning(
        f"stock_predictions 데이터 없음 (date={analysis_date}). "
        f"fallback 비활성화 — AI 데이터 누락으로 처리."
    )
    return []


class ComprehensiveReportService:
    """기술적 분석 + AI 예측 + 감정 분석 종합 리포트"""

    def __init__(self, buy_criteria: Optional[BuyCriteria] = None):
        if buy_criteria is None:
            settings = get_settings()
            buy_criteria = BuyCriteria.from_settings(settings.recommendation)
        self.buy_criteria = buy_criteria

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

        logger.debug(f"감정 분석 로드: {analysis_date} → {len(scores)}개 종목")
        return scores

    def load_ai_predictions(self, analysis_date) -> Dict[str, Dict[str, float]]:
        """
        MongoDB에서 AI 예측 데이터 로드 (정확한 날짜 매칭).

        stock_analysis_results (우선) + stock_predictions (보충) 둘 다 조회.

        Args:
            analysis_date: 분석 날짜 (str "YYYY-MM-DD" 또는 datetime)

        Returns:
            {ticker: {"predicted_price": ..., "current_price": ..., "rise_probability": ...}}
        """
        db = MongoDB.get_db()

        # 날짜 정규화를 모든 DB 쿼리 전에 수행
        if isinstance(analysis_date, datetime):
            target_date = analysis_date
            analysis_date_str = analysis_date.strftime("%Y-%m-%d")
        else:
            analysis_date_str = str(analysis_date)
            target_date = datetime.strptime(analysis_date_str, "%Y-%m-%d")
        target_date_str = target_date.strftime("%Y-%m-%dT00:00:00.000Z")

        # 1. 분석일의 종가 로드 (해당 날짜 데이터 필수 — 없으면 수집 미완료)
        daily_doc = db.daily_stock_data.find_one({"date": analysis_date_str})
        if not daily_doc or not daily_doc.get("stocks"):
            raise DailyDataNotCollectedError(analysis_date_str)

        current_prices = {}
        for ticker, val in daily_doc["stocks"].items():
            price = val if isinstance(val, (int, float)) else val.get("close_price")
            if price:
                current_prices[ticker] = float(price)

        result = {}

        # 3. stock_analysis_results 조회 (정확한 날짜, 풍부한 데이터)
        # 2026-05-20: date 는 string 통일.
        analysis_docs = list(db.stock_analysis_results.find({"date": analysis_date_str}))

        # Fallback: 정확한 날짜에 없으면 이전 최근 날짜 (최대 _MAX_PREDICTION_LOOKBACK_DAYS일)
        if not analysis_docs:
            min_lookback_str = (target_date - timedelta(days=_MAX_PREDICTION_LOOKBACK_DAYS)).strftime("%Y-%m-%d")
            latest = db.stock_analysis_results.find_one(
                {"date": {"$gte": min_lookback_str, "$lte": analysis_date_str}},
                sort=[("date", -1)],
            )
            if latest:
                fallback_date = latest["date"]  # string
                analysis_docs = list(db.stock_analysis_results.find({"date": fallback_date}))
                logger.info(
                    f"stock_analysis_results 날짜 fallback: {analysis_date_str} → {fallback_date} ({len(analysis_docs)}건)"
                )
            else:
                logger.warning(
                    f"stock_analysis_results {_MAX_PREDICTION_LOOKBACK_DAYS}일 이내 데이터 없음 (기준: {analysis_date_str})"
                )

        for doc in analysis_docs:
            ticker = doc.get("ticker")
            if not ticker:
                continue
            predictions = doc.get("predictions", {})
            predicted = predictions.get("predicted_future_price")
            rise_pct = predictions.get("rise_probability")  # % 단위
            if rise_pct is not None:
                try:
                    rise_pct = float(rise_pct)
                except (TypeError, ValueError):
                    rise_pct = None

            if rise_pct is None and predicted:
                current = current_prices.get(ticker, 0)
                if current and current > 0:
                    rise_pct = (predicted - current) / current * 100

            result[ticker] = {
                "predicted_price": float(predicted) if predicted else None,
                "current_price": current_prices.get(ticker, 0.0),
                "rise_probability": round(rise_pct, 2) if rise_pct is not None else 0.0,
            }

        # 4. stock_predictions 조회 (날짜는 ISODate(UTC)로 저장된 경우 많음 → 여러 방식 시도)
        preds = _query_stock_predictions_by_date(db, analysis_date_str, target_date)

        pred_count = 0
        for doc in preds:
            ticker = doc.get("ticker")
            if not ticker:
                continue
            if ticker in result:
                continue  # stock_analysis_results 데이터 우선

            predicted = doc.get("predicted_price")
            stored_rise = doc.get("rise_probability")
            current = current_prices.get(ticker, doc.get("actual_price", 0))

            # 저장된 rise_probability 우선 사용 (sync_service와 일치)
            # 없으면 predicted/current로 계산. predicted 없어도 stored_rise만 있으면 가능
            if stored_rise is not None:
                rise_prob = float(stored_rise)
            elif predicted and current and current > 0:
                rise_prob = (predicted - current) / current * 100
            else:
                # AI 데이터 의미 있는 신호 없으면 skip
                continue

            result[ticker] = {
                "predicted_price": float(predicted) if predicted else None,
                "current_price": float(current) if current else 0.0,
                "rise_probability": round(rise_prob, 2),
            }
            pred_count += 1

        logger.debug(
            f"AI 예측 로드: {analysis_date} → {len(result)}개 종목 "
            f"(analysis_results={len(analysis_docs)}, predictions 보충={pred_count})"
        )
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
        # AI rise_probability를 0-3.5 스케일로 정규화 (tech_score와 동일 범위)
        # 10% 상승 → 1.0, 20% 상승 → 2.0, 35% 이상 → 3.5
        ai_scores = {
            ticker: min(data["rise_probability"] / 10.0, 3.5)
            for ticker, data in ai_predictions.items()
        }

        buy_candidates = self.buy_criteria.filter_candidates(
            technical_results,
            ai_scores=ai_scores,
            sentiment_scores=sentiment_scores,
        )

        # 3. 아깝게 탈락한 종목 (near-miss TOP3)
        excluded_tickers = {c.get("ticker") for c in buy_candidates}
        near_miss_candidates = self.buy_criteria.get_near_miss_candidates(
            technical_results,
            excluded_tickers=excluded_tickers,
            ai_scores=ai_scores,
            sentiment_scores=sentiment_scores,
        )
        # near-miss에도 AI/감정 상세 데이터 추가
        for nm in near_miss_candidates:
            ticker = nm.get("ticker", "")
            ai_data = ai_predictions.get(ticker, {})
            nm["ai_prediction"] = {
                "rise_probability": ai_data.get("rise_probability", 0.0),
                "predicted_price": ai_data.get("predicted_price"),
                "current_price": ai_data.get("current_price"),
            }
            nm["sentiment_score"] = sentiment_scores.get(ticker, 0.0)

        # 4. 후보에 상세 데이터 추가
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

        # 등급별 요약 (RecommendationGrade enum → label 문자열로 변환)
        grade_summary = {}
        for c in buy_candidates:
            g = c.get("grade")
            if g:
                label = g.label if hasattr(g, "label") else str(g)
                grade_summary[label] = grade_summary.get(label, 0) + 1

        report = {
            "analysis_date": analysis_date,
            "total_analyzed": len(technical_results),
            "sentiment_count": len(sentiment_scores),
            "prediction_count": len(ai_predictions),
            "candidate_count": len(buy_candidates),
            "buy_candidates": buy_candidates,
            "near_miss_candidates": near_miss_candidates,
            "grade_summary": grade_summary,
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
