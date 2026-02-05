"""
Technical Analysis Application Service

포트 기반 기술적 분석 서비스.
domain/strategy/indicators.py의 순수 함수를 사용합니다.
"""

import logging
from typing import List, Dict, Any, Optional
from datetime import datetime, timedelta
from dataclasses import asdict

import pandas as pd

from .ports import (
    StockRepositoryPort,
    PriceRepositoryPort,
    AnalysisResultRepositoryPort,
    AnalysisNotifierPort,
    TechnicalResult,
)
from domain.strategy.indicators import (
    calculate_sma,
    calculate_rsi,
    calculate_macd,
)

logger = logging.getLogger(__name__)


class TechnicalAnalysisApplicationService:
    """
    기술적 분석 애플리케이션 서비스

    포트를 통해 외부 시스템과 통신하고,
    도메인 레이어의 순수 함수를 사용하여 분석 수행.
    """

    def __init__(
        self,
        stock_repository: StockRepositoryPort,
        price_repository: PriceRepositoryPort,
        result_repository: AnalysisResultRepositoryPort,
        notifier: Optional[AnalysisNotifierPort] = None,
        lookback_days: int = 180
    ):
        self.stock_repository = stock_repository
        self.price_repository = price_repository
        self.result_repository = result_repository
        self.notifier = notifier
        self.lookback_days = lookback_days

    async def analyze_stocks(
        self,
        request_id: str,
        thread_ts: Optional[str] = None,
        target_date: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        전체 종목 기술적 분석

        Args:
            request_id: 요청 ID
            thread_ts: Slack 스레드 타임스탬프
            target_date: 분석 기준 날짜 (YYYY-MM-DD)

        Returns:
            분석 결과 요약
        """
        try:
            logger.info(f"[{request_id}] 기술적 분석 시작 (target_date={target_date})")

            # 시작 알림
            if self.notifier:
                await self.notifier.notify_analysis_start("technical", thread_ts)

            # 1. 활성 종목 조회
            active_stocks = await self.stock_repository.get_active_stocks()
            if not active_stocks:
                logger.warning("No active stocks found")
                return {"status": "success", "total_analyzed": 0, "recommended_count": 0, "results": []}

            ticker_to_name = {s.ticker: s.stock_name for s in active_stocks}

            # 2. 날짜 범위 결정
            if target_date:
                target_dt = datetime.strptime(target_date, "%Y-%m-%d")
                analysis_date = target_date
            else:
                target_dt = datetime.now()
                analysis_date = target_dt.strftime("%Y-%m-%d")

            start_dt = target_dt - timedelta(days=self.lookback_days)
            start_date = start_dt.strftime("%Y-%m-%d")
            end_date = analysis_date

            # 3. 가격 데이터 조회
            price_data = await self.price_repository.get_daily_prices(start_date, end_date)

            if not price_data:
                logger.warning("No price data found")
                return {"status": "success", "total_analyzed": 0, "recommended_count": 0, "results": []}

            # 4. 종목별 분석
            results: List[TechnicalResult] = []
            recommended: List[TechnicalResult] = []

            for ticker, prices in price_data.items():
                if len(prices) < 50:
                    continue

                result = self._analyze_single_stock(
                    ticker=ticker,
                    stock_name=ticker_to_name.get(ticker, ticker),
                    prices=prices,
                    analysis_date=analysis_date
                )

                if result:
                    results.append(result)
                    if result.is_recommended:
                        recommended.append(result)

            # 5. 결과 저장
            if results:
                await self.result_repository.save_batch(results)

            # 6. 완료 알림
            if self.notifier:
                await self.notifier.notify_analysis_complete(
                    "technical",
                    len(results),
                    len(recommended),
                    thread_ts
                )

            logger.info(f"[{request_id}] 기술적 분석 완료: {len(results)}개 분석, {len(recommended)}개 추천")

            return {
                "status": "success",
                "total_analyzed": len(results),
                "recommended_count": len(recommended),
                "results": [asdict(r) for r in results]
            }

        except Exception as e:
            logger.exception(f"[{request_id}] 기술적 분석 실패: {e}")

            if self.notifier:
                await self.notifier.notify_analysis_error("technical", str(e), thread_ts)

            return {
                "status": "failed",
                "error": str(e)
            }

    def _analyze_single_stock(
        self,
        ticker: str,
        stock_name: str,
        prices: list,
        analysis_date: str
    ) -> Optional[TechnicalResult]:
        """단일 종목 분석"""
        try:
            # DataFrame 생성
            df = pd.DataFrame([
                {"date": p.date, "close": p.close_price}
                for p in prices
            ])
            df["date"] = pd.to_datetime(df["date"])
            df.set_index("date", inplace=True)
            df.sort_index(inplace=True)
            df = df.ffill().bfill()

            close = df["close"]

            # 지표 계산 (domain layer 순수 함수 사용)
            sma20 = calculate_sma(close, 20)
            sma50 = calculate_sma(close, 50)
            rsi = calculate_rsi(close, 14)
            macd_line, signal_line, _ = calculate_macd(close)

            # 분석 날짜의 값 추출
            target_dt = pd.to_datetime(analysis_date)
            if target_dt not in df.index:
                return None

            latest_sma20 = sma20.loc[target_dt]
            latest_sma50 = sma50.loc[target_dt]
            latest_rsi = rsi.loc[target_dt]
            latest_macd = macd_line.loc[target_dt]
            latest_signal = signal_line.loc[target_dt]

            # NaN 체크
            if pd.isna(latest_sma20) or pd.isna(latest_sma50):
                return None

            # 시그널 판단
            golden_cross = latest_sma20 > latest_sma50
            macd_buy = latest_macd > latest_signal
            is_recommended = golden_cross and (latest_rsi < 50) and macd_buy

            return TechnicalResult(
                ticker=ticker,
                stock_name=stock_name,
                date=analysis_date,
                sma20=float(latest_sma20),
                sma50=float(latest_sma50),
                rsi=float(latest_rsi) if not pd.isna(latest_rsi) else 50.0,
                macd=float(latest_macd) if not pd.isna(latest_macd) else 0.0,
                signal=float(latest_signal) if not pd.isna(latest_signal) else 0.0,
                golden_cross=bool(golden_cross),
                macd_buy_signal=bool(macd_buy),
                is_recommended=bool(is_recommended)
            )

        except Exception as e:
            logger.warning(f"Failed to analyze {ticker}: {e}")
            return None
