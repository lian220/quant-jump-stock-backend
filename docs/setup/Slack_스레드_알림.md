# Slack 스레드 알림 시스템

## 📋 개요

경제 데이터 수집 프로세스의 상태를 Slack 스레드로 알림하는 시스템입니다.

## 🔄 처리 플로우

```
1. API 호출
   POST /api/economic/trigger-update

2. Kotlin Core - Slack 루트 메시지 발송
   ├─ Slack API 호출 (chat.postMessage)
   ├─ "📊 경제 데이터 업데이트 요청" 메시지 발송
   └─ Slack 응답에서 threadTs 받음 ← 스레드 루트 생성

3. Kafka 메시지 발행
   ├─ Topic: economic.data.update.request
   └─ Payload: { requestId, threadTs, timestamp, source }

4. Python Data Engine - Kafka 메시지 수신
   └─ threadTs 추출

5. Python - Slack 답글 (수집 시작)
   ├─ "🔄 경제 데이터 수집 시작"
   └─ thread_ts=threadTs 사용 → 루트 메시지의 답글로 등록

6. 경제 데이터 수집 실행
   └─ FRED API + Yahoo Finance 데이터 수집

7. Python - Slack 답글 (수집 완료)
   ├─ "✅ 경제 데이터 수집 완료"
   └─ thread_ts=threadTs 사용 → 루트 메시지의 답글로 등록
```

## 🔧 설정 방법

### 1. Slack App 생성 및 권한 설정

#### 1.1 Slack App 생성
1. https://api.slack.com/apps 접속
2. "Create New App" → "From scratch"
3. App 이름 입력 및 워크스페이스 선택

#### 1.2 Bot Token Scopes 추가
**OAuth & Permissions** 메뉴에서 다음 권한 추가:
- `chat:write` - 메시지 전송
- `chat:write.public` - Public 채널에 메시지 전송 (스레드 포함)

#### 1.3 App 설치
1. "Install to Workspace" 클릭
2. 권한 승인
3. **Bot User OAuth Token** 복사 (`xoxb-`로 시작)

### 2. Slack 채널 설정

#### 2.1 알림 받을 채널 준비
- 예: `#스케쥴러` 채널

#### 2.2 채널 ID 확인
1. Slack에서 채널 열기
2. 채널 이름 클릭 → "About" 탭
3. 하단의 **Channel ID** 복사 (예: `C0A1XASTLH2`)

#### 2.3 Bot을 채널에 초대 (중요!)
**방법 1: 채널 통합 메뉴**
1. 채널에서 이름 클릭 → "Integrations" 탭
2. "Add apps" 클릭
3. 생성한 App 이름 검색 후 추가

**방법 2: 채널에서 명령어**
```
/invite @your-app-name
```

### 3. 환경변수 설정

#### 3.1 루트 `.env.local` 파일 설정
```bash
# Slack Configuration
SLACK_BOT_TOKEN=xoxb-xxxxxxxxxxxxx-xxxxxxxxxxxxx-xxxxxxxxxxxxxxxx
SLACK_CHANNEL=C0A1XASTLH2  # 채널 ID (필수!)
SLACK_WEBHOOK_URL_SCHEDULER=https://hooks.slack.com/services/T.../B.../...
```

#### 3.2 Docker Compose 설정 확인
```yaml
quantiq-core:
  env_file:
    - .env.local  # 루트 .env.local 사용
  environment:
    SLACK_BOT_TOKEN: ${SLACK_BOT_TOKEN}
    SLACK_CHANNEL: ${SLACK_CHANNEL}

quantiq-data-engine:
  env_file:
    - .env.local  # 루트 .env.local 사용
  environment:
    SLACK_BOT_TOKEN: ${SLACK_BOT_TOKEN}
    SLACK_CHANNEL: ${SLACK_CHANNEL}
```

### 4. 서비스 재시작
```bash
docker compose down
docker compose up -d
```

## 🧪 테스트 방법

### 1. Bot이 채널에 있는지 확인
```bash
curl -X POST 'https://slack.com/api/chat.postMessage' \
  -H 'Authorization: Bearer YOUR_BOT_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"channel":"YOUR_CHANNEL_ID","text":"🧪 테스트"}'
```

**성공 응답:**
```json
{"ok":true,"ts":"1234567890.123456"}
```

**실패 응답 (Bot 없음):**
```json
{"ok":false,"error":"not_in_channel"}
```

### 2. API 호출 테스트
```bash
curl -X POST http://localhost:10010/api/economic/trigger-update
```

### 3. Slack 확인
**예상 결과:**
```
📊 경제 데이터 업데이트 요청
└─ 🔄 경제 데이터 수집 시작
└─ ✅ 경제 데이터 수집 완료
```

## ❌ 문제 해결

### 1. `not_in_channel` 오류

**원인:** Bot이 채널에 초대되지 않음

**해결:**
1. Slack에서 해당 채널 열기
2. Integrations → Add apps
3. **Bot Token의 App** 추가 (Webhook과 다름!)

### 2. `channel_not_found` 오류

**원인:**
- 잘못된 채널 ID
- 채널 이름 사용 (한글 인코딩 문제)

