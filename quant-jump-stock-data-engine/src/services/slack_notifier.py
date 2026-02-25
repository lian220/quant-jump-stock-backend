"""
Slack 알림 서비스

Bot Token 우선, Webhook fallback.
Bot Token 사용 시 스레드 답글(thread_ts) 지원.
"""

import requests
import logging
import threading
from datetime import datetime
from pytz import timezone
from core.config import settings
from typing import Optional, Dict, List

from adapter.output.slack.bot_client import SlackBotClient

KST = timezone('Asia/Seoul')
EST = timezone('America/New_York')

logger = logging.getLogger(__name__)

# Bot Client 싱글톤 (thread-safe)
_bot_client: Optional[SlackBotClient] = None
_bot_client_lock = threading.Lock()


def _get_bot_client() -> SlackBotClient:
    global _bot_client
    if _bot_client is None:
        with _bot_client_lock:
            if _bot_client is None:
                _bot_client = SlackBotClient(bot_token=getattr(settings, 'SLACK_BOT_TOKEN', ''))
    return _bot_client


class SlackNotifier:
    """Slack 알림 서비스 - Bot Token (스레드 답글) + Webhook fallback"""

    @staticmethod
    def _post_to_webhook(url: str, text: str, attachments: list = None, blocks: list = None):
        """Webhook URL로 메시지 POST (fallback)"""
        if not url:
            return
        if not getattr(settings, 'SLACK_ENABLED', True):
            logger.info("Slack 비활성화 상태 (SLACK_ENABLED=false)")
            return
        try:
            payload = {"text": text}
            if attachments:
                payload["attachments"] = attachments
            if blocks:
                payload["blocks"] = blocks
            response = requests.post(url, json=payload, timeout=10)
            response.raise_for_status()
            logger.debug("Slack Webhook 알림 발송 완료")
        except Exception as e:
            logger.error(f"Slack Webhook 알림 발송 실패: {e}")

    @staticmethod
    def _post_message(
        channel: str,
        webhook_url: str,
        text: str,
        attachments: list = None,
        blocks: list = None,
        thread_ts: Optional[str] = None,
    ) -> Optional[str]:
        """
        Bot Token 우선, Webhook fallback으로 메시지 발송.

        Returns:
            Bot Token 사용 시 메시지 ts (thread_ts로 사용 가능). Webhook이면 None.
        """
        if not getattr(settings, 'SLACK_ENABLED', True):
            logger.info("Slack 비활성화 상태 (SLACK_ENABLED=false)")
            return None

        bot = _get_bot_client()
        if bot.is_available and channel:
            ts = bot.post_message(
                channel=channel,
                text=text,
                blocks=blocks,
                attachments=attachments,
                thread_ts=thread_ts,
            )
            if ts is not None:
                return ts
            # 봇 실패 시 Webhook fallback (thread 답글 불가)
            logger.warning(f"Bot post 실패 (channel={channel}), Webhook fallback으로 전송 (thread_ts 미지원)")

        # Webhook fallback (thread_ts 미지원)
        SlackNotifier._post_to_webhook(webhook_url, text, attachments=attachments, blocks=blocks)
        return None

    @staticmethod
    def _get_current_time() -> str:
        now_kst = datetime.now(KST)
        now_est = datetime.now(EST)
        return f"{now_kst.strftime('%Y-%m-%d %H:%M KST')} / {now_est.strftime('%H:%M EST')}"

    @staticmethod
    def _get_scheduler_channel() -> str:
        return getattr(settings, 'SLACK_CHANNEL_SCHEDULER', '')

    @staticmethod
    def _get_error_channel() -> str:
        return getattr(settings, 'SLACK_CHANNEL_ERROR', '')

    @staticmethod
    def _get_analysis_channel() -> str:
        return getattr(settings, 'SLACK_CHANNEL_ANALYSIS', '')

    @staticmethod
    def _get_scheduler_webhook() -> str:
        return getattr(settings, 'SLACK_WEBHOOK_URL_SCHEDULER', '') or settings.SLACK_WEBHOOK_URL

    @staticmethod
    def _get_error_webhook() -> str:
        return getattr(settings, 'SLACK_WEBHOOK_URL_ERROR', '') or settings.SLACK_WEBHOOK_URL

    @staticmethod
    def _get_analysis_webhook() -> str:
        return getattr(settings, 'SLACK_WEBHOOK_URL_ANALYSIS', '') or settings.SLACK_WEBHOOK_URL

    # ============================================================
    # 경제 데이터 수집 알림
    # ============================================================

    @staticmethod
    def notify_economic_data_collection_start(
        request_id: str,
        source: str = "pubsub",
        parent_thread_ts: Optional[str] = None,
    ) -> Optional[str]:
        """
        경제 데이터 수집 시작 알림.

        Returns:
            thread_ts (Bot Token 사용 시). 이후 답글에 사용.
        """
        text = "🔄 경제 데이터 수집 시작"
        attachments = [
            {
                "color": "0099cc",
                "title": "데이터 수집 진행 중",
                "text": "경제 데이터 수집이 시작되었습니다.",
                "fields": [
                    {"title": "Request ID", "value": request_id, "short": True},
                    {"title": "Source", "value": source, "short": True},
                    {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": True},
                    {"title": "Status", "value": "🔄 In Progress", "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        return SlackNotifier._post_message(
            channel=SlackNotifier._get_scheduler_channel(),
            webhook_url=SlackNotifier._get_scheduler_webhook(),
            text=text,
            attachments=attachments,
            thread_ts=parent_thread_ts,
        )

    @staticmethod
    def notify_economic_data_collection_success(
        request_id: str,
        data_summary: dict = None,
        thread_ts: Optional[str] = None,
    ):
        """경제 데이터 수집 완료 알림 (thread reply)"""
        if data_summary is None:
            data_summary = {}

        fred_count = data_summary.get("fred_collected", 0)
        yahoo_count = data_summary.get("yahoo_collected", 0)
        total_count = data_summary.get("total_indicators", fred_count + yahoo_count)
        stocks_count = data_summary.get("stocks_collected", 0)
        duration = data_summary.get("duration", "N/A")

        text = "✅ 경제 데이터 수집 완료"
        attachments = [
            {
                "color": "28a745",
                "title": "📊 수집 결과 요약",
                "text": f"총 {total_count}개 지표, {stocks_count}개 종목 수집 완료 (FRED: {fred_count}, Yahoo: {yahoo_count})",
                "fields": [
                    {"title": "Request ID", "value": request_id, "short": True},
                    {"title": "소요 시간", "value": duration, "short": True},
                    {"title": "FRED 지표", "value": f"{fred_count}개", "short": True},
                    {"title": "Yahoo Finance", "value": f"{yahoo_count}개", "short": True},
                    {"title": "개별 종목", "value": f"{stocks_count}개", "short": True},
                    {"title": "완료 시각", "value": datetime.now(KST).strftime("%Y-%m-%d %H:%M:%S"), "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        SlackNotifier._post_message(
            channel=SlackNotifier._get_scheduler_channel(),
            webhook_url=SlackNotifier._get_scheduler_webhook(),
            text=text,
            attachments=attachments,
            thread_ts=thread_ts,
        )

    @staticmethod
    def notify_economic_data_collection_error(
        request_id: str,
        error: str,
        thread_ts: Optional[str] = None,
    ):
        """경제 데이터 수집 오류 알림 (thread reply)"""
        text = "⚠️ 경제 데이터 수집 오류"
        attachments = [
            {
                "color": "dc3545",
                "title": "경제 데이터 수집 실패",
                "text": "경제 데이터 수집 중 오류가 발생했습니다.",
                "fields": [
                    {"title": "Request ID", "value": request_id, "short": True},
                    {"title": "Error", "value": error[:200], "short": False},
                    {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": True},
                    {"title": "Status", "value": "❌ Failed", "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        SlackNotifier._post_message(
            channel=SlackNotifier._get_error_channel(),
            webhook_url=SlackNotifier._get_error_webhook(),
            text=text,
            attachments=attachments,
            thread_ts=thread_ts,
        )

    # ============================================================
    # 종합 분석 리포트
    # ============================================================

    @staticmethod
    def notify_comprehensive_report(report: Dict, thread_ts: Optional[str] = None):
        """Quantiq 종합 분석 리포트 발송 (thread reply 지원)"""
        analysis_channel = SlackNotifier._get_analysis_channel()
        webhook_url = SlackNotifier._get_analysis_webhook()
        if not analysis_channel and not webhook_url:
            logger.warning("SLACK_CHANNEL_ANALYSIS / SLACK_WEBHOOK_URL_ANALYSIS 미설정, 추천 알림 생략")
            return

        current_time = SlackNotifier._get_current_time()
        total = report.get("total_analyzed", 0)
        candidates = report.get("buy_candidates", [])
        candidate_count = len(candidates)
        summary = report.get("summary", {})
        breakdown = report.get("breakdown", {})
        analysis_date = report.get("analysis_date", "N/A")

        avg_composite = summary.get("avg_composite_score", 0)
        avg_rise = summary.get("avg_rise_probability", 0)

        blocks = [
            {
                "type": "header",
                "text": {"type": "plain_text", "text": "🎯 Quantiq 종합 분석 완료", "emoji": True}
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"기술적 분석, AI 예측, 감정 분석을 종합한 투자 추천이 완료되었습니다. ({analysis_date})"
                }
            },
            {
                "type": "section",
                "fields": [
                    {"type": "mrkdwn", "text": f"*총 분석 종목*\n{total}개"},
                    {"type": "mrkdwn", "text": f"*최종 추천 종목*\n{candidate_count}개"},
                    {"type": "mrkdwn", "text": f"*평균 종합 점수*\n{avg_composite:.2f}"},
                    {"type": "mrkdwn", "text": f"*평균 상승 확률*\n{avg_rise:.1f}%"},
                ]
            },
            {"type": "divider"},
        ]

        tech_info = breakdown.get("technical", {})
        ai_info = breakdown.get("ai_prediction", {})
        sent_info = breakdown.get("sentiment", {})

        def _ticker_summary(tickers: list, count: int) -> str:
            if not tickers:
                return "해당 없음"
            shown = tickers[:3]
            names = ", ".join(shown)
            extra = count - len(shown)
            return f"{names}" + (f" 외 {extra}개" if extra > 0 else "")

        blocks.append({
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": (
                    "*세부 분석 결과*\n\n"
                    f"📊 *기술적 지표 분석* ({tech_info.get('count', 0)}개)\n"
                    f"└ {_ticker_summary(tech_info.get('tickers', []), tech_info.get('count', 0))}\n"
                    f"└ 골든크로스, RSI<70, MACD매수신호\n\n"
                    f"🤖 *AI 주가 예측* ({ai_info.get('count', 0)}개)\n"
                    f"└ {_ticker_summary(ai_info.get('tickers', []), ai_info.get('count', 0))}\n"
                    f"└ 평균 상승률: {ai_info.get('avg_rise', 0):.1f}%\n\n"
                    f"💬 *뉴스 감정 분석* ({sent_info.get('count', 0)}개)\n"
                    f"└ {_ticker_summary(sent_info.get('tickers', []), sent_info.get('count', 0))}\n"
                    f"└ 감정 점수 ≥ 0.15 (긍정)"
                )
            }
        })
        blocks.append({"type": "divider"})

        if candidate_count > 0:
            blocks.append({
                "type": "section",
                "text": {"type": "mrkdwn", "text": f"*🏆 TOP {min(candidate_count, 5)} 추천 종목*"}
            })

            for i, rec in enumerate(candidates[:5], 1):
                indicators = rec.get("technical_indicators") or rec
                scores = rec.get("scores", {})
                ticker = rec.get("ticker", "N/A")
                stock_name = rec.get("stock_name", ticker)
                composite = scores.get("composite_score", 0)

                ai_pred = rec.get("ai_prediction", {})
                rise_prob = ai_pred.get("rise_probability", 0)
                sentiment = rec.get("sentiment_score", 0)

                signals = []
                if indicators.get("golden_cross"):
                    signals.append("골든크로스")
                if indicators.get("macd_buy_signal"):
                    signals.append("MACD매수")
                rsi_val = indicators.get("rsi", 100)
                if rsi_val < 70:
                    signals.append(f"RSI({rsi_val:.0f})")
                signal_text = ", ".join(signals) if signals else "없음"

                rise_str = f"{rise_prob:+.1f}%" if rise_prob != 0 else "N/A"
                sent_str = f"{sentiment:.2f}" if sentiment > 0 else "N/A"

                blocks.append({
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": (
                            f"*{i}. {stock_name}* (`{ticker}`)\n"
                            f"• 종합점수: `{composite:.2f}` | 상승확률: `{rise_str}` | 감정: `{sent_str}`\n"
                            f"• 기술신호: {signal_text}"
                        )
                    }
                })
        else:
            blocks.append({
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": "ℹ️ *추천 종목 없음* - 현재 매수 조건을 충족하는 종목이 없습니다."
                }
            })

        # 아깝게 탈락한 종목 TOP-N (upstream에서 이미 개수 제한됨)
        near_miss = report.get("near_miss_candidates", [])
        if near_miss:
            top_n = len(near_miss)
            blocks.append({"type": "divider"})
            blocks.append({
                "type": "section",
                "text": {"type": "mrkdwn", "text": f"*📊 아깝게 탈락한 종목 TOP{top_n}*\n_조건이 거의 충족되어 다음 기회를 노릴 종목_"}
            })
            for i, nm in enumerate(near_miss, 1):
                nm_indicators = nm.get("technical_indicators") or nm
                nm_scores = nm.get("scores", {})
                nm_ticker = nm.get("ticker", "N/A")
                nm_name = nm.get("stock_name", nm_ticker)
                nm_composite = nm_scores.get("composite_score", 0)

                missing = nm.get("missing_conditions", [])
                met = nm.get("met_conditions", [])

                met_text = ", ".join(met) if met else "없음"
                missing_text = ", ".join(missing) if missing else "없음"

                blocks.append({
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": (
                            f"*{i}. {nm_name}* (`{nm_ticker}`) — 종합점수: `{nm_composite:.2f}`\n"
                            f"• ✅ 충족: {met_text}\n"
                            f"• ❌ 미충족: {missing_text}"
                        )
                    }
                })

        blocks.extend([
            {"type": "divider"},
            {
                "type": "context",
                "elements": [
                    {"type": "mrkdwn", "text": (
                        f"⏰ {current_time} | "
                        f"기준: composite = 0.3×AI + 0.4×기술 + 0.3×감정 | "
                        f"Quantiq Data Engine"
                    )}
                ]
            }
        ])

        fallback_text = (
            f"🎯 Quantiq 종합 분석 완료: {total}개 분석, "
            f"{candidate_count}개 추천, 평균 종합 {avg_composite:.2f}"
        )

        SlackNotifier._post_message(
            channel=analysis_channel,
            webhook_url=webhook_url,
            text=fallback_text,
            blocks=blocks,
            thread_ts=thread_ts,
        )

    # ============================================================
    # 뉴스 수집
    # ============================================================

    @staticmethod
    def notify_news_collection(result: Dict):
        """뉴스 수집 결과 알림"""
        channel = getattr(settings, 'SLACK_CHANNEL_ANALYSIS', '')
        webhook_url = getattr(settings, 'SLACK_WEBHOOK_URL_NEWS', '')
        if not channel and not webhook_url:
            logger.debug("SLACK_CHANNEL_ANALYSIS/SLACK_WEBHOOK_URL_NEWS 미설정, 뉴스 알림 생략")
            return

        source = result.get("source", "unknown")
        count = result.get("collected_count", 0)
        if count == 0:
            return

        current_time = SlackNotifier._get_current_time()
        blocks = [
            {
                "type": "header",
                "text": {"type": "plain_text", "text": f"📰 뉴스 수집 완료 ({source})", "emoji": True}
            },
            {
                "type": "section",
                "fields": [
                    {"type": "mrkdwn", "text": f"*수집 건수*\n{count}건"},
                    {"type": "mrkdwn", "text": f"*소스*\n{source}"},
                ]
            },
            {"type": "divider"},
            {
                "type": "context",
                "elements": [
                    {"type": "mrkdwn", "text": f"⏰ {current_time} | Quantiq Data Engine"}
                ]
            }
        ]

        fallback_text = f"📰 뉴스 수집 완료: {source}에서 {count}건 저장"
        SlackNotifier._post_message(
            channel=channel,
            webhook_url=webhook_url,
            text=fallback_text,
            blocks=blocks,
        )

    # ============================================================
    # FRED / Yahoo Finance 에러 알림
    # ============================================================

    @staticmethod
    def notify_fred_api_error(indicator_code: str, error: str):
        text = "⚠️ FRED API 오류"
        attachments = [
            {
                "color": "ffc107",
                "title": "FRED API 호출 실패",
                "text": f"경제 지표 {indicator_code} 수집에 실패했습니다.",
                "fields": [
                    {"title": "Indicator", "value": indicator_code, "short": True},
                    {"title": "Error", "value": error[:200], "short": True},
                    {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]
        SlackNotifier._post_message(
            channel=SlackNotifier._get_error_channel(),
            webhook_url=SlackNotifier._get_error_webhook(),
            text=text,
            attachments=attachments,
        )

    @staticmethod
    def notify_yahoo_finance_error(ticker: str, error: str):
        text = "⚠️ Yahoo Finance 오류"
        attachments = [
            {
                "color": "ffc107",
                "title": "Yahoo Finance 호출 실패",
                "text": f"시장 지표 {ticker} 수집에 실패했습니다.",
                "fields": [
                    {"title": "Ticker", "value": ticker, "short": True},
                    {"title": "Error", "value": error[:200], "short": True},
                    {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]
        SlackNotifier._post_message(
            channel=SlackNotifier._get_error_channel(),
            webhook_url=SlackNotifier._get_error_webhook(),
            text=text,
            attachments=attachments,
        )

    @staticmethod
    def send_thread_message(text: str, thread_ts: str, channel: Optional[str] = None):
        """스레드 답글 전송"""
        if not thread_ts:
            return

        bot = _get_bot_client()
        if bot.is_available:
            target_channel = channel or SlackNotifier._get_scheduler_channel()
            if target_channel:
                bot.post_message(channel=target_channel, text=text, thread_ts=thread_ts)
                return

        logger.debug(f"스레드 답글 스킵 (Bot Token/채널 미설정): {text[:50]}...")

    @staticmethod
    def notify_backtest_completed(
        strategy_id: int,
        strategy_name: str,
        request_id: str,
        backtest_type: str,
        start_date: str,
        end_date: str,
        total_return_pct: float,
        cagr: float,
        mdd: float,
        sharpe_ratio: float,
        total_trades: int,
        win_rate: float,
        execution_time: float
    ):
        """백테스트 완료 알림 (Scheduler Webhook)"""
        text = f"✅ 백테스트 완료: {strategy_name}"
        
        return_emoji = "🟢" if total_return_pct > 0 else "🔴"
        sharpe_emoji = "⭐" if sharpe_ratio and sharpe_ratio > 1.0 else "📊"
        
        attachments = [
            {
                "color": "28a745" if total_return_pct > 0 else "dc3545",
                "title": f"{return_emoji} {strategy_name} (전략 #{strategy_id})",
                "text": f"백테스트 타입: {backtest_type} | 기간: {start_date} ~ {end_date}",
                "fields": [
                    {"title": "Request ID", "value": request_id[:8] + "...", "short": True},
                    {"title": "소요 시간", "value": f"{execution_time:.2f}초", "short": True},
                    {"title": f"{return_emoji} 총 수익률", "value": f"{total_return_pct:+.2f}%", "short": True},
                    {"title": "📈 연평균 수익률 (CAGR)", "value": f"{cagr:+.2f}%", "short": True},
                    {"title": "📉 최대 낙폭 (MDD)", "value": f"{mdd:.2f}%", "short": True},
                    {"title": f"{sharpe_emoji} Sharpe Ratio", "value": f"{sharpe_ratio:.2f}" if sharpe_ratio else "N/A", "short": True},
                    {"title": "🔄 총 거래", "value": f"{total_trades}건", "short": True},
                    {"title": "🎯 승률", "value": f"{win_rate:.1f}%" if win_rate else "N/A", "short": True},
                ],
                "footer": "Quantiq Data Engine · Backtest",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]
        
        SlackNotifier._post_to_webhook(SlackNotifier._get_scheduler_webhook(), text, attachments=attachments)

    @staticmethod
    def notify_job_submitted(thread_ts: str, job_name: str, mode: str, elapsed: str):
        """Vertex AI Job 제출 완료 Slack 스레드 답글"""
        SlackNotifier._post_message(
            channel=SlackNotifier._get_scheduler_channel(),
            webhook_url=SlackNotifier._get_scheduler_webhook(),
            text=f"📤 Vertex AI Job 제출 완료 ({mode})",
            attachments=[{
                "color": "0099cc",
                "title": f"Job 제출 완료 - {mode}",
                "fields": [
                    {"title": "Job Name", "value": job_name or "N/A", "short": True},
                    {"title": "소요 시간", "value": elapsed, "short": True},
                    {"title": "Status", "value": "⏳ GPU 할당 대기 중...", "short": True},
                ]
            }],
            thread_ts=thread_ts,
        )

    @staticmethod
    def notify_buy_candidates(total_analyzed: int, buy_candidates: List, buy_criteria):
        """매수 후보 알림 (종합 리포트로 통합됨)"""
        logger.debug(
            f"매수 후보 분석 완료: {total_analyzed}개 분석, "
            f"{len(buy_candidates)}개 후보 (notify_comprehensive_report에서 전송됨)"
        )
