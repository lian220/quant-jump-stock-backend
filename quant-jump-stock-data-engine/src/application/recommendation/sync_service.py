"""
RecommendationSyncService - MongoDB → PostgreSQL 동기화

MongoDB에 저장된 AI 예측, 감정 분석, 기술적 분석 결과를 통합하여
PostgreSQL prediction_results 테이블에 저장한다.

Composite Score = 0.3 × ai + 0.4 × tech + 0.3 × sentiment (max 7.5)
"""

import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
from decimal import Decimal
from psycopg2.extras import execute_values

from core.database import MongoDB, PostgreSQL

logger = logging.getLogger(__name__)


class RecommendationSyncService:
    """MongoDB 분석 결과를 PostgreSQL로 동기화하는 서비스"""

    def __init__(self):
        self.mongo_db = MongoDB.get_db()
        # Composite Score 가중치
        self.weight_ai = Decimal("0.3")
        self.weight_tech = Decimal("0.4")
        self.weight_sentiment = Decimal("0.3")

    def sync_latest_recommendations(self, analysis_date: str) -> Dict[str, Any]:
        """
        최신 분석 결과를 PostgreSQL로 동기화

        Args:
            analysis_date: 분석 날짜 (YYYY-MM-DD)

        Returns:
            동기화 결과 요약
        """
        try:
            logger.info(f"🔄 [Sync] MongoDB → PostgreSQL 동기화 시작 (date={analysis_date})")

            # 1. MongoDB에서 데이터 조회
            ai_predictions = self._fetch_ai_predictions(analysis_date)
            ai_analysis_results = self._fetch_stock_analysis_results(analysis_date)
            sentiment_analysis = self._fetch_sentiment_analysis(analysis_date)
            technical_analysis = self._fetch_technical_analysis(analysis_date)
            current_prices = self._fetch_current_prices(analysis_date)

            # stock_analysis_results 데이터를 ai_predictions에 병합 (analysis_results 우선)
            ai_predictions = self._merge_ai_sources(ai_predictions, ai_analysis_results)

            logger.info(
                f"[Sync] 조회 완료: AI={len(ai_predictions)}, Sentiment={len(sentiment_analysis)}, "
                f"Tech={len(technical_analysis)}, Prices={len(current_prices)} (date={analysis_date})"
            )

            # 2. 종목별로 데이터 병합
            merged_data = self._merge_analysis_data(
                ai_predictions,
                sentiment_analysis,
                technical_analysis,
                current_prices  # 🆕 Phase 6.5
            )

            if not merged_data:
                logger.warning(f"⚠️ [Sync] 병합된 데이터 없음 (date={analysis_date})")
                return {"status": "success", "synced_count": 0, "message": "No data to sync"}

            # 3. PostgreSQL에 저장
            synced_count = self._save_to_postgresql(merged_data, analysis_date)

            logger.info(f"✅ [Sync] 동기화 완료: {synced_count}개 종목")

            return {
                "status": "success",
                "analysis_date": analysis_date,
                "synced_count": synced_count,
                "ai_count": len(ai_predictions),
                "sentiment_count": len(sentiment_analysis),
                "tech_count": len(technical_analysis)
            }

        except Exception as e:
            logger.exception(f"❌ [Sync] 동기화 실패: {e}")
            return {"status": "failed", "error": str(e)}

    def _fetch_stock_analysis_results(self, analysis_date: str) -> Dict[str, Dict]:
        """
        AI 분석 결과 조회 (MongoDB stock_analysis_results) - 정확한 날짜 매칭

        - predictions.rise_probability: 상승 예상 퍼센트 (%)
        - predictions.predicted_future_price: 예측 미래 가격
        - recommendation: STRONG BUY / BUY / HOLD / SELL
        - metrics.accuracy: 모델 정확도
        """
        try:
            from datetime import datetime as dt
            target_date = dt.strptime(analysis_date, "%Y-%m-%d")
            # date 필드가 ISODate 또는 String일 수 있으므로 둘 다 검색
            target_date_str = target_date.strftime("%Y-%m-%dT00:00:00.000Z")
            results = list(self.mongo_db.stock_analysis_results.find({
                "$or": [
                    {"date": target_date},
                    {"date": target_date_str},
                ]
            }))

            output = {}
            for r in results:
                if "ticker" not in r:
                    continue

                predictions = r.get("predictions", {})
                rise_pct = predictions.get("rise_probability")  # 상승 예상 % (e.g. 16.25)

                # rise_probability (%) → 0~1 정규화
                # 0% = 0.5 (중립), +20% = 1.0 (최대 강세), -20% = 0.0 (최대 약세)
                rise_prob_normalized = None
                if rise_pct is not None:
                    rise_prob_normalized = max(0.0, min(1.0, 0.5 + rise_pct / 40.0))

                output[r["ticker"]] = {
                    "predicted_price": predictions.get("predicted_future_price"),
                    "rise_probability": rise_prob_normalized,
                    "recommendation": r.get("recommendation"),
                    "accuracy": r.get("metrics", {}).get("accuracy"),
                }

            return output

        except Exception as e:
            logger.warning(f"AI 분석 결과 조회 실패: {e}")
            return {}

    def _merge_ai_sources(
        self,
        predictions: Dict[str, Dict],
        analysis_results: Dict[str, Dict]
    ) -> Dict[str, Dict]:
        """
        stock_predictions와 stock_analysis_results를 병합

        stock_analysis_results의 rise_probability와 predicted_price를 우선 사용.
        stock_predictions에만 있는 종목은 그대로 유지.
        """
        merged = dict(predictions)  # stock_predictions 기본

        for ticker, ar_data in analysis_results.items():
            if ticker not in merged:
                merged[ticker] = {}

            # analysis_results의 rise_probability 우선 (stock_predictions에는 없는 필드)
            if ar_data.get("rise_probability") is not None:
                merged[ticker]["rise_probability"] = ar_data["rise_probability"]

            # analysis_results의 predicted_price 우선 (stock_predictions에도 있지만 analysis가 더 최신)
            if ar_data.get("predicted_price") is not None:
                merged[ticker]["predicted_price"] = ar_data["predicted_price"]

        return merged

    def _fetch_ai_predictions(self, analysis_date: str) -> Dict[str, Dict]:
        """
        AI 예측 데이터 조회 (MongoDB stock_predictions).
        - date 필드가 ISODate(UTC)로 저장되므로 naive datetime exact match + 문자열 $or 사용.
        - rise_probability 없으면 predicted_price/actual_price로 계산 후 0~1 정규화.
        """
        try:
            dt_date = datetime.strptime(analysis_date, "%Y-%m-%d")

            # naive datetime exact match + 문자열 $or (comprehensive_report.py / _fetch_stock_analysis_results 패턴)
            predictions = list(self.mongo_db.stock_predictions.find({
                "$or": [
                    {"date": dt_date},
                    {"date": analysis_date},
                ]
            }))

            logger.info(
                f"[Sync] stock_predictions 조회 date={analysis_date} -> {len(predictions)}건 "
                f"(ai_predicted_price/ai_rise_probability 반영용)"
            )

            out = {}
            for pred in predictions:
                ticker = pred.get("ticker")
                if not ticker:
                    continue
                predicted = pred.get("predicted_price")
                actual = pred.get("actual_price")
                rise = pred.get("rise_probability")
                if rise is None and predicted is not None and actual is not None and actual != 0:
                    ratio = (float(predicted) - float(actual)) / float(actual)
                    rise = max(0.0, min(1.0, 0.5 + ratio))
                out[ticker] = {
                    "predicted_price": float(predicted) if predicted is not None else None,
                    "rise_probability": float(rise) if rise is not None else None,
                }
            return out
        except Exception as e:
            logger.warning(f"AI 예측 조회 실패: {e}", exc_info=True)
            return {}

    def _fetch_sentiment_analysis(self, analysis_date: str) -> Dict[str, Dict]:
        """감정 분석 데이터 조회 (MongoDB sentiment_analysis)"""
        try:
            sentiments = list(self.mongo_db.sentiment_analysis.find(
                {"date": analysis_date}
            ))

            return {
                sent["ticker"]: {
                    # 실제 필드명: average_sentiment_score, article_count
                    "sentiment_score": sent.get("average_sentiment_score", sent.get("sentiment_score")),  # -1~1
                    "news_count": sent.get("article_count", sent.get("news_count", 0)),
                } for sent in sentiments if "ticker" in sent
            }
        except Exception as e:
            logger.warning(f"감정 분석 조회 실패: {e}")
            return {}

    def _fetch_technical_analysis(self, analysis_date: str) -> Dict[str, Dict]:
        """기술적 분석 데이터 조회 (MongoDB stock_recommendations). date 필드 문자열/ISODate 둘 다 매칭."""
        try:
            dt_date = datetime.strptime(analysis_date, "%Y-%m-%d")
            start_utc = datetime(dt_date.year, dt_date.month, dt_date.day, 0, 0, 0)
            end_utc = start_utc + timedelta(days=1)

            recommendations = list(self.mongo_db.stock_recommendations.find(
                {"date": {"$gte": start_utc, "$lt": end_utc}}
            ))
            if not recommendations:
                recommendations = list(self.mongo_db.stock_recommendations.find(
                    {"date": analysis_date}
                ))
            if not recommendations:
                y, m, d = dt_date.year, dt_date.month, dt_date.day
                recommendations = list(self.mongo_db.stock_recommendations.find({
                    "$expr": {
                        "$and": [
                            {"$eq": [{"$year": "$date"}, y]},
                            {"$eq": [{"$month": "$date"}, m]},
                            {"$eq": [{"$dayOfMonth": "$date"}, d]},
                        ]
                    }
                }))

            results = {}
            for rec in recommendations:
                if "ticker" not in rec:
                    continue

                tech_indicators = rec.get("technical_indicators", {})
                results[rec["ticker"]] = {
                    "stock_name": rec.get("stock_name", rec["ticker"]),
                    "sma20": tech_indicators.get("sma20"),
                    "sma50": tech_indicators.get("sma50"),
                    "rsi": tech_indicators.get("rsi"),
                    "macd": tech_indicators.get("macd"),
                    "signal": tech_indicators.get("signal"),
                    "golden_cross": tech_indicators.get("golden_cross", False),
                    "macd_buy_signal": tech_indicators.get("macd_buy_signal", False),
                    "is_recommended": rec.get("is_recommended", False),
                }

            return results

        except Exception as e:
            logger.warning(f"기술적 분석 조회 실패: {e}")
            return {}

    def _fetch_current_prices(self, analysis_date: str) -> Dict[str, float]:
        """
        현재가 조회 (MongoDB daily_stock_data)

        Args:
            analysis_date: 분석 날짜 (YYYY-MM-DD)

        Returns:
            {ticker: close_price} 딕셔너리
        """
        try:
            # daily_stock_data에서 해당 날짜 데이터 조회
            daily_data = self.mongo_db.daily_stock_data.find_one({"date": analysis_date})

            if not daily_data or "stocks" not in daily_data:
                logger.warning(f"⚠️ [Sync] 일별 데이터 없음 (date={analysis_date})")
                return {}

            # stocks 딕셔너리에서 close 가격 추출
            prices = {}
            for ticker, stock_data in daily_data["stocks"].items():
                if "close" in stock_data:
                    prices[ticker] = float(stock_data["close"])

            return prices

        except Exception as e:
            logger.warning(f"현재가 조회 실패: {e}")
            return {}

    def _merge_analysis_data(
        self,
        ai_data: Dict,
        sentiment_data: Dict,
        tech_data: Dict,
        price_data: Dict  # 🆕 Phase 6.5
    ) -> List[Dict]:
        """종목별로 AI + 감정 + 기술적 데이터 병합 및 Composite Score 계산"""

        # 모든 종목 티커 수집
        all_tickers = set(ai_data.keys()) | set(sentiment_data.keys()) | set(tech_data.keys())

        merged = []
        for ticker in all_tickers:
            ai = ai_data.get(ticker, {})
            sentiment = sentiment_data.get(ticker, {})
            tech = tech_data.get(ticker, {})

            # AI·감정·기술 중 하나라도 있으면 병합 (기술적 분석 필수 아님)
            if not ai and not sentiment and not tech:
                continue

            # rise_probability가 없지만 predicted_price가 있으면 직접 계산
            rise_probability = ai.get("rise_probability")
            current_price = price_data.get(ticker)
            if rise_probability is None and ai.get("predicted_price") and current_price:
                rise_probability = (ai["predicted_price"] - current_price) / current_price

            # AI 점수 계산 (0~5)
            ai_score = self._calculate_ai_score(rise_probability)

            # 감정 점수 계산 (0~5)
            sentiment_score = self._calculate_sentiment_score(sentiment.get("sentiment_score"))

            # 기술적 점수 계산 (0~3.5, 없으면 0)
            tech_score = self._calculate_tech_score(tech) if tech else Decimal("0")
            tech_signals_count = self._count_tech_signals(tech) if tech else 0

            # Composite Score 계산 (누락된 지표는 분모에서 제외, 기술도 선택)
            has_ai = rise_probability is not None
            has_sentiment = sentiment.get("sentiment_score") is not None
            has_tech = bool(tech)
            composite_score = self._calculate_composite_score(
                ai_score=ai_score,
                sentiment_score=sentiment_score,
                tech_score=tech_score,
                has_ai=has_ai,
                has_sentiment=has_sentiment,
                has_tech=has_tech,
            )

            # 등급 판정
            grade = self._determine_grade(composite_score)

            # 추천 이유 생성
            reason = self._generate_recommendation_reason(
                ai_score, sentiment_score, tech_score, tech_signals_count
            )

            # 🆕 Phase 6.5: 가격 메트릭 계산
            target_price = ai.get("predicted_price")
            upside_percent = None
            price_recommendation = None

            if current_price and target_price:
                upside_percent = ((target_price - current_price) / current_price) * 100
                price_recommendation = self._determine_price_recommendation(upside_percent)

            stock_name = (tech or {}).get("stock_name") or (sentiment or {}).get("stock_name") or (ai or {}).get("stock_name") or ticker
            merged.append({
                "ticker": ticker,
                "stock_name": stock_name,
                "ai_predicted_price": ai.get("predicted_price"),
                "ai_rise_probability": rise_probability,
                "ai_score": float(ai_score),
                "sentiment_score": sentiment.get("sentiment_score"),
                "sentiment_news_count": sentiment.get("news_count"),
                "sentiment_normalized_score": float(sentiment_score),
                "tech_sma20": (tech or {}).get("sma20"),
                "tech_sma50": (tech or {}).get("sma50"),
                "tech_rsi": (tech or {}).get("rsi"),
                "tech_macd": (tech or {}).get("macd"),
                "tech_signal": (tech or {}).get("signal"),
                "tech_golden_cross": (tech or {}).get("golden_cross", False),
                "tech_macd_buy_signal": (tech or {}).get("macd_buy_signal", False),
                "tech_score": float(tech_score),
                "tech_signals_count": tech_signals_count,
                "composite_score": float(composite_score),
                "composite_grade": grade,
                "is_recommended": self._determine_recommended(composite_score, grade),
                "recommendation_reason": reason,
                # 🆕 Phase 6.5: 가격 메트릭
                "current_price": current_price,
                "target_price": target_price,
                "upside_percent": round(upside_percent, 2) if upside_percent else None,
                "price_recommendation": price_recommendation,
            })

        # Composite Score 내림차순 정렬
        merged.sort(key=lambda x: x["composite_score"], reverse=True)

        return merged

    def _calculate_composite_score(
        self,
        ai_score: Decimal,
        sentiment_score: Decimal,
        tech_score: Decimal,
        has_ai: bool,
        has_sentiment: bool,
        has_tech: bool = True,
    ) -> Decimal:
        """
        Composite Score 계산 (누락 지표 제외 가중 평균).
        AI/감정/기술 모두 선택 사항이며, 있는 지표만 가중 평균에 포함.
        """
        weighted_sum = Decimal("0")
        weight_sum = Decimal("0")
        if has_tech:
            weighted_sum += self.weight_tech * tech_score
            weight_sum += self.weight_tech
        if has_ai:
            weighted_sum += self.weight_ai * ai_score
            weight_sum += self.weight_ai
        if has_sentiment:
            weighted_sum += self.weight_sentiment * sentiment_score
            weight_sum += self.weight_sentiment
        if weight_sum == 0:
            return Decimal("0")

        return (weighted_sum / weight_sum).quantize(Decimal("0.01"))

    def _calculate_ai_score(self, rise_probability: Optional[float]) -> Decimal:
        """AI 점수 계산: rise_probability × 5 (0~5)"""
        if rise_probability is None:
            return Decimal("0")
        try:
            value = Decimal(str(rise_probability))
        except Exception:
            logger.warning(f"rise_probability 변환 실패 (값={rise_probability!r}), 0으로 대체")
            return Decimal("0")
        return value * Decimal("5")

    def _calculate_sentiment_score(self, sentiment: Optional[float]) -> Decimal:
        """감정 점수 정규화: (sentiment + 1) / 2 × 5 (0~5)"""
        if sentiment is None:
            return Decimal("0")
        # sentiment: -1~1 → normalized: 0~5
        normalized = (Decimal(str(sentiment)) + Decimal("1")) / Decimal("2") * Decimal("5")
        return normalized.quantize(Decimal("0.01"))

    def _calculate_tech_score(self, tech: Dict) -> Decimal:
        """기술적 점수 계산: 1.5×골든크로스 + 1.0×RSI<50 + 1.0×MACD매수 (0~3.5)"""
        score = Decimal("0")
        if tech.get("golden_cross"):
            score += Decimal("1.5")
        if tech.get("rsi") and tech["rsi"] < 50:
            score += Decimal("1.0")
        if tech.get("macd_buy_signal"):
            score += Decimal("1.0")
        return score

    def _count_tech_signals(self, tech: Dict) -> int:
        """충족된 기술 신호 개수 (0~3)"""
        count = 0
        if tech.get("golden_cross"):
            count += 1
        if tech.get("rsi") and tech["rsi"] < 50:
            count += 1
        if tech.get("macd_buy_signal"):
            count += 1
        return count

    def _determine_grade(self, composite_score: Decimal) -> str:
        """등급 판정: S, A, B, C, D"""
        if composite_score >= Decimal("6.0"):
            return "S"
        elif composite_score >= Decimal("4.5"):
            return "A"
        elif composite_score >= Decimal("3.0"):
            return "B"
        elif composite_score >= Decimal("1.5"):
            return "C"
        else:
            return "D"

    def _generate_recommendation_reason(
        self,
        ai_score: Decimal,
        sentiment_score: Decimal,
        tech_score: Decimal,
        tech_signals: int
    ) -> Optional[str]:
        """추천 이유 생성"""
        reasons = []

        if ai_score >= Decimal("3.5"):
            reasons.append(f"AI 예측 강세({float(ai_score):.1f}점)")
        if sentiment_score >= Decimal("3.5"):
            reasons.append(f"뉴스 긍정({float(sentiment_score):.1f}점)")
        if tech_score >= Decimal("2.0"):
            reasons.append(f"기술적 신호 {tech_signals}/3개 충족")

        return ", ".join(reasons) if reasons else None

    def _determine_recommended(self, composite_score: Decimal, grade: str) -> bool:
        """
        추천 여부 판정 (Composite Score 기준)

        Args:
            composite_score: 종합 점수 (0~7.5)
            grade: 등급 (S, A, B, C, D)

        Returns:
            추천 여부 (True/False)
        """
        # BETA 상태 (AI/감정 미통합): 0.8점 이상
        # 통합 후: 2.0점 이상 (C등급)
        MIN_SCORE_BETA = Decimal("0.8")
        # MIN_SCORE_INTEGRATED = Decimal("2.0")  # 통합 후 활성화

        # 현재는 BETA 기준 사용
        return composite_score >= MIN_SCORE_BETA

    def _determine_price_recommendation(self, upside_percent: float) -> str:
        """
        상승여력 기반 가격 추천 등급

        Args:
            upside_percent: 상승여력 (%)

        Returns:
            추천 등급 (강력매수/매수/보유/매도)
        """
        if upside_percent >= 10.0:
            return "강력매수"
        elif upside_percent >= 5.0:
            return "매수"
        elif upside_percent >= -5.0:
            return "보유"
        else:
            return "매도"

    def _save_to_postgresql(self, merged_data: List[Dict], analysis_date: str) -> int:
        """PostgreSQL에 배치 upsert"""
        if not merged_data:
            return 0

        upsert_sql = """
        INSERT INTO prediction_results (
            ticker, stock_name, analysis_date,
            ai_predicted_price, ai_rise_probability, ai_score,
            sentiment_score, sentiment_news_count, sentiment_normalized_score,
            tech_sma20, tech_sma50, tech_rsi, tech_macd, tech_signal,
            tech_golden_cross, tech_macd_buy_signal, tech_score, tech_signals_count,
            composite_score, composite_grade,
            is_recommended, recommendation_reason,
            current_price, target_price, upside_percent, price_recommendation,
            updated_at
        ) VALUES %s
        ON CONFLICT (ticker, analysis_date)
        DO UPDATE SET
            stock_name = EXCLUDED.stock_name,
            ai_predicted_price = EXCLUDED.ai_predicted_price,
            ai_rise_probability = EXCLUDED.ai_rise_probability,
            ai_score = EXCLUDED.ai_score,
            sentiment_score = EXCLUDED.sentiment_score,
            sentiment_news_count = EXCLUDED.sentiment_news_count,
            sentiment_normalized_score = EXCLUDED.sentiment_normalized_score,
            tech_sma20 = EXCLUDED.tech_sma20,
            tech_sma50 = EXCLUDED.tech_sma50,
            tech_rsi = EXCLUDED.tech_rsi,
            tech_macd = EXCLUDED.tech_macd,
            tech_signal = EXCLUDED.tech_signal,
            tech_golden_cross = EXCLUDED.tech_golden_cross,
            tech_macd_buy_signal = EXCLUDED.tech_macd_buy_signal,
            tech_score = EXCLUDED.tech_score,
            tech_signals_count = EXCLUDED.tech_signals_count,
            composite_score = EXCLUDED.composite_score,
            composite_grade = EXCLUDED.composite_grade,
            is_recommended = EXCLUDED.is_recommended,
            recommendation_reason = EXCLUDED.recommendation_reason,
            current_price = EXCLUDED.current_price,
            target_price = EXCLUDED.target_price,
            upside_percent = EXCLUDED.upside_percent,
            price_recommendation = EXCLUDED.price_recommendation,
            updated_at = CURRENT_TIMESTAMP
        """

        row_template = "(" + ",".join(["%s"] * 26) + ", CURRENT_TIMESTAMP)"
        rows = [
            (
                data["ticker"], data["stock_name"], analysis_date,
                data["ai_predicted_price"], data["ai_rise_probability"], data["ai_score"],
                data["sentiment_score"], data["sentiment_news_count"], data["sentiment_normalized_score"],
                data["tech_sma20"], data["tech_sma50"], data["tech_rsi"],
                data["tech_macd"], data["tech_signal"],
                data["tech_golden_cross"], data["tech_macd_buy_signal"],
                data["tech_score"], data["tech_signals_count"],
                data["composite_score"], data["composite_grade"],
                data["is_recommended"], data["recommendation_reason"],
                data["current_price"], data["target_price"], data["upside_percent"], data["price_recommendation"],
            )
            for data in merged_data
        ]

        try:
            with PostgreSQL.get_connection() as conn:
                with conn.cursor() as cursor:
                    execute_values(
                        cursor,
                        upsert_sql,
                        rows,
                        template=row_template,
                        page_size=200,
                    )
            return len(rows)
        except Exception as batch_error:
            logger.error(f"❌ [Sync] 배치 upsert 실패, 개별 저장으로 폴백: {batch_error}")

        # 폴백: 개별 쿼리 (기존 방식)
        synced_count = 0
        fallback_sql = upsert_sql.replace("VALUES %s", f"VALUES {row_template}")
        for row in rows:
            try:
                PostgreSQL.execute_query(fallback_sql, row, fetch_all=False)
                synced_count += 1
            except Exception as row_error:
                logger.error(f"❌ [Sync] 개별 저장 실패 (ticker={row[0]}): {row_error}")
        return synced_count