**해결:**
- 채널 **ID** 사용 (`C`로 시작)
- 채널 이름 대신 ID 사용

### 3. `threadTs=null` (Kotlin)

**원인:** Slack API 응답에서 `ts` 없음

**확인 사항:**
1. Bot Token 권한: `chat:write.public` 있는지
2. Bot이 채널에 있는지
3. 채널 ID가 정확한지

**로그 확인:**
```bash
docker logs quantiq-core | grep "Slack"
```

**정상:** `✅ Slack 스레드 루트 생성: requestId=xxx, threadTs=xxx`
**비정상:** `⚠️ Slack 메시지 발송 성공하지만 threadTs 없음`

### 4. Python `not_in_channel` (Kotlin은 성공)

**원인:** 환경변수 불일치

**확인:**
```bash
docker exec quantiq-core printenv SLACK_CHANNEL
docker exec quantiq-data-engine printenv SLACK_CHANNEL
```

두 값이 **동일한 채널 ID**여야 함!

## 📊 로그 확인

### Kotlin Core
```bash
docker logs quantiq-core --tail 50 | grep -E "Slack|thread|경제"
```

**확인할 내용:**
- `✅ Slack 스레드 루트 생성: requestId=xxx, threadTs=1234567890.123456`
- `✅ Kafka 이벤트 발행 완료: requestId=xxx, threadTs=1234567890.123456`

### Python Data Engine
```bash
docker logs quantiq-data-engine --tail 50 | grep -E "Slack|thread|경제"
```

**확인할 내용:**
- `📌 Kotlin 루트 스레드 연결: request_id=xxx, thread_ts=1234567890.123456`
- `✅ Slack 스레드 답글 발송: thread_ts=1234567890.123456`

## 🔑 핵심 포인트

### 1. Bot vs Webhook
- **Webhook**: 채널이 URL에 포함, 스레드 **불가능**
- **Bot API**: 채널 지정 필요, 스레드 **가능**

### 2. 채널 ID vs 채널 이름
- **채널 이름** (`#스케쥴러`): 한글 인코딩 문제 발생 가능
- **채널 ID** (`C0A1XASTLH2`): 권장! 안정적

### 3. Bot 초대 필수
- Webhook은 URL에 채널 정보 포함 → 초대 불필요
- **Bot API는 채널에 초대 필수!**

### 4. Kotlin이 먼저 threadTs 생성
- Kotlin: Slack API 호출 → `threadTs` 받음
- Python: Kafka에서 `threadTs` 받음 → 답글로 사용

## 🔄 현재 상태 (2026-01-31)

### ✅ 완료
- [x] Kotlin SlackNotificationService Slack API 방식 구현
- [x] Python SlackNotifier 스레드 지원 구현
- [x] Kafka 이벤트에 threadTs 포함
- [x] 환경변수 설정 통합 (루트 .env.local 사용)
- [x] Python Event Schema에 threadTs 필드 추가
- [x] main.py에서 threadTs 추출 및 전달 구현
- [x] Bot을 채널 `C0A1XASTLH2`에 초대 완료
- [x] 실제 동작 테스트 및 검증 완료
- [x] Slack 스레드 답글 기능 정상 작동 확인
- [x] 수집 완료 메시지에 간략한 데이터 요약 추가

### 📊 구현된 기능

#### 1. 스레드 답글 기능
- Kotlin Core가 Slack API로 루트 메시지 발송 및 threadTs 획득
- Kafka를 통해 Python으로 threadTs 전달
- Python에서 "수집 시작", "수집 완료" 메시지를 스레드 답글로 발송

#### 2. 데이터 수집 결과 요약
수집 완료 메시지에 다음 정보 표시:
- FRED 지표 수집 개수
- Yahoo Finance 지표 수집 개수
- 총 수집 지표 개수
- 소요 시간
- 완료 시각

### 🎯 검증 완료
```bash
# 테스트 API 호출
curl -X POST http://localhost:10010/api/economic/trigger-update

# 로그 확인
docker logs quantiq-core | grep "threadTs"
# ✅ Slack 스레드 루트 생성: requestId=xxx, threadTs=xxx

docker logs quantiq-data-engine | grep "스레드"
# 📌 Kotlin 루트 스레드 연결: request_id=xxx, thread_ts=xxx
# ✅ Slack 스레드 답글 발송: thread_ts=xxx, ts=xxx
```

### 🔧 주요 코드 변경사항

#### Python Event Schema (`schema.py`)
```python
@dataclass
class EconomicDataSyncRequestedPayload:
    requestId: str
    dataTypes: List[str]
    source: str
    priority: str = "normal"
    threadTs: Optional[str] = None  # 추가됨
```

#### Python Main (`main.py`)
```python
# threadTs 추출 및 전달
thread_ts = payload.get("threadTs")
SlackNotifier.notify_economic_data_collection_start(request_id, source, thread_ts)
SlackNotifier.notify_economic_data_collection_success(request_id, summary, thread_ts)
```

#### Slack Notification 개선 (`slack_notifier.py`)
```python
# 수집 결과 데이터 표시
fred_count = data_summary.get("fred_collected", 0)
yahoo_count = data_summary.get("yahoo_collected", 0)
total_count = data_summary.get("total_indicators", 0)
```
