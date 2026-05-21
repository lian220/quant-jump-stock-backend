"""
RecommendationSyncService - MongoDB → PostgreSQL 동기화

MongoDB에 저장된 AI 예측, 감정 분석, 기술적 분석 결과를 통합하여
PostgreSQL prediction_results 테이블에 저장한다.

Composite Score = 0.3 × ai + 0.4 × tech + 0.3 × sentiment (동적 가중치 재분배, 최대 ~7.4)
  - AI score:        0~10  (rise_probability_normalized × 10)
    * rise_probability_normalized = 0.5 + rise_pct/40  (0%→0.5, +20%→1.0, -20%→0.0)
  - Tech score:      0~3.5 (골든크로스 1.5 + RSI<threshold 1.0 + MACD매수 1.0)
    * RSI threshold: RecommendationCriteriaSettings.rsi_threshold (기본 70)
  - Sentiment score: 0~10  ((sentiment + 1) / 2 × 10)
  - 최대값: 0.3×10 + 0.4×3.5 + 0.3×10 = 3.0 + 1.4 + 3.0 = 7.4

Grade 기준: S≥6.0, A≥4.5, B≥3.0, C≥1.5, D<1.5
(banbu-stocktrading 원본 기준, rise_prob 25% 기준 composite ≈ 7.5)
"""

import logging
from typing import Dict, List, Optional, Any
from decimal import Decimal
from psycopg2.extras import execute_values

from core.database import MongoDB, PostgreSQL
from domain.recommendation.scoring_policy import ScoringPolicy

logger = logging.getLogger(__name__)


