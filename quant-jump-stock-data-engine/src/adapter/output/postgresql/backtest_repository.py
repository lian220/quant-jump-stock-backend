"""
PostgreSQL Backtest Result Repository Adapter

백테스트 결과를 PostgreSQL에 저장하는 어댑터.
Spring 백엔드에서 조회할 수 있도록 결과를 저장합니다.

SCRUM-186: Kafka Consumer + PostgreSQL 결과 저장
"""

import logging
import asyncio
from typing import Optional
from decimal import Decimal
import json
import psycopg2
import psycopg2.extras
from contextlib import contextmanager

from datetime import date, datetime, timezone, timedelta
from dataclasses import dataclass, field
from typing import List, Dict, Any

# KST 타임존 (UTC+9)
KST = timezone(timedelta(hours=9))


def get_kst_now() -> datetime:
    """현재 KST 시간 반환 (naive datetime - timezone 정보 없음)"""
    # PostgreSQL timestamp without time zone 컬럼에 저장하기 위해
    # timezone 정보 없는 naive datetime 반환
    return datetime.now(KST).replace(tzinfo=None)

from application.backtest.result import BacktestResult, BacktestTrade, EquityCurvePoint

logger = logging.getLogger(__name__)


@dataclass
class BacktestCheckpoint:
    """
    백테스트 체크포인트 (포트폴리오 상태 스냅샷)

    Note: equity_curve는 backtest_results에서 조회하므로 여기에 저장하지 않음.
          중복 저장 방지 및 저장 용량 최적화.
    """
    backtest_id: int
    checkpoint_date: date
    cash: Decimal
    high_watermark: Decimal
    positions: List[Dict[str, Any]] = field(default_factory=list)
    trade_count: int = 0
    id: Optional[int] = None


