"""
Slack 알림 서비스

Bot Token 우선, Webhook fallback.
Bot Token 사용 시 스레드 답글(thread_ts) 지원.
"""

import re
import time
import requests
import logging
import threading
from datetime import datetime
from pytz import timezone
from core.config import settings
from typing import Optional, Dict, List

from adapter.output.slack.bot_client import SlackBotClient
from config.settings import get_settings
from domain.recommendation.composite_grade import RecommendationGrade
from domain.recommendation.score import Score
from domain.recommendation.scoring_policy import ScoringPolicy

KST = timezone('Asia/Seoul')
EST = timezone('America/New_York')

logger = logging.getLogger(__name__)


# ─── 점수 표시 (분모 ScoringPolicy.composite_max SSoT) ─────────────────────
def _format_score_for_slack(score: Score) -> str:
    """Score → Slack 표시 (분모를 score.composite_max 에서 동적으로).

    하드코딩 `7.4` 폐기. spec 변경 시 표시도 자동 추종.
    현행 spec 의 composite_max=7.4 → "x.xx/7.40" 형태 그대로.
    """
    return (
        f"종합점수: `{float(score.composite_score):.2f}/{float(score.composite_max):.2f}` "
        f"(달성도 {float(score.confidence):.0%})"
    )

# Bot Client 싱글톤 (thread-safe)
_bot_client: Optional[SlackBotClient] = None
_bot_client_lock = threading.Lock()

# 에러 알림 폭주 방지: 동일 (topic, error_type) 5분 쿨다운
_ERROR_NOTIFY_COOLDOWN_SEC = 300
_error_notify_history: Dict[str, float] = {}
_error_notify_lock = threading.Lock()

# 민감정보 마스킹 — 2026-05-14 docker compose / MongoDB URI 노출 사고 후속 방어선
# 커버 패턴:
#   1. KEY=VALUE / KEY: VALUE
#   2. JSON  "key": "value"
#   3. URI   scheme://user:password@host
_SECRET_KV_PATTERN = re.compile(
    r'(?i)\b(password|passwd|secret|token|api[-_]?key|bearer|authorization|'
    r'mongodb_uri|db_password|gcp_credentials)\s*[=:]\s*\S+',
)
_SECRET_JSON_PATTERN = re.compile(
    r'(?i)"(password|passwd|secret|token|api[-_]?key|bearer|authorization|'
    r'mongodb_uri|db_password|gcp_credentials)"\s*:\s*"[^"]*"',
)
_URI_CREDENTIAL_PATTERN = re.compile(
    r'([a-zA-Z][a-zA-Z0-9+\-.]*://[^:/?#@\s]+):[^@\s]+@'
)
# "Bearer <token>" 형태 (HTTP Authorization 헤더 등)
_BEARER_TOKEN_PATTERN = re.compile(r'(?i)\b(Bearer)\s+[A-Za-z0-9._\-]+')


