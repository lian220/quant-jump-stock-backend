"""
Secret Manager 변경 알람 Cloud Function

Eventarc trigger로 `google.cloud.secretmanager.v1.SecretManagerService.AddSecretVersion`
audit log 이벤트를 수신하여 Slack 채널에 알림 전송.

배경 (2026-05-12 사고):
  76일 정지 사고의 원인이 2026-02-27 23:33 UTC의 Secret v17→v18 키 이름 변경이었으나,
  변경 시점에 누구도 인지하지 못했음. freshness 알람의 일부로서 Secret 변경 자체를
  실시간 감지하여 동일 사고 재발 차단.

배포: 수동 (자주 변경되지 않는 인프라). README.md 참조.
"""

from __future__ import annotations

import json
import logging
import os
from typing import Any

import functions_framework
import requests
from cloudevents.http import CloudEvent

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO)

def _load_webhook_url() -> str:
    """Slack webhook URL 로드.

    GCP `--set-secrets` 는 시크릿 전체 값만 환경변수로 마운트하며 단일 키 추출을 지원하지 않음.
    qjs-env-common 시크릿은 dotenv 형식 (KEY=VALUE 줄)이므로 SECRET_BUNDLE 환경변수에서 파싱.

    우선순위:
      1. SLACK_WEBHOOK_URL 직접 주입 (테스트/수동 배포)
      2. SECRET_BUNDLE 안의 SLACK_WEBHOOK_URL_SCHEDULER 줄
    """
    direct = os.getenv("SLACK_WEBHOOK_URL", "")
    if direct:
        return direct
    bundle = os.getenv("SECRET_BUNDLE", "")
    if bundle:
        for raw in bundle.splitlines():
            line = raw.strip()
            if line.startswith("SLACK_WEBHOOK_URL_SCHEDULER="):
                return line.split("=", 1)[1].strip().strip('"').strip("'")
    return ""


SLACK_WEBHOOK_URL = _load_webhook_url()

# Secret Manager AuditLog method 이름 → 사람이 읽기 쉬운 라벨
_METHOD_LABEL = {
    "google.cloud.secretmanager.v1.SecretManagerService.AddSecretVersion": "📝 새 버전 추가",
    "google.cloud.secretmanager.v1.SecretManagerService.DestroySecretVersion": "🗑️ 버전 폐기",
    "google.cloud.secretmanager.v1.SecretManagerService.DisableSecretVersion": "🚫 버전 비활성",
    "google.cloud.secretmanager.v1.SecretManagerService.EnableSecretVersion": "✅ 버전 활성",
    "google.cloud.secretmanager.v1.SecretManagerService.UpdateSecret": "✏️ 시크릿 메타 수정",
    "google.cloud.secretmanager.v1.SecretManagerService.DeleteSecret": "❌ 시크릿 삭제",
    "google.cloud.secretmanager.v1.SecretManagerService.CreateSecret": "➕ 시크릿 생성",
}


def _extract_resource_name(audit_payload: dict[str, Any]) -> str:
    """audit log payload에서 secret 자원 이름 추출."""
    name = (
        audit_payload.get("resourceName")
        or audit_payload.get("request", {}).get("parent")
        or audit_payload.get("request", {}).get("name")
        or "(unknown)"
    )
    # "projects/<id>/secrets/<name>/versions/<ver>" → 보기 좋게
    parts = name.split("/")
    if "secrets" in parts:
        idx = parts.index("secrets")
        secret_name = parts[idx + 1] if idx + 1 < len(parts) else "(unknown)"
        version = parts[-1] if "versions" in parts else None
        return f"{secret_name}" + (f" (version {version})" if version else "")
    return name


def _build_slack_message(audit_payload: dict[str, Any]) -> dict[str, Any]:
    """Slack mrkdwn 메시지 빌드."""
    method = audit_payload.get("methodName", "(unknown)")
    label = _METHOD_LABEL.get(method, f"⚠️ {method}")
    principal = (
        audit_payload.get("authenticationInfo", {}).get("principalEmail", "(unknown)")
    )
    resource = _extract_resource_name(audit_payload)
    timestamp = audit_payload.get("requestMetadata", {}).get("requestAttributes", {}).get(
        "time", ""
    )

    blocks = [
        {
            "type": "header",
            "text": {
                "type": "plain_text",
                "text": "🔐 Secret Manager 변경 감지",
            },
        },
        {
            "type": "section",
            "fields": [
                {"type": "mrkdwn", "text": f"*동작*\n{label}"},
                {"type": "mrkdwn", "text": f"*시각*\n{timestamp or 'n/a'}"},
                {"type": "mrkdwn", "text": f"*시크릿*\n`{resource}`"},
                {"type": "mrkdwn", "text": f"*변경자*\n`{principal}`"},
            ],
        },
        {
            "type": "context",
            "elements": [
                {
                    "type": "mrkdwn",
                    "text": (
                        "2026-05-12 76일 정지 사고의 원인이 Secret v17→v18 키 변경이었음. "
                        "본 알람은 동일 사고 재발 차단용."
                    ),
                }
            ],
        },
    ]

    return {"blocks": blocks, "text": f"Secret Manager 변경: {resource} ({label})"}


@functions_framework.cloud_event
def secret_version_alert(cloud_event: CloudEvent) -> None:
    """
    Eventarc CloudEvent 핸들러.

    Trigger: Audit Log (Cloud Audit) for Secret Manager events.
    Payload: cloud_event.data 안에 protoPayload (AuditLog) 가 들어있음.
    """
    if not SLACK_WEBHOOK_URL:
        logger.error("SLACK_WEBHOOK_URL 환경변수 미설정 — 알람 발송 skip")
        return

    # CloudEvent data가 base64 인코딩일 수도 있으나 Eventarc Audit Log trigger는
    # 통상적으로 직접 JSON. functions_framework가 알아서 파싱해줌.
    data = cloud_event.data or {}

    # Audit log 가 'protoPayload' 키 안에 있는 경우와 평탄한 경우 둘 다 처리
    audit_payload = data.get("protoPayload") if isinstance(data, dict) else None
    if audit_payload is None:
        # 평탄한 형식
        audit_payload = data

    if not isinstance(audit_payload, dict):
        logger.warning("audit_payload 형식 인식 불가: type=%s", type(audit_payload))
        return

    method = audit_payload.get("methodName", "")

    # DataAccess 이벤트 (AccessSecretVersion 등 *읽기*) 는 빈도 매우 높음 → 알람 제외
    # 우리가 관심 있는 건 *변경* 이벤트만
    if method not in _METHOD_LABEL:
        logger.info("관심 외 method, skip: %s", method)
        return

    message = _build_slack_message(audit_payload)

    try:
        resp = requests.post(SLACK_WEBHOOK_URL, json=message, timeout=10)
        resp.raise_for_status()
        logger.info(
            "Slack 알람 전송 성공: method=%s resource=%s",
            method,
            _extract_resource_name(audit_payload),
        )
    except requests.RequestException as e:
        logger.exception("Slack 알람 전송 실패: %s", e)
        # 알람 전송 실패는 raise 하지 않음 (Eventarc 재시도 폭주 방지)