class PostgresBacktestRepository:
    """
    PostgreSQL 백테스트 결과 저장소

    백테스트 실행 결과를 PostgreSQL에 저장하여
    Spring 백엔드에서 조회할 수 있도록 합니다.
    """

    def __init__(
        self,
        host: str,
        port: int,
        database: str,
        user: str,
        password: str
    ):
        self._conn_params = {
            "host": host,
            "port": port,
            "database": database,
            "user": user,
            "password": password
        }

    @contextmanager
    def _get_connection(self):
        """PostgreSQL 연결 컨텍스트 매니저"""
        conn = None
        try:
            conn = psycopg2.connect(**self._conn_params)
            yield conn
        except Exception as e:
            logger.error(f"PostgreSQL connection error: {e}")
            raise
        finally:
            if conn:
                conn.close()

    async def save_result(self, result: BacktestResult, request_id: Optional[str] = None) -> int:
        """
        백테스트 결과 저장

        Args:
            result: 백테스트 결과
            request_id: 요청 ID (Kafka 메시지 추적용)

        Returns:
            생성된 backtest_results.id
        """
        return await asyncio.to_thread(self._save_result_sync, result, request_id)

    def _save_result_sync(self, result: BacktestResult, request_id: Optional[str] = None) -> int:
        """save_result의 동기 구현"""
        try:
            with self._get_connection() as conn:
                with conn.cursor() as cursor:
                    # 1. backtest_results 저장
                    result_id = self._insert_result(cursor, result)

                    # 2. backtest_trades 저장
                    if result.trades:
                        self._insert_trades(cursor, result_id, result.trades)

                    # 3. equity_curve는 JSONB로 저장 (별도 테이블 없음)
                    if result.equity_curve:
                        self._update_equity_curve(cursor, result_id, result.equity_curve)

                    conn.commit()

                    logger.info(
                        f"Saved backtest result: id={result_id}, "
                        f"strategy={result.strategy_id}, trades={len(result.trades)}"
                    )
                    return result_id

        except Exception as e:
            logger.error(f"Failed to save backtest result: {e}")
            raise

    def _insert_result(self, cursor, result: BacktestResult) -> int:
        """backtest_results 테이블에 삽입"""
        cursor.execute(
            """
            INSERT INTO backtest_results (
                strategy_id,
                start_date,
                end_date,
                initial_capital,
                final_value,
                total_return,
                cagr,
                mdd,
                sharpe_ratio,
                sortino_ratio,
                volatility,
                total_trades,
                winning_trades,
                losing_trades,
                win_rate,
                avg_win,
                avg_loss,
                benchmark_return,
                alpha,
                beta,
                equity_curve,
                status,
                created_at,
                completed_at
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s
            ) RETURNING id
            """,
            (
                result.strategy_id,
                result.start_date,
                result.end_date,
                float(result.initial_capital),
                float(result.final_value),
                float(result.total_return),
                float(result.cagr),
                float(result.mdd),
                float(result.sharpe_ratio) if result.sharpe_ratio else None,
                float(result.sortino_ratio) if result.sortino_ratio else None,
                float(result.volatility) if result.volatility else None,
                result.total_trades,
                result.winning_trades,
                result.losing_trades,
                float(result.win_rate) if result.win_rate else None,
                float(result.avg_win) if result.avg_win else None,
                float(result.avg_loss) if result.avg_loss else None,
                float(result.benchmark_return) if result.benchmark_return else None,
                float(result.alpha) if result.alpha else None,
                float(result.beta) if result.beta else None,
                json.dumps([{"date": str(p.date), "equity": float(p.equity)} for p in result.equity_curve]) if result.equity_curve else None,
                "COMPLETED",
                get_kst_now(),  # created_at
                get_kst_now()   # completed_at
            )
        )
        return cursor.fetchone()[0]

    def _insert_trades(self, cursor, result_id: int, trades: list[BacktestTrade]):
        """backtest_trades 테이블에 일괄 삽입"""
        if not trades:
            return

        values = []
        for trade in trades:
            values.append((
                result_id,
                trade.trade_date,
                trade.symbol,  # ticker
                trade.trade_type.upper(),  # side
                trade.quantity,
                float(trade.price),
                float(trade.amount),
                float(trade.commission),
                float(trade.realized_pnl) if trade.realized_pnl else None,
                float(trade.realized_pnl_pct) if trade.realized_pnl_pct else None,
                trade.holding_days,
                trade.exit_reason  # signal_reason
            ))

        psycopg2.extras.execute_batch(
            cursor,
            """
            INSERT INTO backtest_trades (
                backtest_id,
                trade_date,
                ticker,
                side,
                quantity,
                price,
                amount,
                commission,
                pnl,
                pnl_percent,
                holding_days,
                signal_reason
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """,
            values,
            page_size=100
        )

        logger.debug(f"Inserted {len(trades)} trades for result_id={result_id}")

    def _update_equity_curve(
        self,
        cursor,
        result_id: int,
        equity_curve: list[EquityCurvePoint]
    ):
        """equity_curve를 JSONB로 저장"""
        equity_data = [
            {
                "date": point.date.isoformat(),
                "equity": float(point.equity),
                "cash": float(point.cash),
                "positions_value": float(point.positions_value),
                "drawdown_pct": float(point.drawdown_pct)
            }
            for point in equity_curve
        ]

        cursor.execute(
            """
            UPDATE backtest_results
            SET equity_curve = %s
            WHERE id = %s
            """,
            (json.dumps(equity_data), result_id)
        )

    def _calculate_return_pct(self, result: BacktestResult) -> Decimal:
        """총 수익률 퍼센트 계산"""
        if result.initial_capital == 0:
            return Decimal("0")
        return (result.total_return / result.initial_capital) * 100

    async def update_status(
        self,
        result_id: int,
        status: str,
        error_message: Optional[str] = None
    ):
        """결과 상태 업데이트"""
        await asyncio.to_thread(self._update_status_sync, result_id, status, error_message)

    def _update_status_sync(
        self,
        result_id: int,
        status: str,
        error_message: Optional[str] = None
    ):
        """update_status의 동기 구현"""
        try:
            with self._get_connection() as conn:
                with conn.cursor() as cursor:
                    cursor.execute(
                        """
                        UPDATE backtest_results
                        SET status = %s,
                            error_message = %s,
                            updated_at = %s
                        WHERE id = %s
                        """,
                        (status, error_message, get_kst_now(), result_id)
                    )
                    conn.commit()

            logger.info(f"Updated backtest result {result_id} status to {status}")

        except Exception as e:
            logger.error(f"Failed to update backtest result status: {e}")
            raise

    async def find_by_id(self, result_id: int) -> Optional[BacktestResult]:
        """결과 ID로 조회"""
        return await asyncio.to_thread(self._find_by_id_sync, result_id)

    def _find_by_id_sync(self, result_id: int) -> Optional[BacktestResult]:
        """find_by_id의 동기 구현"""
        try:
            with self._get_connection() as conn:
                with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
                    cursor.execute(
                        """
                        SELECT br.*, s.name as strategy_name
                        FROM backtest_results br
                        JOIN strategies s ON br.strategy_id = s.id
                        WHERE br.id = %s
                        """,
                        (result_id,)
                    )
                    row = cursor.fetchone()

                    if not row:
                        return None

                    # trades 조회
                    cursor.execute(
                        """
                        SELECT * FROM backtest_trades
                        WHERE backtest_id = %s
                        ORDER BY trade_date
                        """,
                        (result_id,)
                    )
                    trade_rows = cursor.fetchall()

            return self._to_domain(row, trade_rows)

        except Exception as e:
            logger.error(f"Failed to find backtest result {result_id}: {e}")
            return None

    def _to_domain(self, row: dict, trade_rows: list) -> BacktestResult:
        """DB 행 → 도메인 모델 변환"""
        trades = [
            BacktestTrade(
                symbol=t["ticker"],  # DB column: ticker
                trade_type=t["side"].lower(),  # DB column: side
                trade_date=t["trade_date"],
                price=Decimal(str(t["price"])),
                quantity=t["quantity"],
                amount=Decimal(str(t["amount"])),
                commission=Decimal(str(t["commission"])),
                exit_reason=t.get("signal_reason"),  # DB column: signal_reason
                realized_pnl=Decimal(str(t["pnl"])) if t.get("pnl") else None,
                realized_pnl_pct=Decimal(str(t["pnl_percent"])) if t.get("pnl_percent") else None,  # DB column: pnl_percent
                entry_price=None,  # Not stored in DB
                holding_days=t.get("holding_days")
            )
            for t in trade_rows
        ]

        # equity_curve 파싱
        equity_curve = []
        if row.get("equity_curve"):
            curve_data = row["equity_curve"]
            if isinstance(curve_data, str):
                curve_data = json.loads(curve_data)
            equity_curve = [
                EquityCurvePoint(
                    date=p["date"],
                    equity=Decimal(str(p["equity"])),
                    cash=Decimal(str(p.get("cash", 0))),
                    positions_value=Decimal(str(p.get("positions_value", 0))),
                    drawdown_pct=Decimal(str(p.get("drawdown_pct", 0)))
                )
                for p in curve_data
            ]

        return BacktestResult(
            strategy_id=row["strategy_id"],
            strategy_name=row.get("strategy_name", ""),
            start_date=row["start_date"],
            end_date=row["end_date"],
            initial_capital=Decimal(str(row["initial_capital"])),
            final_value=Decimal(str(row["final_value"])),
            total_return=Decimal(str(row["total_return"])),
            cagr=Decimal(str(row["cagr"])),
            mdd=Decimal(str(row["mdd"])),
            sharpe_ratio=Decimal(str(row["sharpe_ratio"])) if row.get("sharpe_ratio") else None,
            sortino_ratio=Decimal(str(row["sortino_ratio"])) if row.get("sortino_ratio") else None,
            volatility=Decimal(str(row["volatility"])) if row.get("volatility") else None,
            win_rate=Decimal(str(row["win_rate"])) if row.get("win_rate") else None,
            total_trades=row["total_trades"],
            winning_trades=row["winning_trades"],
            losing_trades=row["losing_trades"],
            avg_win=Decimal(str(row["avg_win"])) if row.get("avg_win") else None,
            avg_loss=Decimal(str(row["avg_loss"])) if row.get("avg_loss") else None,
            largest_win=Decimal(str(row["largest_win"])) if row.get("largest_win") else None,
            largest_loss=Decimal(str(row["largest_loss"])) if row.get("largest_loss") else None,
            profit_factor=Decimal(str(row["profit_factor"])) if row.get("profit_factor") else None,
            avg_holding_days=Decimal(str(row["avg_holding_period"])) if row.get("avg_holding_period") else None,
            benchmark_return=Decimal(str(row["benchmark_return"])) if row.get("benchmark_return") else None,
            alpha=Decimal(str(row["alpha"])) if row.get("alpha") else None,
            beta=Decimal(str(row["beta"])) if row.get("beta") else None,
            trades=trades,
            equity_curve=equity_curve,
            exit_reason_counts=row.get("exit_reason_counts", {}),
            execution_time_seconds=row.get("execution_time_seconds", 0)
        )

    # ============================================================
    # Checkpoint Methods (증분 백테스트 지원)
    # ============================================================

    async def find_active_backtest(
        self,
        strategy_id: int,
        tickers: List[str],
        start_date: str
    ) -> Optional[Dict[str, Any]]:
        """
        동일 전략의 활성 백테스트 조회 (증분 업데이트 가능한 것)

        Args:
            strategy_id: 전략 ID
            tickers: 종목 목록 (현재는 검증 안 함, 향후 확장용)
            start_date: 시작일 (YYYY-MM-DD 문자열)

        Returns:
            기존 백테스트 정보 {id, end_date, last_checkpoint_date} 또는 None
        """
        return await asyncio.to_thread(
            self._find_active_backtest_sync, strategy_id, tickers, start_date
        )

    def _find_active_backtest_sync(
        self,
        strategy_id: int,
        tickers: List[str],
        start_date: str
    ) -> Optional[Dict[str, Any]]:
        """find_active_backtest의 동기 구현"""
        try:
            from datetime import datetime as dt
            start_date_parsed = dt.strptime(start_date, "%Y-%m-%d").date()

            with self._get_connection() as conn:
                with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
                    cursor.execute(
                        """
                        SELECT id, end_date, last_checkpoint_date, is_incremental
                        FROM backtest_results
                        WHERE strategy_id = %s
                          AND start_date = %s
                          AND status = 'COMPLETED'
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                        (strategy_id, start_date_parsed)
                    )
                    row = cursor.fetchone()
                    return dict(row) if row else None
        except Exception as e:
            logger.error(f"Failed to find active backtest: {e}")
            return None

    async def get_latest_checkpoint(
        self,
        backtest_id: int
    ) -> Optional[BacktestCheckpoint]:
        """
        백테스트의 가장 최근 체크포인트 조회

        Args:
            backtest_id: 백테스트 결과 ID

        Returns:
            BacktestCheckpoint 또는 None
        """
        return await asyncio.to_thread(self._get_latest_checkpoint_sync, backtest_id)

    def _get_latest_checkpoint_sync(
        self,
        backtest_id: int
    ) -> Optional[BacktestCheckpoint]:
        """get_latest_checkpoint의 동기 구현 (equity_curve는 backtest_results에서 조회)"""
        try:
            with self._get_connection() as conn:
                with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
                    cursor.execute(
                        """
                        SELECT id, backtest_id, checkpoint_date, cash, high_watermark,
                               positions, trade_count
                        FROM backtest_checkpoints
                        WHERE backtest_id = %s
                        ORDER BY checkpoint_date DESC
                        LIMIT 1
                        """,
                        (backtest_id,)
                    )
                    row = cursor.fetchone()

                    if not row:
                        return None

                    return BacktestCheckpoint(
                        id=row['id'],
                        backtest_id=row['backtest_id'],
                        checkpoint_date=row['checkpoint_date'],
                        cash=Decimal(str(row['cash'])),
                        high_watermark=Decimal(str(row['high_watermark'])),
                        positions=row['positions'] if row['positions'] else [],
                        trade_count=row['trade_count']
                    )
        except Exception as e:
            logger.error(f"Failed to get latest checkpoint: {e}")
            return None

    async def get_equity_curve_until(
        self,
        backtest_id: int,
        until_date: date
    ) -> List[Dict[str, Any]]:
        """
        backtest_results에서 특정 날짜까지의 equity_curve 조회

        Args:
            backtest_id: 백테스트 결과 ID
            until_date: 조회 종료일 (이 날짜 포함)

        Returns:
            equity_curve 리스트 (until_date까지만)
        """
        return await asyncio.to_thread(
            self._get_equity_curve_until_sync, backtest_id, until_date
        )

    def _get_equity_curve_until_sync(
        self,
        backtest_id: int,
        until_date: date
    ) -> List[Dict[str, Any]]:
        """get_equity_curve_until의 동기 구현"""
        try:
            with self._get_connection() as conn:
                with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
                    cursor.execute(
                        """
                        SELECT equity_curve
                        FROM backtest_results
                        WHERE id = %s
                        """,
                        (backtest_id,)
                    )
                    row = cursor.fetchone()

                    if not row or not row.get('equity_curve'):
                        return []

                    curve_data = row['equity_curve']
                    if isinstance(curve_data, str):
                        curve_data = json.loads(curve_data)

                    # until_date까지만 필터링
                    until_date_str = until_date.isoformat()
                    filtered_curve = [
                        point for point in curve_data
                        if point.get('date', '') <= until_date_str
                    ]

                    logger.debug(
                        f"Retrieved equity_curve until {until_date}: "
                        f"{len(filtered_curve)} points from {len(curve_data)} total"
                    )
                    return filtered_curve

        except Exception as e:
            logger.error(f"Failed to get equity_curve until {until_date}: {e}")
            return []

    async def save_checkpoint(self, checkpoint: BacktestCheckpoint) -> int:
        """
        체크포인트 저장 (upsert)

        Args:
            checkpoint: 체크포인트 데이터

        Returns:
            체크포인트 ID
        """
        return await asyncio.to_thread(self._save_checkpoint_sync, checkpoint)

    def _save_checkpoint_sync(self, checkpoint: BacktestCheckpoint) -> int:
        """save_checkpoint의 동기 구현 (equity_curve 제외 - backtest_results에서 조회)"""
        try:
            with self._get_connection() as conn:
                with conn.cursor() as cursor:
                    cursor.execute(
                        """
                        INSERT INTO backtest_checkpoints (
                            backtest_id, checkpoint_date, cash, high_watermark,
                            positions, trade_count, created_at
                        ) VALUES (%s, %s, %s, %s, %s, %s, %s)
                        ON CONFLICT (backtest_id, checkpoint_date)
                        DO UPDATE SET
                            cash = EXCLUDED.cash,
                            high_watermark = EXCLUDED.high_watermark,
                            positions = EXCLUDED.positions,
                            trade_count = EXCLUDED.trade_count,
                            created_at = EXCLUDED.created_at
                        RETURNING id
                        """,
                        (
                            checkpoint.backtest_id,
                            checkpoint.checkpoint_date,
                            float(checkpoint.cash),
                            float(checkpoint.high_watermark),
                            json.dumps(checkpoint.positions),
                            checkpoint.trade_count,
                            get_kst_now()
                        )
                    )
                    checkpoint_id = cursor.fetchone()[0]
                    conn.commit()

                    logger.info(
                        f"Saved checkpoint: backtest_id={checkpoint.backtest_id}, "
                        f"date={checkpoint.checkpoint_date}"
                    )
                    return checkpoint_id
        except Exception as e:
            logger.error(f"Failed to save checkpoint: {e}")
            raise

    async def update_result_incremental(
        self,
        backtest_id: int,
        result: BacktestResult,
        new_trades: List[BacktestTrade],
        request_id: Optional[str] = None
    ) -> None:
        """
        기존 백테스트 결과를 증분 업데이트

        Args:
            backtest_id: 기존 백테스트 ID
            result: 업데이트된 전체 결과 (지표 재계산)
            new_trades: 새로 추가된 거래만
            request_id: 요청 ID (Kafka 메시지 추적용)
        """
        await asyncio.to_thread(
            self._update_result_incremental_sync,
            backtest_id, result, new_trades, request_id
        )

    def _update_result_incremental_sync(
        self,
        backtest_id: int,
        result: BacktestResult,
        new_trades: List[BacktestTrade],
        request_id: Optional[str] = None
    ) -> None:
        """update_result_incremental의 동기 구현"""
        try:
            new_end_date = result.end_date

            with self._get_connection() as conn:
                with conn.cursor() as cursor:
                    # 1. 결과 지표 업데이트
                    cursor.execute(
                        """
                        UPDATE backtest_results SET
                            end_date = %s,
                            final_value = %s,
                            total_return = %s,
                            cagr = %s,
                            mdd = %s,
                            sharpe_ratio = %s,
                            sortino_ratio = %s,
                            volatility = %s,
                            total_trades = %s,
                            winning_trades = %s,
                            losing_trades = %s,
                            win_rate = %s,
                            avg_win = %s,
                            avg_loss = %s,
                            equity_curve = %s,
                            is_incremental = TRUE,
                            last_checkpoint_date = %s,
                            completed_at = %s
                        WHERE id = %s
                        """,
                        (
                            new_end_date,
                            float(result.final_value),
                            float(result.total_return),
                            float(result.cagr),
                            float(result.mdd),
                            float(result.sharpe_ratio) if result.sharpe_ratio else None,
                            float(result.sortino_ratio) if result.sortino_ratio else None,
                            float(result.volatility) if result.volatility else None,
                            result.total_trades,
                            result.winning_trades,
                            result.losing_trades,
                            float(result.win_rate) if result.win_rate else None,
                            float(result.avg_win) if result.avg_win else None,
                            float(result.avg_loss) if result.avg_loss else None,
                            json.dumps([
                                {"date": p.date.isoformat(), "equity": float(p.equity)}
                                for p in result.equity_curve
                            ]) if result.equity_curve else None,
                            new_end_date,
                            get_kst_now(),
                            backtest_id
                        )
                    )

                    # 2. 새 거래만 추가
                    if new_trades:
                        self._insert_trades(cursor, backtest_id, new_trades)

                    conn.commit()

                    logger.info(
                        f"Updated backtest {backtest_id} incrementally: "
                        f"end_date={new_end_date}, new_trades={len(new_trades)}, "
                        f"request_id={request_id}"
                    )
        except Exception as e:
            logger.error(f"Failed to update backtest incrementally: {e}")
            raise
