"""Economic Data Service - 비즈니스 로직"""
import logging
import math
import time
import requests
import yfinance as yf
import pandas as pd
from datetime import datetime, timedelta
from typing import Dict, Any, Optional
from collections import defaultdict

from .repository import EconomicDataRepository
from core.config import settings

logger = logging.getLogger(__name__)


class EconomicDataService:
    """경제 데이터 수집 서비스"""

    def __init__(self):
        self.repository = EconomicDataRepository()

    def collect_economic_data(self, target_date: str = None) -> Dict[str, Any]:
        """
        경제 데이터를 수집하여 daily_stock_data에 저장합니다.
        날짜별로 fred_indicators와 yfinance_indicators를 통합하여 저장합니다.

        Args:
            target_date: 수집할 기준 날짜 (YYYY-MM-DD). 미입력 시 당일 기준으로 조회
        """
        try:
            # 기준 날짜 설정
            if target_date:
                try:
                    end_date = datetime.strptime(target_date, "%Y-%m-%d")
                    logger.info(f"경제 데이터 수집 시작 (기준일: {target_date})")
                except ValueError:
                    logger.error(f"잘못된 날짜 형식: {target_date}. YYYY-MM-DD 형식이어야 합니다.")
                    raise ValueError(f"Invalid date format: {target_date}. Expected YYYY-MM-DD")
            else:
                end_date = datetime.now()
                logger.info(f"경제 데이터 수집 시작 (기준일: {end_date.strftime('%Y-%m-%d')} - 당일)")

            # 날짜 범위 설정
            # GDP는 분기별 데이터로 발표 지연이 있어 365일 조회
            # CPI/실업률은 월별, 금리/환율은 일별
            start_date = end_date - timedelta(days=365)
            start_date_str = start_date.strftime("%Y-%m-%d")
            end_date_str = end_date.strftime("%Y-%m-%d")

            # FRED 및 Yahoo Finance 지표 조회
            fred_indicators = self._load_fred_indicators()
            yfinance_indicators = self._load_yfinance_indicators()

            # 날짜별 데이터를 그룹화할 딕셔너리
            daily_data = defaultdict(lambda: {
                "fred_indicators": {},
                "yfinance_indicators": {},
                "stocks": {}
            })

            # FRED 데이터 수집 (날짜별로 그룹화)
            fred_count = self._collect_fred_data_grouped(
                fred_indicators, start_date_str, end_date_str, daily_data
            )

            # Yahoo Finance 데이터 수집 (날짜별로 그룹화)
            yahoo_count = self._collect_yahoo_data_grouped(
                yfinance_indicators, start_date_str, end_date_str, daily_data
            )

            # 개별 종목 데이터 수집 (날짜별로 그룹화)
            stocks_count = self._collect_individual_stocks(
                start_date_str, end_date_str, daily_data
            )

            # daily_stock_data에 날짜별로 저장
            saved_dates = 0
            for date_str, data in daily_data.items():
                if self.repository.upsert_daily_data(date_str, data):
                    saved_dates += 1
                    logger.info(f"✅ daily_stock_data 저장: {date_str} (FRED: {len(data['fred_indicators'])}, Yahoo: {len(data['yfinance_indicators'])}, Stocks: {len(data['stocks'])})")

            logger.info(f"경제 데이터 수집 완료: FRED={fred_count}개 지표, Yahoo={yahoo_count}개 지표, Stocks={stocks_count}개 종목, {saved_dates}일치 저장")

            return {
                "success": True,
                "target_date": end_date_str,
                "fred_collected": fred_count,
                "yahoo_collected": yahoo_count,
                "stocks_collected": stocks_count,
                "dates_saved": saved_dates
            }

        except Exception as e:
            logger.error(f"경제 데이터 수집 실패: {e}")
            return {
                "success": False,
                "error": str(e)
            }

    def _load_fred_indicators(self) -> Dict[str, str]:
        """PostgreSQL에서 FRED 지표를 조회합니다."""
        indicators = {}
        try:
            docs = self.repository.find_active_fred_indicators()
            for doc in docs:
                if "code" in doc and "name" in doc:
                    indicators[doc["code"]] = doc["name"]
        except Exception as e:
            logger.error(f"FRED 지표 조회 실패: {e}")
        return indicators

    def _load_yfinance_indicators(self) -> Dict[str, str]:
        """PostgreSQL에서 Yahoo Finance 지표를 조회합니다. {ticker: name} 형식 반환."""
        indicators = {}
        try:
            docs = self.repository.find_active_yfinance_indicators()
            for doc in docs:
                if "ticker" in doc and "name" in doc:
                    indicators[doc["ticker"]] = doc["name"]
        except Exception as e:
            logger.error(f"Yahoo Finance 지표 조회 실패: {e}")
        return indicators

    def _collect_fred_data_grouped(
        self,
        indicators: Dict[str, str],
        start_date: str,
        end_date: str,
        daily_data: Dict[str, Dict]
    ) -> int:
        """
        FRED 데이터를 수집하여 daily_data에 날짜별로 그룹화합니다.

        Args:
            indicators: {code: name} 형식의 FRED 지표 딕셔너리
            start_date: 시작 날짜
            end_date: 종료 날짜
            daily_data: 날짜별 데이터를 저장할 딕셔너리 (참조로 전달)

        Returns:
            성공적으로 수집한 지표 개수
        """
        success_count = 0

        for code, name in indicators.items():
            try:
                df = self._fetch_fred_data(code, start_date, end_date)

                if df is not None and not df.empty:
                    # 각 날짜별로 데이터를 그룹화 (code를 키로 사용)
                    for date, row in df.iterrows():
                        date_str = date.strftime("%Y-%m-%d")
                        value = float(row.iloc[0]) if not pd.isna(row.iloc[0]) else None

                        if value is not None:
                            daily_data[date_str]["fred_indicators"][code] = {
                                "value": value,
                                "name": name
                            }

                    success_count += 1
                    logger.info(f"✅ FRED 데이터 수집 완료: {code} ({name})")

            except Exception as e:
                logger.error(f"❌ FRED 데이터 수집 실패: {code} - {e}")

        return success_count

    def _collect_yahoo_data_grouped(
        self,
        indicators: Dict[str, str],
        start_date: str,
        end_date: str,
        daily_data: Dict[str, Dict]
    ) -> int:
        """
        Yahoo Finance 데이터를 수집하여 daily_data에 날짜별로 그룹화합니다.

        Args:
            indicators: {ticker: name} 형식의 Yahoo Finance 지표 딕셔너리
            start_date: 시작 날짜
            end_date: 종료 날짜
            daily_data: 날짜별 데이터를 저장할 딕셔너리 (참조로 전달)

        Returns:
            성공적으로 수집한 지표 개수
        """
        success_count = 0

        for ticker, name in indicators.items():
            try:
                df = self._fetch_yahoo_data(ticker, start_date, end_date)

                if df is not None and not df.empty:
                    # 각 날짜별로 데이터를 그룹화 (ticker를 키로, OHLCV 전체 저장)
                    for date, row in df.iterrows():
                        date_str = date.strftime("%Y-%m-%d")
                        close_price = float(row["Close"]) if "Close" in row and not pd.isna(row["Close"]) else None

                        if close_price is not None:
                            daily_data[date_str]["yfinance_indicators"][ticker] = {
                                "open": float(row["Open"]) if "Open" in row and not pd.isna(row["Open"]) else None,
                                "high": float(row["High"]) if "High" in row and not pd.isna(row["High"]) else None,
                                "low": float(row["Low"]) if "Low" in row and not pd.isna(row["Low"]) else None,
                                "close": close_price,
                                "volume": int(row["Volume"]) if "Volume" in row and not pd.isna(row["Volume"]) else 0,
                                "close_price": close_price,
                                "name": name
                            }

                    success_count += 1
                    logger.info(f"✅ Yahoo Finance 데이터 수집 완료: {ticker} ({name})")

            except Exception as e:
                logger.error(f"❌ Yahoo Finance 데이터 수집 실패: {ticker} - {e}")

        return success_count

    def _fetch_fred_data(self, series_id: str, start_date: str, end_date: str) -> pd.DataFrame:
        """FRED API에서 데이터를 가져옵니다."""
        try:
            url = "https://api.stlouisfed.org/fred/series/observations"
            params = {
                "series_id": series_id,
                "api_key": settings.FRED_API_KEY,
                "file_type": "json",
                "observation_start": start_date,
                "observation_end": end_date
            }

            response = requests.get(url, params=params, timeout=10)
            response.raise_for_status()

            data = response.json()
            observations = data.get("observations", [])

            if not observations:
                return None

            df = pd.DataFrame(observations)
            df["date"] = pd.to_datetime(df["date"])
            df = df.set_index("date")
            df["value"] = pd.to_numeric(df["value"], errors="coerce")

            return df[["value"]]

        except Exception as e:
            logger.error(f"FRED 데이터 가져오기 실패: {series_id} - {e}")
            return None

    def _fetch_yahoo_data(self, ticker: str, start_date: str, end_date: str, stock_obj: yf.Ticker = None) -> pd.DataFrame:
        """Yahoo Finance에서 데이터를 가져옵니다.

        Args:
            ticker: 종목 티커
            start_date: 시작 날짜
            end_date: 종료 날짜
            stock_obj: 재사용할 yf.Ticker 인스턴스 (없으면 새로 생성)
        """
        try:
            stock = stock_obj or yf.Ticker(ticker)
            df = stock.history(start=start_date, end=end_date, interval="1d")

            if df is None or df.empty:
                return None

            return df

        except Exception as e:
            logger.error(f"Yahoo Finance 데이터 가져오기 실패: {ticker} - {e}")
            return None

    def _collect_individual_stocks(
        self,
        start_date: str,
        end_date: str,
        daily_data: Dict[str, Dict]
    ) -> int:
        """
        개별 종목 데이터를 수집하여 daily_data에 날짜별로 그룹화합니다.

        Args:
            start_date: 시작 날짜
            end_date: 종료 날짜
            daily_data: 날짜별 데이터를 저장할 딕셔너리 (참조로 전달)

        Returns:
            성공적으로 수집한 종목 개수
        """
        success_count = 0

        # PostgreSQL에서 활성 종목 조회
        active_stocks = self.repository.find_active_stocks_from_postgres()
        tickers = [s["ticker"] for s in active_stocks if "ticker" in s]

        logger.info(f"📊 개별 종목 데이터 수집 시작: {len(tickers)}개 종목 (PostgreSQL)")

        for ticker in tickers:
            try:
                # yf.Ticker 인스턴스를 1회 생성하여 OHLCV + info에 재사용
                stock_obj = yf.Ticker(ticker)
                df = self._fetch_yahoo_data(ticker, start_date, end_date, stock_obj=stock_obj)

                if df is not None and not df.empty:
                    # 펀더멘탈 데이터 수집 (종목당 1회)
                    info_data = self._fetch_ticker_info(ticker, stock_obj=stock_obj)

                    # 가장 최신 날짜 계산 (info는 최신 날짜에만 저장)
                    latest_date_str = df.index.max().strftime("%Y-%m-%d")

                    # 각 날짜별로 데이터를 그룹화
                    for date, row in df.iterrows():
                        date_str = date.strftime("%Y-%m-%d")

                        # OHLCV 전체 저장
                        if "Close" in row and not pd.isna(row["Close"]):
                            stock_data = {
                                "open": float(row["Open"]) if "Open" in row and not pd.isna(row["Open"]) else None,
                                "high": float(row["High"]) if "High" in row and not pd.isna(row["High"]) else None,
                                "low": float(row["Low"]) if "Low" in row and not pd.isna(row["Low"]) else None,
                                "close": float(row["Close"]),
                                "volume": int(row["Volume"]) if "Volume" in row and not pd.isna(row["Volume"]) else 0,
                                # 하위 호환성을 위해 close_price도 유지
                                "close_price": float(row["Close"])
                            }
                            # info는 최신 날짜에만 저장 (동일 스냅샷 중복 방지)
                            if info_data and date_str == latest_date_str:
                                stock_data["info"] = info_data
                            daily_data[date_str]["stocks"][ticker] = stock_data

                    success_count += 1
                    logger.info(f"✅ 종목 데이터 수집 완료: {ticker} ({len(df)}일, info={'있음' if info_data else '없음'})")

            except Exception as e:
                logger.error(f"❌ 종목 데이터 수집 실패: {ticker} - {e}")

        logger.info(f"📊 개별 종목 데이터 수집 완료: {success_count}/{len(tickers)}개")
        return success_count

    # 퀀트 분석에 필요한 펀더멘탈 필드 (~40개)
    _INFO_FIELDS = {
        # 밸류에이션
        "trailingPE", "forwardPE", "priceToBook", "priceToSalesTrailing12Months",
        "enterpriseToEbitda", "enterpriseToRevenue", "trailingPegRatio",
        # 수익성
        "profitMargins", "operatingMargins", "grossMargins", "ebitdaMargins",
        "returnOnAssets", "returnOnEquity",
        # 성장성
        "earningsGrowth", "revenueGrowth", "earningsQuarterlyGrowth",
        # 재무 건전성
        "debtToEquity", "currentRatio", "quickRatio", "freeCashflow",
        "totalCash", "totalDebt",
        # 주당 지표
        "epsTrailingTwelveMonths", "epsForward", "bookValue",
        "revenuePerShare", "totalCashPerShare",
        # 배당
        "dividendYield", "dividendRate", "payoutRatio",
        # 시장 데이터
        "marketCap", "enterpriseValue", "beta", "sharesOutstanding", "floatShares",
        # 애널리스트
        "recommendationMean", "targetMeanPrice", "targetMedianPrice",
        "targetHighPrice", "targetLowPrice", "numberOfAnalystOpinions",
        # 공매도
        "shortRatio", "shortPercentOfFloat",
        # 52주 / 이동평균
        "fiftyTwoWeekHigh", "fiftyTwoWeekLow", "52WeekChange",
        "fiftyDayAverage", "twoHundredDayAverage",
        # 거래량
        "averageVolume", "averageDailyVolume10Day",
        # 분류
        "sector", "industry",
    }

    def _fetch_ticker_info(self, ticker: str, stock_obj: yf.Ticker = None) -> Optional[Dict[str, Any]]:
        """yfinance에서 종목 펀더멘탈 정보를 가져옵니다 (퀀트 분석용 필드만 저장).

        Args:
            ticker: 종목 티커
            stock_obj: 재사용할 yf.Ticker 인스턴스 (없으면 새로 생성)
        """
        try:
            stock = stock_obj or yf.Ticker(ticker)
            raw_info = stock.info

            if not raw_info:
                return None

            # 허용 필드만 추출 + NaN/None/inf 필터링
            cleaned = {}
            for k, v in raw_info.items():
                if k not in self._INFO_FIELDS:
                    continue
                if v is None:
                    continue
                if isinstance(v, float) and (math.isnan(v) or math.isinf(v)):
                    continue
                cleaned[k] = v

            # rate limiting (기존 yfinance 호출 간격 활용)
            time.sleep(0.2)

            return cleaned if cleaned else None

        except Exception as e:
            time.sleep(0.2)  # rate limit even on failure
            logger.warning(f"펀더멘탈 정보 수집 실패: {ticker} - {e}")
            return None
