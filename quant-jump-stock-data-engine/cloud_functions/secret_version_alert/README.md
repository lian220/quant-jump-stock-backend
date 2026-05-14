# Secret Version Alert — Cloud Function

Secret Manager 변경 이벤트를 Eventarc로 받아 Slack 채널에 알람 전송.

## 배경

2026-02-27 ~ 2026-05-13 Vertex AI 76일 정지 사고의 직접 원인이
`qjs-env-common` Secret v17→v18 시점의 키 이름 변경 (`VERTEX_AI_*` → `GCP_*`)이었으나,
변경 시점에 누구도 인지하지 못했음. 

본 Cloud Function은 동일 사고 재발 차단을 위한 **실시간 Secret 변경 감지** 메커니즘.

## 감지 대상 이벤트

| 메서드 | 동작 |
|---|---|
| `AddSecretVersion` | 📝 새 버전 추가 (가장 중요 — 76일 정지의 직접 trigger 였음) |
| `DestroySecretVersion` | 🗑️ 버전 폐기 |
| `DisableSecretVersion` / `EnableSecretVersion` | 🚫/✅ 버전 활성화 토글 |
| `UpdateSecret` / `DeleteSecret` / `CreateSecret` | 메타 변경 |

`AccessSecretVersion` 같은 *읽기* 이벤트는 빈도가 매우 높아 알람 제외.

## 일회성 수동 배포 (운영 환경)

자주 변경되지 않는 인프라이므로 GitHub Actions 자동 배포 워크플로우 추가하지 않고
수동 배포로 처리. 향후 SigNoz 등 외부 observability 플랫폼 도입 시 통합 검토.

### 사전 준비

```bash
# 1. Secret Manager API + Eventarc API + Cloud Audit Log 활성화
gcloud services enable \
  eventarc.googleapis.com \
  secretmanager.googleapis.com \
  logging.googleapis.com

# 2. Audit Log에서 Secret Manager Admin Activity 가 *기본 활성화*되어 있음 — 추가 설정 불필요

# 3. Cloud Function 서비스 어카운트 IAM 확인 (Compute 기본 SA 또는 별도 SA)
#    필요 권한: roles/secretmanager.secretAccessor (Slack webhook secret 마운트용)
```

### 배포

```bash
cd quant-jump-stock-data-engine/cloud_functions/secret_version_alert

# Cloud Function 배포 (gen2)
gcloud functions deploy secret-version-alert \
  --gen2 \
  --runtime=python311 \
  --region=asia-northeast3 \
  --entry-point=secret_version_alert \
  --trigger-event-filters="type=google.cloud.audit.log.v1.written" \
  --trigger-event-filters="serviceName=secretmanager.googleapis.com" \
  --trigger-location=global \
  --source=. \
  --set-secrets="SLACK_WEBHOOK_URL=qjs-env-common:latest/SLACK_WEBHOOK_URL_SCHEDULER" \
  --memory=256Mi \
  --max-instances=3
```

> **참고**: `--set-secrets` 의 두 번째 `:` 뒤가 secret 내부 키 이름. `qjs-env-common`
> 시크릿의 내용 안에 `SLACK_WEBHOOK_URL_SCHEDULER=...` 줄이 있어야 매칭됨.
> 별도 SLACK_WEBHOOK_URL 시크릿을 따로 만들어 두는 방법도 가능.

### 배포 후 검증

```bash
# 1. Cloud Function 상태 확인
gcloud functions describe secret-version-alert --region=asia-northeast3 \
  --gen2 --format='value(state,url,eventTrigger)'

# 2. 테스트 — 의도적 Secret 갱신 (실제 운영 시크릿 건드리지 말고 테스트용 시크릿 생성)
echo "TEST=v1" | gcloud secrets create qjs-eventarc-test --data-file=-
# → ~30초 안에 Slack 채널 (SCHEDULER) 에 "📝 새 버전 추가 / qjs-eventarc-test" 알람 도착해야

# 3. 테스트 후 정리
gcloud secrets delete qjs-eventarc-test
# → "❌ 시크릿 삭제" 알람 추가 도착 확인

# 4. 로그 확인
gcloud functions logs read secret-version-alert --region=asia-northeast3 --gen2 --limit=20
```

## 비용

- Cloud Function gen2: 첫 200만 호출/월 무료
- Eventarc: $0.40 / 백만 이벤트
- Secret Manager 변경 빈도 매우 낮음 (월 ~10건 미만 추정)
- **실제 비용: 거의 0** (~$0.01/월)

## 트러블슈팅

### 알람이 안 옴

```bash
# Audit Log 가 실제 발생하는지
gcloud logging read 'protoPayload.serviceName="secretmanager.googleapis.com"
  AND protoPayload.methodName=~"AddSecretVersion|DestroySecretVersion"' \
  --limit=5 --freshness=1d --format='value(timestamp,protoPayload.methodName)'

# Cloud Function이 호출됐는지
gcloud functions logs read secret-version-alert --region=asia-northeast3 \
  --gen2 --limit=30
```

### Slack webhook 권한 오류

```bash
# Cloud Function 의 SA 가 secret accessor 권한 있는지
SA=$(gcloud functions describe secret-version-alert --region=asia-northeast3 \
  --gen2 --format='value(serviceConfig.serviceAccountEmail)')

gcloud secrets get-iam-policy qjs-env-common --format=json | jq '.bindings'
# 없으면 추가:
gcloud secrets add-iam-policy-binding qjs-env-common \
  --member="serviceAccount:$SA" \
  --role="roles/secretmanager.secretAccessor"
```

## 미래 확장

SigNoz 도입 시 Slack 알람은 SigNoz Alert로 통합 검토. 그 시점에 본 Cloud Function 폐기 또는 보완.