def _mask_secrets(text: str) -> str:
    """KIS/Atlas/Auth 응답 등에 포함될 수 있는 시크릿 평문을 마스킹."""
    if not text:
        return text
    text = _SECRET_KV_PATTERN.sub(r'\1=***', text)
    text = _SECRET_JSON_PATTERN.sub(r'"\1":"***"', text)
    text = _URI_CREDENTIAL_PATTERN.sub(r'\1:***@', text)
    text = _BEARER_TOKEN_PATTERN.sub(r'\1 ***', text)
    return text


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

        logger.info(f"Slack 발송 시도: channel={channel}, webhook={'설정됨' if webhook_url else '미설정'}")

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
        # 2026-05-19: 에러 알림은 webhook 직행 (_post_error). 이 함수는 deprecated.
        return getattr(settings, 'SLACK_CHANNEL_ERROR', '')

    @staticmethod
    def _post_error(text: str, attachments: list = None, blocks: list = None) -> None:
        """에러 알림은 항상 SLACK_WEBHOOK_URL_ERROR 로 직행 (Bot Token / channel ID 우회).

        배경 (2026-05-19):
          SLACK_CHANNEL_SCHEDULER / _ANALYSIS / _ERROR / _TRADING 가 동일 채널 ID 로 설정되어
          Bot Token 사용 시 에러 알림이 스케줄러 채널로 라우팅되는 버그. 에러는 thread reply
          가치 낮고 webhook URL 이 정확한 에러 채널을 가지므로 webhook 직행이 단순/안전.
        """
        SlackNotifier._post_to_webhook(
            SlackNotifier._get_error_webhook(),
            text,
            attachments=attachments,
            blocks=blocks,
        )

    @staticmethod
    def _get_analysis_channel() -> str:
        return getattr(settings, 'SLACK_CHANNEL_ANALYSIS', '')

    @staticmethod
    def _get_scheduler_webhook() -> str:
        return getattr(settings, 'SLACK_WEBHOOK_URL_SCHEDULER', '') or settings.SLACK_WEBHOOK_URL

    @staticmethod
    def _get_error_webhook() -> str:
        return getattr(settings, 'SLACK_WEBHOOK_URL_ERROR', '')

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

        SlackNotifier._post_error(text=text, attachments=attachments)

    # ============================================================
    # 데이터 freshness 알람 (2026-05-18 사고 후속 — 매일 23:00 KST 자동 체크)
    # ============================================================

    @staticmethod
    def notify_freshness_alert(
        check_date: str,
        stock_predictions_count: int,
        stock_recommendations_count: int,
        threshold: int,
    ):
        """
        데이터 freshness 알람 — 매일 23:00 KST Cloud Scheduler 트리거.

        stock_predictions 또는 stock_recommendations 가 threshold 미만이면
        다음날 추천 사고 사전 차단을 위해 운영자 알림.

        배경:
          - 2026-05-14 Vertex AI Job SUCCEEDED 인데 stock_predictions 0건
          - 운영자 미인지 → 5/15 23:20 KST "추천 0개" 사용자 노출
          - 사전 freshness probe 로 동일 사고 차단
        """
        text = "🚨 데이터 freshness 부족 — 다음 추천 사고 위험"
        attachments = [
            {
                "color": "#dc3545",
                "title": f"{check_date} 데이터 freshness 알람",
                "text": (
                    f"임계 ({threshold}건) 미만 컬렉션 감지. "
                    f"다음 cron 발화 시 추천 0개 위험 — 즉시 파이프라인 점검 필요."
                ),
                "fields": [
                    {"title": "분석일", "value": check_date, "short": True},
                    {"title": "임계", "value": f"{threshold}건", "short": True},
                    {"title": "stock_predictions", "value": f"{stock_predictions_count}건", "short": True},
                    {"title": "stock_recommendations", "value": f"{stock_recommendations_count}건", "short": True},
                    {"title": "조치", "value": "Vertex AI Job 상태 + technical_analysis 실행 점검", "short": False},
                ],
                "footer": "Quantiq Data Engine — freshness probe",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]
        SlackNotifier._post_error(text=text, attachments=attachments)

    # ============================================================
    # Pre-flight 데이터 결손 알림 (2026-05-15 사고 후속)
    # ============================================================

    @staticmethod
    def notify_recommendation_data_gap(
        analysis_date: str,
        total_analyzed: int,
        ai_prediction_count: int,
        sentiment_count: int,
        tech_count: int,
    ):
        """
        추천 산출 입력 데이터 결손 시 운영자 채널 알림.

        사용자 분석 채널 대신 에러 채널로 발송 — "0개 추천" 평문 노출 차단.
        TALEB 권고: 0개 추천 → 사용자 매수 보류 → 종목 급등 시 책임론 위험.
        MEADOWS leverage: 메시지 정책이 아니라 pre-flight gate가 진짜 개입점.
        """
        text = "⚠️ 추천 산출 보류 — 입력 데이터 결손"
        attachments = [
            {
                "color": "#ffc107",
                "title": f"분석일 {analysis_date} 추천 송출 보류",
                "text": (
                    "AI 예측 또는 분석 입력이 결손되어 사용자 채널 송출을 차단했습니다. "
                    "데이터 파이프라인 복구 후 재처리 필요."
                ),
                "fields": [
                    {"title": "분석일", "value": analysis_date, "short": True},
                    {"title": "총 분석", "value": f"{total_analyzed}개", "short": True},
                    {"title": "AI 예측", "value": f"{ai_prediction_count}개", "short": True},
                    {"title": "감정 분석", "value": f"{sentiment_count}개", "short": True},
                    {"title": "기술 분석", "value": f"{tech_count}개", "short": True},
                    {"title": "조치", "value": "파이프라인 점검 + 데이터 백필", "short": False},
                ],
                "footer": "Quantiq Data Engine — pre-flight gate",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]
        SlackNotifier._post_error(text=text, attachments=attachments)

    # ============================================================
    # VIX 거시 gate 알림 (PR 5, 2026-05-22)
    # ============================================================

    @staticmethod
    def notify_vix_gate_triggered(
        analysis_date: str,
        vix_value: Optional[float],
        threshold: Optional[float],
    ):
        """VIX 거시 변동성 임계 초과 시 양쪽 채널 알림 (운영자 + 사용자) + 송출 차단.

        TALEB asymmetric risk: 변동성 폭증 시 추천 신호의 노이즈 비율 급증.
                                False positive (잘못된 추천 → 사용자 손실) 비용 비대칭.
        MEADOWS systems: 산식 외부 macro gate. 산식 자체는 그대로 유지.
        DOUMONT clarity: 사용자가 "시스템 다운" 으로 오인하지 않도록 분석 채널에도 사유 공지.
        """
        # 1. 운영자 채널 (상세 — 디버깅용)
        ops_text = "⚠️ 추천 송출 보류 — 시장 변동성 임계 초과"
        ops_attachments = [
            {
                "color": "#ff6b6b",
                "title": f"분석일 {analysis_date} VIX 거시 gate 발동",
                "text": (
                    "VIX (시장 변동성 지수) 가 임계값 초과로 추천 송출을 차단했습니다. "
                    "변동성 폭증 시 신호 노이즈로 인한 사용자 손실 위험 회피 목적."
                ),
                "fields": [
                    {"title": "분석일", "value": analysis_date, "short": True},
                    {"title": "VIX", "value": f"{vix_value:.2f}" if vix_value is not None else "결손", "short": True},
                    {"title": "임계값", "value": f"{threshold:.2f}" if threshold is not None else "—", "short": True},
                    {"title": "정책", "value": "scoring_spec.yaml > macro_gates.vix", "short": True},
                    {"title": "롤백", "value": "yaml flip: enabled: false 또는 threshold 상향", "short": False},
                ],
                "footer": "Quantiq Data Engine — PR 5 VIX macro gate",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]
        SlackNotifier._post_error(text=ops_text, attachments=ops_attachments)

        # 2. 사용자(분석) 채널 (간결 — 신뢰 유지) — review 시나리오 5
        user_text = "ℹ️ 오늘은 시장 변동성이 평소보다 높아 종목 추천을 일시 보류합니다."
        vix_display = f"{vix_value:.1f}" if vix_value is not None else "결손"
        user_attachments = [
            {
                "color": "#ffc107",
                "title": f"{analysis_date} 추천 보류 안내",
                "text": (
                    f"시장 변동성 지수(VIX)가 {vix_display}로 평소 범위를 벗어났습니다. "
                    f"변동성이 높은 날은 추천의 신뢰도가 낮아질 수 있어 자동으로 보류합니다.\n\n"
                    f"시스템은 정상 동작 중이며, 변동성이 안정화되면 추천이 재개됩니다."
                ),
                "footer": "Quantiq — 안전 우선 정책",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]
        analysis_webhook = SlackNotifier._get_analysis_webhook()
        analysis_channel = SlackNotifier._get_analysis_channel()
        SlackNotifier._post_message(
            channel=analysis_channel,
            webhook_url=analysis_webhook,
            text=user_text,
            attachments=user_attachments,
        )

    # ============================================================
    # Startup 결함 알림 (PR 2, 2026-05-21)
    # ============================================================

    @staticmethod
    def notify_spec_load_failed(spec_path: str, error: str, error_type: str):
        """ScoringPolicy.load_default() 실패 시 운영자 알림.

        Cloud Run lifespan/startup hook 에서 호출. 컨테이너는 어쨌든 fail-fast 로
        시작 안 되지만, 운영자가 Cloud Run console 로그를 보기 전 즉시 인지하도록
        에러 채널에 알림 발송. 메시지 전송 실패해도 startup 은 그대로 실패시킴.
        """
        text = "🚨 ScoringPolicy spec 로드 실패 — 컨테이너 startup 차단"
        attachments = [
            {
                "color": "#dc3545",
                "title": "scoring_spec.yaml 로드/검증 실패",
                "text": (
                    "Cloud Run 새 revision 의 startup 단계에서 spec 로드가 실패했습니다. "
                    "이전 revision 이 트래픽을 유지 — 사용자 영향 없음. "
                    "운영자 즉시 점검 필요."
                ),
                "fields": [
                    {"title": "Spec Path", "value": spec_path or "(unknown)", "short": False},
                    {"title": "Error Type", "value": error_type, "short": True},
                    {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": True},
                    {"title": "Error", "value": _mask_secrets((error or "(no message)")[:500]), "short": False},
                    {"title": "조치", "value": "scoring_spec.yaml 형식/invariant 검증 + 이미지 재배포", "short": False},
                ],
                "footer": "Quantiq Data Engine — startup gate",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]
        try:
            SlackNotifier._post_error(text=text, attachments=attachments)
        except Exception as send_err:
            # Slack 자체가 죽었어도 startup 실패는 그대로 propagate
            logger.warning("notify_spec_load_failed Slack 발송 실패 (무시): %s", send_err)

    # ============================================================
    # Pub/Sub 핸들러 공통 에러 알림
    # ============================================================

    @staticmethod
    def _should_notify_error(topic: str, error_type: str) -> bool:
        """
        동일 (topic, error_type) 조합에 대해 5분 내 1회만 알림 발송.

        Cloud Scheduler가 반복 발행하는 메시지에서 동일 버그가 매번 트리거되면
        Slack 채널이 같은 메시지로 채워지는 폭주를 방지.
        """
        key = f"{topic}::{error_type}"
        now = time.monotonic()
        with _error_notify_lock:
            last = _error_notify_history.get(key)
            if last is not None and (now - last) < _ERROR_NOTIFY_COOLDOWN_SEC:
                logger.info(
                    f"⏳ 에러 알림 쿨다운 중 (스킵): key={key} 남은={int(_ERROR_NOTIFY_COOLDOWN_SEC - (now - last))}s"
                )
                return False
            _error_notify_history[key] = now
            # 메모리 누수 방지: 만료된 항목 정리
            if len(_error_notify_history) > 100:
                cutoff = now - _ERROR_NOTIFY_COOLDOWN_SEC
                expired = [k for k, t in _error_notify_history.items() if t < cutoff]
                for k in expired:
                    _error_notify_history.pop(k, None)
        return True

    @staticmethod
    def notify_handler_error(
        topic: str,
        error: str,
        error_type: str,
        request_id: Optional[str] = None,
        retryable: bool = False,
    ):
        """
        Pub/Sub push 핸들러 공통 에러 알림.

        push_handler.py 의 catch-all 블록에서 호출되어 모든 핸들러 예외를
        에러 채널로 통합 전송. 알림 실패는 호출 측에서 흡수 (응답 영향 X).

        Args:
            topic: 핸들러 토픽 (dot notation, e.g. "vertex.ai.run.request")
            error: 에러 메시지 (앞 500자만 사용)
            error_type: 예외 클래스명 (e.g. "RuntimeError", "NonRetryableError")
            request_id: 메시지 추적용 ID (있으면)
            retryable: 재시도 여부 (False: ACK 처리됨)
        """
        # Rate-limit dedup: 동일 (topic, error_type) 조합 5분 쿨다운
        if not SlackNotifier._should_notify_error(topic, error_type):
            return

        # 민감정보 마스킹 (KIS/Atlas 에러 메시지에 토큰/비밀번호 누출 방지)
        safe_error = _mask_secrets(error[:500] if error else "(no message)")

        status_label = "🔁 Retryable" if retryable else "🛑 Non-retryable (ACK)"
        text = f"❌ Pub/Sub Handler Error: `{topic}`"
        attachments = [
            {
                "color": "#dc3545",
                "title": f"{error_type} in {topic}",
                "text": safe_error,
                "fields": [
                    {"title": "Topic", "value": topic, "short": True},
                    {"title": "Error Type", "value": error_type, "short": True},
                    {"title": "Request ID", "value": request_id or "n/a", "short": True},
                    {"title": "Status", "value": status_label, "short": True},
                    {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": False},
                ],
                "footer": "Quantiq Data Engine — push_handler",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        SlackNotifier._post_error(text=text, attachments=attachments)

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
        _settings = get_settings()
        rsi_threshold = _settings.recommendation.rsi_threshold

        avg_composite = summary.get("avg_composite_score", 0)
        avg_rise = summary.get("avg_rise_probability", 0)

        # Task 2.3: composite 분모는 ScoringPolicy.composite_max SSoT.
        _policy = ScoringPolicy.load_default()
        _composite_max = float(_policy.composite_max)
        _ax = _policy.axes
        _min_rec = float(_policy.min_composite_score)

        # PR 2: AI 예측 fallback 표시 (analysis_date 와 다른 날짜로 fallback 시)
        effective_prediction_date = report.get("effective_prediction_date")
        header_text = f"기술적 분석, AI 예측, 감정 분석을 종합한 투자 추천이 완료되었습니다. ({analysis_date})"
        if effective_prediction_date and effective_prediction_date != analysis_date:
            header_text += (
                f"\n\n⚠ AI 예측 데이터는 `{analysis_date}` 자료가 없어 "
                f"`{effective_prediction_date}` 기준값을 사용했습니다."
            )

        blocks = [
            {
                "type": "header",
                "text": {"type": "plain_text", "text": "🎯 Quantiq 종합 분석 완료", "emoji": True}
            },
            {
                "type": "section",
                "text": {"type": "mrkdwn", "text": header_text}
            },
            {
                "type": "section",
                "fields": [
                    {"type": "mrkdwn", "text": f"*총 분석 종목*\n{total}개"},
                    {"type": "mrkdwn", "text": f"*최종 추천 종목*\n{candidate_count}개"},
                    {"type": "mrkdwn", "text": f"*평균 종합 점수*\n{avg_composite:.2f} / {_composite_max:.2f}"},
                    {"type": "mrkdwn", "text": f"*평균 상승 확률*\n{avg_rise:.1f}%"},
                ]
            },
        ]

        # PR 3b: AI 음수 예측으로 차단된 종목 수 표시 (사용자가 추천 적은 이유 이해)
        ai_veto_count = report.get("ai_veto_count", 0)
        if ai_veto_count > 0:
            blocks.append({
                "type": "section",
                "text": {"type": "mrkdwn", "text": (
                    f"ℹ️ *AI 하락 예측 차단*: {ai_veto_count}개 종목이 AI 의 음수 예측으로 추천 대상에서 제외되었습니다."
                )}
            })

        blocks.append({"type": "divider"})

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
                    f"└ 골든크로스, RSI<{rsi_threshold:.0f}, MACD매수신호\n\n"
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
            # 등급별 요약
            grade_summary = report.get("grade_summary", {})
            grade_lines = []
            for g in RecommendationGrade:
                count = grade_summary.get(g.label, 0)
                if count > 0:
                    grade_lines.append(f"{g.emoji} {g.label}: {count}개")
            grade_text = " | ".join(grade_lines) if grade_lines else ""

            blocks.append({
                "type": "section",
                "text": {"type": "mrkdwn", "text": f"*🏆 추천 종목 현황*\n{grade_text}"}
            })

            for i, rec in enumerate(candidates[:5], 1):
                indicators = rec.get("technical_indicators") or rec
                scores = rec.get("scores", {})
                ticker = rec.get("ticker", "N/A")
                stock_name = rec.get("stock_name", ticker)
                grade = rec.get("grade")
                grade_emoji = grade.emoji if hasattr(grade, "emoji") else "⚪"
                grade_label = grade.label if hasattr(grade, "label") else str(grade)

                # Task 2.3: 종합점수 분모 ScoringPolicy.composite_max SSoT.
                # buy_criteria (filter_candidates / get_near_miss_candidates) 와 sync_service 둘 다
                # 후보 dict 의 top-level "score" 키로 Score 객체를 올린다 — 단일 lookup.
                # 둘 다 없으면 ScoringPolicy 직접 로드해 동적 분모 산출 (legacy defensive).
                score_obj: Optional[Score] = rec.get("score") or scores.get("score_obj")
                if score_obj is not None:
                    score_str = _format_score_for_slack(score_obj)
                else:
                    composite = scores.get("composite_score", 0)
                    confidence = scores.get("confidence", 0)
                    score_str = (
                        f"종합점수: `{composite:.2f}/{_composite_max:.2f}` "
                        f"(달성도 {confidence:.0%})"
                    )

                ai_pred = rec.get("ai_prediction", {})
                rise_prob = ai_pred.get("rise_probability", 0)
                sentiment = rec.get("sentiment_score", 0)

                signals = []
                if indicators.get("golden_cross"):
                    signals.append("골든크로스")
                if indicators.get("macd_buy_signal"):
                    signals.append("MACD매수")
                rsi_val = indicators.get("rsi", 100)
                if rsi_val < rsi_threshold:
                    signals.append(f"RSI({rsi_val:.0f})")
                signal_text = ", ".join(signals) if signals else "없음"

                rise_str = f"{rise_prob:+.1f}%" if rise_prob != 0 else "N/A"
                sent_str = f"{sentiment:.2f}" if sentiment > 0 else "N/A"

                blocks.append({
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": (
                            f"*{grade_emoji} {i}. {stock_name}* (`{ticker}`) — {grade_label}\n"
                            f"• {score_str} | 상승확률: `{rise_str}` | 감정: `{sent_str}`\n"
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
                        f"기준: {_ax['ai']['weight']}×AI(0~{float(_ax['ai']['max']):.0f}) + "
                        f"{_ax['technical']['weight']}×기술(0~{float(_ax['technical']['max']):.1f}) + "
                        f"{_ax['sentiment']['weight']}×감정(0~{float(_ax['sentiment']['max']):.0f}) "
                        f"→ composite (max {_composite_max:.1f}) | "
                        f"AI/감정은 양수 예측만 점수, 매수 추천=composite≥{_min_rec:.1f} + 가격 매수 | "
                        f"Quantiq Data Engine"
                    )}
                ]
            }
        ])

        fallback_text = (
            f"🎯 Quantiq 종합 분석 완료: {total}개 분석, "
            f"{candidate_count}개 추천, 평균 종합 {avg_composite:.2f}"
        )

        # 종합 분석 리포트는 Webhook으로만 발송 (채널 라우팅이 Webhook URL에 내장)
        SlackNotifier._post_to_webhook(webhook_url, fallback_text, blocks=blocks)

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
        SlackNotifier._post_error(text=text, attachments=attachments)

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
        SlackNotifier._post_error(text=text, attachments=attachments)

    @staticmethod
    def notify_daily_data_missing(analysis_date: str):
        """경제 데이터 미수집 에러 알림 → 에러 채널"""
        text = "❌ 종합 리포트 생성 실패"
        attachments = [
            {
                "color": "dc3545",
                "title": "경제 데이터 미수집",
                "text": (
                    f"`daily_stock_data` (date={analysis_date}) 가 존재하지 않습니다.\n"
                    f"경제 데이터 수집이 선행되어야 종합 리포트를 생성할 수 있습니다."
                ),
                "fields": [
                    {"title": "분석 날짜", "value": analysis_date, "short": True},
                    {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp()),
            }
        ]
        SlackNotifier._post_error(text=text, attachments=attachments)

    @staticmethod
    def notify_report_generation_error(analysis_date: str, error: str):
        """종합 리포트 생성/전송 실패 알림 → 에러 채널"""
        text = "❌ 종합 리포트 생성 실패"
        attachments = [
            {
                "color": "dc3545",
                "title": "리포트 생성 오류",
                "text": f"종합 리포트 생성/전송 중 오류가 발생했습니다.\n```{error[:500]}```",
                "fields": [
                    {"title": "분석 날짜", "value": analysis_date, "short": True},
                    {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp()),
            }
        ]
        SlackNotifier._post_error(text=text, attachments=attachments)

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
    def notify_recommendation_start(
        analysis_dates: list,
        thread_ts: Optional[str] = None,
    ) -> Optional[str]:
        """종목 추천 분석 시작 알림 → 스케줄러 채널"""
        scheduler_channel = SlackNotifier._get_scheduler_channel()
        scheduler_webhook = SlackNotifier._get_scheduler_webhook()
        if not scheduler_channel and not scheduler_webhook:
            logger.debug("스케줄러 채널 미설정, 추천 시작 알림 생략")
            return thread_ts

        date_str = (
            analysis_dates[0]
            if len(analysis_dates) == 1
            else f"{analysis_dates[0]} ~ {analysis_dates[-1]}"
        )
        text = "📊 종목 추천 분석 시작..."
        attachments = [
            {
                "color": "0099cc",
                "title": "종목 추천 분석 진행 중",
                "text": "기술적 분석 + AI 예측 + 감정 분석 종합 중",
                "fields": [
                    {"title": "분석 기간", "value": date_str, "short": True},
                    {"title": "날짜 수", "value": f"{len(analysis_dates)}일", "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp()),
            }
        ]
        ts = SlackNotifier._post_message(
            channel=scheduler_channel,
            webhook_url=scheduler_webhook,
            text=text,
            attachments=attachments,
            thread_ts=thread_ts,
        )
        return ts or thread_ts

    @staticmethod
    def notify_recommendation_complete(
        analysis_date: str,
        synced_count: int,
        candidate_count: int,
        near_miss_count: int,
        elapsed: float,
        thread_ts: Optional[str] = None,
    ) -> None:
        """종목 추천 완료 알림 → 스케줄러 채널"""
        scheduler_channel = SlackNotifier._get_scheduler_channel()
        scheduler_webhook = SlackNotifier._get_scheduler_webhook()
        if not scheduler_channel and not scheduler_webhook:
            logger.debug("스케줄러 채널 미설정, 추천 완료 알림 생략")
            return

        text = "✅ 종목 추천 분석 완료"
        attachments = [
            {
                "color": "28a745",
                "title": "종목 추천 완료",
                "fields": [
                    {"title": "분석 날짜", "value": analysis_date, "short": True},
                    {"title": "동기화 종목", "value": f"{synced_count}개", "short": True},
                    {"title": "최종 추천", "value": f"{candidate_count}개", "short": True},
                    {"title": "근접 탈락", "value": f"{near_miss_count}개", "short": True},
                    {"title": "소요 시간", "value": f"{elapsed:.1f}초", "short": True},
                    {
                        "title": "완료 시각",
                        "value": datetime.now(KST).strftime("%Y-%m-%d %H:%M:%S"),
                        "short": True,
                    },
                ],
                "footer": "종합 리포트 → 분석 채널 확인 | Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp()),
            }
        ]
        SlackNotifier._post_message(
            channel=scheduler_channel,
            webhook_url=scheduler_webhook,
            text=text,
            attachments=attachments,
            thread_ts=thread_ts,
        )

    @staticmethod
    def notify_buy_candidates(total_analyzed: int, buy_candidates: List, buy_criteria):
        """매수 후보 알림 (종합 리포트로 통합됨)"""
        logger.debug(
            f"매수 후보 분석 완료: {total_analyzed}개 분석, "
            f"{len(buy_candidates)}개 후보 (notify_comprehensive_report에서 전송됨)"
        )
