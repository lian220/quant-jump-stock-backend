"""
감성 분석 기반 SCREENING 전략 백테스트

전략:
- 매일 sentiment_score > 0.2 AND sentiment_count >= 10 인 종목 선별
- 선별된 종목에 균등 분산 투자
- 5일 후 매도 (또는 조건 미충족 시 즉시 매도)

사용법:
    python scripts/backtest_sentiment_strategy.py
"""
import os
import sys
from pathlib import Path
from datetime import date, timedelta
from decimal import Decimal
import pandas as pd
import numpy as np

# 환경 변수 로드
try:
    from dotenv import load_dotenv
    project_root = Path(__file__).parent.parent.parent
    env_file = project_root / ".env.local"
    if not env_file.exists():
        env_file = project_root / ".env"
    load_dotenv(env_file)

    mongodb_uri = os.environ.get("MONGODB_URI")
    if mongodb_uri:
        os.environ["MONGODB_URI"] = mongodb_uri.replace("mongodb:27017", "localhost:27017")
        print(f"✅ 환경 변수 로드: {env_file}")
except Exception as e:
    print(f"⚠️  .env 파일 로드 실패: {e}")

# src 경로 추가
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))

from application.backtest.data_loader_mongo import MongoDataLoader


class SentimentScreeningBacktest:
    """감성 분석 SCREENING 백테스트"""

    def __init__(
        self,
        symbols: list,
        start_date: date,
        end_date: date,
        initial_capital: Decimal = Decimal("100000"),
        sentiment_threshold: float = 0.2,
        sentiment_count_min: int = 10,
        holding_days: int = 5,
        stop_loss_pct: float = -3.0,
        take_profit_pct: float = 5.0
    ):
        self.symbols = symbols
        self.start_date = start_date
        self.end_date = end_date
        self.initial_capital = initial_capital
        self.sentiment_threshold = sentiment_threshold
        self.sentiment_count_min = sentiment_count_min
        self.holding_days = holding_days
        self.stop_loss_pct = stop_loss_pct
        self.take_profit_pct = take_profit_pct

        self.loader = MongoDataLoader()
        self.data = {}
        self.positions = {}  # {symbol: {"entry_date": date, "entry_price": float, "shares": int}}
        self.equity_curve = []
        self.trades = []
        self.cash = initial_capital

    def load_data(self):
        """데이터 로드 (감성 분석 포함)"""
        print(f"\n📊 데이터 로드 중...")
        print(f"   - 종목: {len(self.symbols)}개")
        print(f"   - 기간: {self.start_date} ~ {self.end_date}")

        self.data = self.loader.load(
            self.symbols,
            self.start_date,
            self.end_date,
            include_sentiment=True
        )

        print(f"✅ 데이터 로드 완료: {len(self.data)}개 종목")
        for symbol, df in self.data.items():
            print(f"   - {symbol}: {len(df)} days")

    def get_screening_candidates(self, current_date: pd.Timestamp) -> list:
        """SCREENING 조건을 만족하는 종목 조회"""
        candidates = []

        for symbol, df in self.data.items():
            if current_date not in df.index:
                continue

            row = df.loc[current_date]

            # SCREENING 조건
            if (
                row["sentiment_score"] > self.sentiment_threshold and
                row["sentiment_count"] >= self.sentiment_count_min
            ):
                candidates.append({
                    "symbol": symbol,
                    "price": float(row["close"]),
                    "sentiment_score": float(row["sentiment_score"]),
                    "sentiment_count": int(row["sentiment_count"])
                })

        return candidates

    def run(self):
        """백테스트 실행"""
        print(f"\n{'='*80}")
        print(f"🚀 백테스트 시작")
        print(f"{'='*80}")
        print(f"전략 설정:")
        print(f"  - SCREENING 조건: sentiment_score > {self.sentiment_threshold} AND count >= {self.sentiment_count_min}")
        print(f"  - 보유 기간: 최대 {self.holding_days}일")
        print(f"  - Stop Loss: {self.stop_loss_pct}%")
        print(f"  - Take Profit: {self.take_profit_pct}%")
        print(f"  - 초기 자본: ${self.initial_capital:,.2f}")
        print(f"{'='*80}\n")

        # 데이터 로드
        self.load_data()

        # 모든 날짜 가져오기 (모든 종목의 날짜 합집합)
        all_dates = set()
        for df in self.data.values():
            all_dates.update(df.index)
        all_dates = sorted(all_dates)

        print(f"\n📅 백테스트 기간: {len(all_dates)} 거래일")

        # 매일 반복
        for i, current_date in enumerate(all_dates):
            # 포트폴리오 가치 계산
            portfolio_value = float(self.cash)
            for symbol, position in self.positions.items():
                if current_date in self.data[symbol].index:
                    current_price = float(self.data[symbol].loc[current_date]["close"])
                    portfolio_value += position["shares"] * current_price

            self.equity_curve.append({
                "date": current_date,
                "portfolio_value": portfolio_value,
                "cash": float(self.cash),
                "positions_count": len(self.positions)
            })

            # 보유 종목 청산 체크 (Stop Loss / Take Profit / holding_days)
            to_sell = []
            for symbol, position in self.positions.items():
                if current_date not in self.data[symbol].index:
                    continue

                current_price = float(self.data[symbol].loc[current_date]["close"])
                entry_price = position["entry_price"]
                profit_pct = ((current_price - entry_price) / entry_price) * 100
                holding_days = (current_date - position["entry_date"]).days

                # 청산 조건 체크
                exit_reason = None
                if profit_pct <= self.stop_loss_pct:
                    exit_reason = f"Stop Loss ({self.stop_loss_pct}%)"
                    to_sell.append((symbol, exit_reason))
                elif profit_pct >= self.take_profit_pct:
                    exit_reason = f"Take Profit ({self.take_profit_pct}%)"
                    to_sell.append((symbol, exit_reason))
                elif holding_days >= self.holding_days:
                    exit_reason = f"Max Holding ({self.holding_days}일)"
                    to_sell.append((symbol, exit_reason))

            # 청산 실행
            for symbol, exit_reason in to_sell:
                position = self.positions[symbol]
                if current_date in self.data[symbol].index:
                    exit_price = float(self.data[symbol].loc[current_date]["close"])
                    exit_value = position["shares"] * exit_price
                    self.cash += Decimal(str(exit_value))

                    # 거래 기록
                    profit = exit_value - (position["shares"] * position["entry_price"])
                    profit_pct = (profit / (position["shares"] * position["entry_price"])) * 100

                    self.trades.append({
                        "symbol": symbol,
                        "entry_date": position["entry_date"],
                        "exit_date": current_date,
                        "entry_price": position["entry_price"],
                        "exit_price": exit_price,
                        "shares": position["shares"],
                        "profit": profit,
                        "profit_pct": profit_pct,
                        "holding_days": (current_date - position["entry_date"]).days,
                        "exit_reason": exit_reason
                    })

                    print(f"[{current_date.date()}] 📤 SELL {symbol}: {position['shares']} shares @ ${exit_price:.2f} (profit: ${profit:,.2f}, {profit_pct:+.2f}%) [{exit_reason}]")

                del self.positions[symbol]

            # SCREENING 후보 조회
            candidates = self.get_screening_candidates(current_date)

            # 신규 매수 (포지션이 없고 후보가 있을 때)
            if len(self.positions) == 0 and len(candidates) > 0 and self.cash > Decimal("1000"):
                # 균등 분산 투자
                capital_per_stock = float(self.cash) / len(candidates)

                for candidate in candidates:
                    symbol = candidate["symbol"]
                    price = candidate["price"]
                    shares = int(capital_per_stock / price)

                    if shares > 0:
                        cost = Decimal(str(shares * price))
                        self.cash -= cost

                        self.positions[symbol] = {
                            "entry_date": current_date,
                            "entry_price": price,
                            "shares": shares
                        }

                        print(f"[{current_date.date()}] 📥 BUY {symbol}: {shares} shares @ ${price:.2f} (sentiment: {candidate['sentiment_score']:.4f}, cost: ${cost:,.2f})")

        # 백테스트 종료 - 남은 포지션 청산
        final_date = all_dates[-1]
        for symbol, position in list(self.positions.items()):
            if final_date in self.data[symbol].index:
                exit_price = float(self.data[symbol].loc[final_date]["close"])
                exit_value = position["shares"] * exit_price
                self.cash += Decimal(str(exit_value))

                profit = exit_value - (position["shares"] * position["entry_price"])
                profit_pct = (profit / (position["shares"] * position["entry_price"])) * 100

                self.trades.append({
                    "symbol": symbol,
                    "entry_date": position["entry_date"],
                    "exit_date": final_date,
                    "entry_price": position["entry_price"],
                    "exit_price": exit_price,
                    "shares": position["shares"],
                    "profit": profit,
                    "profit_pct": profit_pct,
                    "holding_days": (final_date - position["entry_date"]).days
                })

                print(f"[{final_date.date()}] 📤 FINAL SELL {symbol}: {position['shares']} shares @ ${exit_price:.2f} (profit: ${profit:,.2f}, {profit_pct:+.2f}%)")

        # 최종 자본
        final_value = float(self.cash)

        print(f"\n{'='*80}")
        print(f"✅ 백테스트 완료")
        print(f"{'='*80}\n")

        self.calculate_metrics(final_value)
        self.show_trades()

    def calculate_metrics(self, final_value: float):
        """백테스트 결과 메트릭 계산"""
        print(f"\n{'='*80}")
        print(f"📊 백테스트 결과")
        print(f"{'='*80}\n")

        # 기본 수익률
        total_return = (final_value - float(self.initial_capital)) / float(self.initial_capital) * 100
        profit = final_value - float(self.initial_capital)

        print(f"💰 자본:")
        print(f"   - 초기 자본: ${float(self.initial_capital):,.2f}")
        print(f"   - 최종 자본: ${final_value:,.2f}")
        print(f"   - 총 수익: ${profit:,.2f} ({total_return:+.2f}%)")

        # CAGR 계산
        days = (self.end_date - self.start_date).days
        years = days / 365.25
        cagr = ((final_value / float(self.initial_capital)) ** (1 / years) - 1) * 100 if years > 0 else 0

        print(f"\n📈 수익률:")
        print(f"   - CAGR: {cagr:+.2f}%")
        print(f"   - 기간: {days}일 ({years:.2f}년)")

        # MDD 계산
        if self.equity_curve:
            equity_values = [ec["portfolio_value"] for ec in self.equity_curve]
            peak = equity_values[0]
            max_drawdown = 0

            for value in equity_values:
                if value > peak:
                    peak = value
                drawdown = (peak - value) / peak * 100
                if drawdown > max_drawdown:
                    max_drawdown = drawdown

            print(f"   - MDD: -{max_drawdown:.2f}%")

        # 거래 통계
        if self.trades:
            winning_trades = [t for t in self.trades if t["profit"] > 0]
            losing_trades = [t for t in self.trades if t["profit"] <= 0]
            win_rate = len(winning_trades) / len(self.trades) * 100

            avg_profit = np.mean([t["profit"] for t in self.trades])
            avg_profit_pct = np.mean([t["profit_pct"] for t in self.trades])

            print(f"\n📊 거래 통계:")
            print(f"   - 총 거래 수: {len(self.trades)}")
            print(f"   - 승리: {len(winning_trades)} ({win_rate:.1f}%)")
            print(f"   - 패배: {len(losing_trades)} ({100-win_rate:.1f}%)")
            print(f"   - 평균 수익: ${avg_profit:,.2f} ({avg_profit_pct:+.2f}%)")

            if winning_trades:
                avg_win = np.mean([t["profit"] for t in winning_trades])
                avg_win_pct = np.mean([t["profit_pct"] for t in winning_trades])
                print(f"   - 평균 승리: ${avg_win:,.2f} ({avg_win_pct:+.2f}%)")

            if losing_trades:
                avg_loss = np.mean([t["profit"] for t in losing_trades])
                avg_loss_pct = np.mean([t["profit_pct"] for t in losing_trades])
                print(f"   - 평균 손실: ${avg_loss:,.2f} ({avg_loss_pct:+.2f}%)")

            # Profit Factor
            total_wins = sum([t["profit"] for t in winning_trades]) if winning_trades else 0
            total_losses = abs(sum([t["profit"] for t in losing_trades])) if losing_trades else 0
            profit_factor = total_wins / total_losses if total_losses > 0 else float('inf')
            print(f"   - Profit Factor: {profit_factor:.2f}")

    def show_trades(self):
        """거래 내역 출력"""
        if not self.trades:
            print("\n⚠️  거래 내역이 없습니다.")
            return

        print(f"\n{'='*80}")
        print(f"📜 거래 내역 (최근 10개)")
        print(f"{'='*80}\n")

        df_trades = pd.DataFrame(self.trades)
        print(df_trades.tail(10).to_string(index=False))


def main():
    """메인 실행 함수"""
    # 백테스트 설정
    symbols = ["AAPL", "NVDA", "TSLA", "MSFT", "GOOGL", "AMZN", "META"]
    end_date = date.today()
    start_date = date(2025, 12, 1)  # 2025-12-01부터 시작
    initial_capital = Decimal("100000")

    # 백테스트 실행
    backtest = SentimentScreeningBacktest(
        symbols=symbols,
        start_date=start_date,
        end_date=end_date,
        initial_capital=initial_capital,
        sentiment_threshold=0.2,
        sentiment_count_min=10,
        holding_days=5,
        stop_loss_pct=-3.0,
        take_profit_pct=5.0
    )

    backtest.run()

    # 연결 종료
    backtest.loader.close()


if __name__ == "__main__":
    main()