class RecommendationSyncService:
    """MongoDB 분석 결과를 PostgreSQL로 동기화하는 서비스"""

    def __init__(self, policy: Optional[ScoringPolicy] = None):
        self.mongo_db = MongoDB.get_db()
        # ScoringPolicy SSoT (PR 1)
        self._policy = policy or ScoringPolicy.load_default()

    def sync_latest_recommendations(self, analysis_date: str) -> Dict[str, Any]:
        """
        최신 분석 결과를 PostgreSQL로 동기화

        Args:
            analysis_date: 분석 날짜 (YYYY-MM-DD)

        Returns:
            동기화 결과 요약
        """
        try:
            logger.info("🔄 [Sync] MongoDB → PostgreSQL 동기화 시작 (date=%s)", analysis_date)

            # 1. MongoDB에서 데이터 조회
            ai_predictions = self._fetch_ai_predictions(analysis_date)
            ai_analysis_results = self._fetch_stock_analysis_results(analysis_date)
            sentiment_analysis = self._fetch_sentiment_analysis(analysis_date)
            technical_analysis = self._fetch_technical_analysis(analysis_date)
            current_prices = self._fetch_current_prices(analysis_date)

            # stock_analysis_results 데이터를 ai_predictions에 병합 (analysis_results 우선)
            ai_predictions = self._merge_ai_sources(ai_predictions, ai_analysis_results)

            logger.info(
                "[Sync] 조회 완료: AI=%d, Sentiment=%d, Tech=%d, Prices=%d (date=%s)",
                len(ai_predictions), len(sentiment_analysis), len(technical_analysis),
                len(current_prices), analysis_date,
            )

            # 2. 종목별로 데이터 병합
            merged_data = self._merge_analysis_data(
                ai_predictions,
                sentiment_analysis,
                technical_analysis,
                current_prices  # 🆕 Phase 6.5
            )

            if not merged_data:
                logger.warning("⚠️ [Sync] 병합된 데이터 없음 (date=%s)", analysis_date)
                return {"status": "success", "synced_count": 0, "message": "No data to sync"}

            # 3. PostgreSQL에 저장
            synced_count = self._save_to_postgresql(merged_data, analysis_date)

            logger.info("✅ [Sync] 동기화 완료: %d개 종목", synced_count)

            return {
                "status": "success",
                "analysis_date": analysis_date,
                "synced_count": synced_count,
                "ai_count": len(ai_predictions),
                "sentiment_count": len(sentiment_analysis),
                "tech_count": len(technical_analysis)
            }

        except Exception as e:
            logger.exception("❌ [Sync] 동기화 실패: %s", e)
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
            # 2026-05-20: stock_analysis_results.date 도 string 통일.
            results = list(self.mongo_db.stock_analysis_results.find({
                "date": analysis_date
            }))

            output = {}
            for r in results:
                if "ticker" not in r:
                    continue

                predictions = r.get("predictions", {})
                rise_pct = predictions.get("rise_probability")  # 상승 예상 % (e.g. 16.25)

                # rise_probability (%) → 0~1 정규화 (Decimal end-to-end, PR 1 일관성)
                # 0% = 0.5 (중립), +20% = 1.0 (최대 강세), -20% = 0.0 (최대 약세)
                rise_prob_normalized = None
                if rise_pct is not None:
                    rise_pct_d = Decimal(str(rise_pct))
                    normalized = Decimal("0.5") + rise_pct_d / Decimal("40")
                    rise_prob_normalized = float(max(Decimal("0"), min(Decimal("1"), normalized)))

                output[r["ticker"]] = {
                    "predicted_price": predictions.get("predicted_future_price"),
                    "rise_probability": rise_prob_normalized,
                    "recommendation": r.get("recommendation"),
                    "accuracy": r.get("metrics", {}).get("accuracy"),
                }

            return output

        except Exception as e:
            logger.warning("AI 분석 결과 조회 실패: %s", e)
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

        진정성 규칙(ADR 0001): analysis_date 정확 매칭만. fallback 없음.
        - 옛 데이터를 새 날짜 라벨로 적재하지 않음 (76일 정지 시절 거짓 적재 결함 차단).
        - 데이터 없으면 빈 dict 반환 → 추천 시스템이 "데이터 없음"으로 정직하게 처리.
        - rise_probability 없으면 predicted_price/actual_price로 계산 후 0~1 정규화.
        """
        try:
            # 2026-05-20: stock_predictions.date 도 string 통일.
            # 정확한 날짜 매칭만. fallback 없음 (가짜 적재 차단)
            predictions = list(self.mongo_db.stock_predictions.find({
                "date": analysis_date
            }))

            if not predictions:
                logger.warning(
                    f"[Sync] stock_predictions 데이터 없음 (date={analysis_date}). "
                    f"fallback 비활성화 — AI 데이터 누락으로 처리. (ADR 0001 진정성 규칙)"
                )

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
                    # _fetch_stock_analysis_results와 동일 스케일: 0.5 + rise_pct/40.0
                    # ratio는 소수 (e.g., 0.1 = 10%), rise_pct는 % 단위이므로 ratio*100/40 = ratio*2.5
                    rise = max(0.0, min(1.0, 0.5 + ratio * 2.5))
                out[ticker] = {
                    "predicted_price": float(predicted) if predicted is not None else None,
                    "rise_probability": float(rise) if rise is not None else None,
                }
            return out
        except Exception as e:
            logger.warning("AI 예측 조회 실패: %s", e, exc_info=True)
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
            logger.warning("감정 분석 조회 실패: %s", e)
            return {}

    def _fetch_technical_analysis(self, analysis_date: str) -> Dict[str, Dict]:
        """
        기술적 분석 데이터 조회 (MongoDB stock_recommendations).
        2026-05-20: date 는 string 통일. 단순 매칭.
        """
        try:
            recommendations = self._query_recommendations_by_date(analysis_date)

            # fallback: 데이터 없으면 이전 최근 날짜 조회
            if not recommendations:
                logger.warning(
                    f"[Sync] stock_recommendations 데이터 없음 (date={analysis_date}). "
                    f"fallback 비활성화 — Tech 데이터 누락으로 처리. (ADR 0001 진정성 규칙)"
                )

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
            logger.warning("기술적 분석 조회 실패: %s", e)
            return {}

    def _query_recommendations_by_date(self, date_str: str) -> list:
        """날짜별 stock_recommendations 조회.

        2026-05-20: 전체 컬렉션 string 통일 (NYSE 거래일, timezone-independent).
        """
        return list(self.mongo_db.stock_recommendations.find({"date": date_str}))

    def _fetch_current_prices(self, analysis_date: str) -> Dict[str, float]:
        """
        기준가 조회 (MongoDB daily_stock_data)

        analysis_date 당일 데이터가 없거나 stocks가 비어 있을 경우
        (장중 partial bar 필터링 후 당일 데이터 미수집 상태),
        analysis_date 이전 가장 최근 거래일 종가를 fallback으로 사용합니다.

        Args:
            analysis_date: 분석 날짜 (YYYY-MM-DD)

        Returns:
            {ticker: close_price} 딕셔너리
        """
        try:
            def _extract_prices(doc) -> Dict[str, float]:
                prices = {}
                for ticker, stock_data in doc.get("stocks", {}).items():
                    if "close" in stock_data:
                        prices[ticker] = float(stock_data["close"])
                return prices

            # 1차: analysis_date 당일 데이터 조회
            daily_data = self.mongo_db.daily_stock_data.find_one({"date": analysis_date})

            if daily_data and daily_data.get("stocks"):
                prices = _extract_prices(daily_data)
                if prices:
                    logger.info("[Sync] 기준가 조회 완료 (date=%s, 종목수=%d)", analysis_date, len(prices))
                    return prices

            # 2차: fallback — analysis_date 이전 가장 최근 거래일 종가 사용
            logger.warning(
                f"⚠️ [Sync] {analysis_date} stocks 없음 → 직전 거래일 종가로 fallback"
            )
            fallback_doc = self.mongo_db.daily_stock_data.find_one(
                {"date": {"$lt": analysis_date}, "stocks": {"$exists": True, "$ne": {}}},
                sort=[("date", -1)]
            )

            if not fallback_doc:
                logger.warning("⚠️ [Sync] fallback 데이터도 없음 (date<%s)", analysis_date)
                return {}

            prices = _extract_prices(fallback_doc)
            fallback_date = fallback_doc.get("date", "unknown")
            logger.info(
                f"[Sync] 기준가 fallback 완료 (fallback_date={fallback_date}, 종목수={len(prices)})"
            )
            return prices

        except Exception as e:
            logger.warning("기준가 조회 실패: %s", e)
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

            # rise_probability가 없지만 predicted_price가 있으면 직접 계산 (Decimal end-to-end)
            rise_probability = ai.get("rise_probability")
            current_price = price_data.get(ticker)
            if rise_probability is None and ai.get("predicted_price") and current_price:
                predicted_d = Decimal(str(ai["predicted_price"]))
                current_d = Decimal(str(current_price))
                ratio = (predicted_d - current_d) / current_d
                # _fetch_stock_analysis_results와 동일 스케일: 0.5 + rise_pct/40 (ratio×2.5 ≈ rise_pct/40)
                normalized = Decimal("0.5") + ratio * Decimal("2.5")
                rise_probability = float(max(Decimal("0"), min(Decimal("1"), normalized)))

            # ScoringPolicy SSoT 위임 (PR 1)
            ai_score = self._policy.ai_score_from_normalized(
                Decimal(str(rise_probability)) if rise_probability is not None else None
            )
            tech_score = self._policy.tech_score_from_indicators(tech) if tech else Decimal("0")
            tech_signals_count = self._policy.count_tech_signals(tech) if tech else 0
            sent_raw = sentiment.get("sentiment_score") if sentiment else None
            sentiment_score = self._policy.sentiment_score_from_raw(
                Decimal(str(sent_raw)) if sent_raw is not None else None
            )

            has_ai = rise_probability is not None
            has_sentiment = sent_raw is not None
            has_tech = bool(tech)

            score_result = self._policy.compose_components(
                ai_score=ai_score,
                tech_score=tech_score,
                sentiment_score=sentiment_score,
                has_ai=has_ai,
                has_tech=has_tech,
                has_sentiment=has_sentiment,
                tech_signal_count=tech_signals_count,
            )
            composite_score = score_result.composite_score
            grade = score_result.grade

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
                "score": score_result,  # ScoringPolicy Score 객체 (Task 2.3 에서 다운스트림 사용)
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
                "is_recommended": self._determine_recommended(
                    composite_score, grade, price_recommendation
                ),
                "recommendation_reason": reason,
                # 🆕 Phase 6.5: 가격 메트릭
                "current_price": current_price,
                "target_price": target_price,
                "upside_percent": round(upside_percent, 2) if upside_percent is not None else None,
                "price_recommendation": price_recommendation,
                # 🆕 PR 2 (2026-05-21): ScoringPolicy 라벨 SSoT + AI 예측 출처 추적
                "recommendation_label": score_result.recommendation_label,
                "effective_prediction_date": None,  # sync_service 는 fallback 없음 (ADR 0001)
            })

        # Composite Score 내림차순 정렬
        merged.sort(key=lambda x: x["composite_score"], reverse=True)

        return merged

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

    def _determine_recommended(
        self,
        composite_score: Decimal,
        grade: str,
        price_recommendation: Optional[str] = None,
    ) -> bool:
        """
        추천 여부 판정 (ADR 0001 진정성 규칙 + verdict gate)

        두 게이트 통과해야 is_recommended=True:
        1. composite ≥ 3.0 (grade B 이상)
        2. price_recommendation ∉ {매도, 강력매도}

        verdict gate (#2)의 이유:
        - composite_score 산식과 price_recommendation 산식이 별도 결정
        - 같은 입력에서 "강력매수 grade A" + "매도 priceRec" 동시 출력 가능
        - 모순 라벨 차단을 위해 매도 신호 시 추천 자동 제외 (Drucker/Doumont 지적)

        Args:
            composite_score: 종합 점수 (0~7.4)
            grade: 등급 (S, A, B, C, D)
            price_recommendation: 가격 추천 (강력매수/매수/보유/매도)

        Returns:
            추천 여부 (True/False)
        """
        # Gate 1: grade B 이상
        if composite_score < Decimal("3.0"):
            return False
        # Gate 2: 매도 신호면 추천 안 함 (모순 차단)
        if price_recommendation in ("매도", "강력매도", "SELL", "STRONG_SELL"):
            return False
        return True

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
            recommendation_label, effective_prediction_date,
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
            recommendation_label = EXCLUDED.recommendation_label,
            effective_prediction_date = EXCLUDED.effective_prediction_date,
            updated_at = CURRENT_TIMESTAMP
        """

        row_template = "(" + ",".join(["%s"] * 28) + ", CURRENT_TIMESTAMP)"
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
                data.get("recommendation_label"), data.get("effective_prediction_date"),
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
            logger.exception("❌ [Sync] 배치 upsert 실패, 개별 저장으로 폴백: %s", batch_error)

        # 폴백: 개별 쿼리 (기존 방식)
        synced_count = 0
        fallback_sql = upsert_sql.replace("VALUES %s", f"VALUES {row_template}")
        for row in rows:
            try:
                PostgreSQL.execute_query(fallback_sql, row, fetch_all=False)
                synced_count += 1
            except Exception as row_error:
                logger.exception("❌ [Sync] 개별 저장 실패 (ticker=%s): %s", row[0], row_error)
        return synced_count
