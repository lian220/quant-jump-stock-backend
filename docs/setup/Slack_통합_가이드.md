# 🔔 Slack 통합 가이드

**최종 업데이트**: 2026-02-13

---

## 📋 목차

1. [개요](#개요)
2. [알림 종류](#알림-종류)
3. [설정 방법](#설정-방법)
4. [스레드 기능](#스레드-기능)
5. [테스트](#테스트)
6. [문제 해결](#문제-해결)
7. [운영 및 모니터링](#운영-및-모니터링)

---

## 개요

QuantIQ 시스템의 모든 이벤트를 Slack으로 알림받을 수 있습니다.

### 알림 시스템

| 시스템 | 알림 유형 | Slack 기능 |
|--------|-----------|-----------|
| **경제 데이터 수집** | 요청, 시작, 완료, 오류 | 스레드 답글 |
| **기술적 분석** | 분석 완료, 매수 후보 | 일반 메시지 |
| **AI 예측** | 예측 완료, 오류 | 일반 메시지 |
| **자동 매매** | 매수, 매도, 오류 | 일반 메시지 |
| **스케줄러** | 상태 변경 | 일반 메시지 |

### Slack 채널 구성 권장

```
#스케쥴러    - 경제 데이터, 스케줄러 알림
#분석        - 기술/AI 분석 결과
#트레이딩    - 매수/매도 알림
#에러        - 모든 오류 알림 (선택)
```

---

## 알림 종류

### 1️⃣ 경제 데이터 수집 (스레드)

#### 요청 알림 (Kotlin Core)
```
📊 경제 데이터 업데이트 요청

Request ID: 550e8400-e29b-41d4-a716-446655440000
Timestamp: 2026-01-31T22:00:00Z
Source: quartz_scheduler
Status: Processing
```
**색상**: 🟢 초록색 (#36a64f)

#### 시작 알림 (Python - 스레드 답글)
```
└─ 🔄 경제 데이터 수집 시작
   Request ID: 550e8400-...
   Source: kafka
   Timestamp: 2026-01-31T22:00:02Z
```
**색상**: 🟢 초록색

#### 완료 알림 (Python - 스레드 답글)
```
└─ ✅ 경제 데이터 수집 완료
   FRED Indicators: 16개
   Yahoo Finance: 21개
   Collection Time: 8.5초
   Completed At: 2026-01-31T22:00:11Z
```
**색상**: 🟢 초록색 (#28a745)

#### 오류 알림 (Python - 스레드 답글)
```
└─ ⚠️ 경제 데이터 수집 오류
   Error: Connection timeout - FRED API
   Timestamp: 2026-01-31T22:00:30Z
   Action: 로그를 확인하고 수동 재시도하세요
```
**색상**: 🔴 빨간색 (#dc3545)

---

### 2️⃣ 분석 완료 알림

#### 매수 후보 있을 때
```
🎯 종합 분석 완료

총 분석 종목: 35개
매수 후보: 3개
매수 기준: composite = 0.3×AI + 0.4×기술 + 0.3×감정

📊 TOP 3 매수 후보
1. Apple Inc. (AAPL) — 종합: 1.40
   기술점수: 3.5 | RSI: 28.0 | 골든크로스: ✅ | MACD매수: ✅
   AI: N/A | 감정: N/A

⏰ 2026-02-13 23:10 KST / 09:10 EST
```
**색상**: 🟢 초록색
**채널**: #분석

#### 매수 후보 없을 때
```
🎯 종합 분석 완료

총 분석 종목: 35개
매수 후보: 0개

ℹ️ 매수 후보 없음
현재 AI/감정 미통합 상태에서 composite score max=1.4로
min 2.0 미달이 정상입니다.

⏰ 2026-02-13 23:10 KST
```

---

### 3️⃣ 자동 매매 알림

#### 매수 알림
```
💰 자동 매수 실행

종목: AAPL (Apple Inc.)
수량: 10주
가격: $150.25
총액: $1,502.50
시간: 2026-02-13 00:30 KST
```
**채널**: #트레이딩

#### 매도 알림
```
💸 자동 매도 실행

종목: MSFT (Microsoft)
수량: 5주
매도가: $380.50
매수가: $375.00
수익: +$27.50 (+1.47%)
시간: 2026-02-13 14:45 KST
```
**채널**: #트레이딩

---

## 설정 방법

### Step 1: Slack App 생성

1. [Slack API 대시보드](https://api.slack.com/apps) 접속
2. **Create New App** → **From scratch**
3. App 정보 입력:
   - **App name**: `QuantIQ Alerts`
   - **Workspace**: 워크스페이스 선택
4. **Create App** 클릭

---

### Step 2: Webhook URL 설정 (기본 알림용)

#### 2.1 Incoming Webhooks 활성화
1. 왼쪽 메뉴 **Incoming Webhooks** 클릭
2. **Activate Incoming Webhooks** 토글 ON
3. **Add New Webhook to Workspace** 클릭
4. 알림 받을 채널 선택 (예: #스케쥴러)
5. **Allow** 클릭

#### 2.2 Webhook URL 복사
```
https://hooks.slack.com/services/T.../B.../...
```

**환경 변수 설정:**
```bash
# .env.local
SLACK_WEBHOOK_URL_SCHEDULER=https://hooks.slack.com/services/.../...
SLACK_WEBHOOK_URL_TRADING=https://hooks.slack.com/services/.../...
SLACK_WEBHOOK_URL_ANALYSIS=https://hooks.slack.com/services/.../...
```

---

### Step 3: Bot Token 설정 (스레드 기능용)

#### 3.1 Bot Token Scopes 추가
**OAuth & Permissions** 메뉴에서:
- `chat:write` - 메시지 전송
- `chat:write.public` - Public 채널에 메시지 전송 (스레드 포함)

#### 3.2 App 설치
1. **Install to Workspace** 클릭
2. 권한 승인
3. **Bot User OAuth Token** 복사 (`xoxb-`로 시작)

**환경 변수 설정:**
```bash
# .env.local
SLACK_BOT_TOKEN=xoxb-xxxxxxxxxxxxx-xxxxxxxxxxxxx-xxxxxxxxxxxxxxxx
SLACK_CHANNEL=C0A1XASTLH2  # 채널 ID (필수!)
```

#### 3.3 채널 ID 확인 방법
1. Slack에서 채널 열기
2. 채널 이름 클릭 → **About** 탭
3. 하단 **Channel ID** 복사 (예: `C0A1XASTLH2`)

**⚠️ 중요**: 채널 이름(`#스케쥴러`)이 아닌 **채널 ID**를 사용하세요!

#### 3.4 Bot을 채널에 초대 (필수!)

**방법 1: 채널 통합 메뉴**
1. 채널 이름 클릭 → **Integrations** 탭
2. **Add apps** 클릭
3. `QuantIQ Alerts` 검색 후 추가

**방법 2: 채널에서 명령어**
```
/invite @QuantIQ Alerts
```

---

### Step 4: 애플리케이션 설정

#### Core (Spring Boot)
**파일:** `.env.local`
```bash
# Webhook (기본 알림)
SLACK_WEBHOOK_URL_SCHEDULER=https://hooks.slack.com/services/.../...
SLACK_WEBHOOK_URL_TRADING=https://hooks.slack.com/services/.../...
SLACK_WEBHOOK_URL_ANALYSIS=https://hooks.slack.com/services/.../...

# Bot Token (스레드 기능)
SLACK_BOT_TOKEN=xoxb-xxxxxxxxxxxxx-xxxxxxxxxxxxx-xxxxxxxxxxxxxxxx
SLACK_CHANNEL=C0A1XASTLH2
```

#### Data Engine (Python)
**파일:** `.env.local`
```bash
# Webhook
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/.../...

# Bot Token (스레드 기능)
SLACK_BOT_TOKEN=xoxb-xxxxxxxxxxxxx-xxxxxxxxxxxxx-xxxxxxxxxxxxxxxx
SLACK_CHANNEL=C0A1XASTLH2
```

#### Docker Compose
```yaml
quantiq-core:
  env_file:
    - .env.local
  environment:
    SLACK_BOT_TOKEN: ${SLACK_BOT_TOKEN}
    SLACK_CHANNEL: ${SLACK_CHANNEL}

quantiq-data-engine:
  env_file:
    - .env.local
  environment:
    SLACK_BOT_TOKEN: ${SLACK_BOT_TOKEN}
    SLACK_CHANNEL: ${SLACK_CHANNEL}
```

---

## 스레드 기능

### 처리 흐름

```
1. Kotlin Core - Slack API로 루트 메시지 발송
   ├─ POST https://slack.com/api/chat.postMessage
   ├─ "📊 경제 데이터 업데이트 요청"
   └─ 응답에서 threadTs 받음 (예: "1234567890.123456")

2. Kafka에 threadTs 포함하여 발행
   └─ { requestId, threadTs, timestamp, source }

3. Python Data Engine - threadTs 추출
   └─ payload.get("threadTs")

4. Python - Slack API로 스레드 답글 발송
   ├─ POST https://slack.com/api/chat.postMessage
   ├─ thread_ts=threadTs 사용
   └─ "🔄 수집 시작" / "✅ 수집 완료" 답글
```

### 스레드 vs Webhook 차이

| 항목 | Webhook | Bot API |
|------|---------|---------|
| **스레드** | ❌ 불가능 | ✅ 가능 |
| **채널 지정** | URL에 포함 | 파라미터로 지정 |
| **초대 필요** | ❌ 불필요 | ✅ 필수 |
| **Token** | Webhook URL | Bot Token (`xoxb-`) |

### Python 이벤트 스키마

```python
@dataclass
class EconomicDataSyncRequestedPayload:
    requestId: str
    dataTypes: List[str]
    source: str
    priority: str = "normal"
    threadTs: Optional[str] = None  # 스레드 기능용
```

---

## 테스트

### 1. Webhook URL 테스트

```bash
curl -X POST https://hooks.slack.com/services/YOUR_WORKSPACE/YOUR_CHANNEL/YOUR_TOKEN \
  -H 'Content-type: application/json' \
  --data '{
    "text":"🧪 Webhook 테스트 성공!",
    "attachments": [{
      "color": "28a745",
      "title": "연결 확인",
      "text": "Slack Webhook이 올바르게 설정되었습니다."
    }]
  }'
```

### 2. Bot Token 테스트

```bash
curl -X POST 'https://slack.com/api/chat.postMessage' \
  -H 'Authorization: Bearer YOUR_BOT_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{
    "channel":"YOUR_CHANNEL_ID",
    "text":"🧪 Bot Token 테스트"
  }'
```

**성공 응답:**
```json
{"ok":true,"ts":"1234567890.123456"}
```

**실패 (Bot 미초대):**
```json
{"ok":false,"error":"not_in_channel"}
```

### 3. 전체 시스템 테스트

```bash
# 경제 데이터 수집 트리거
curl -X POST http://localhost:10010/api/economic/trigger-update
```

**예상 Slack 메시지:**
```
📊 경제 데이터 업데이트 요청
└─ 🔄 경제 데이터 수집 시작
└─ ✅ 경제 데이터 수집 완료
```

### 4. 로그 확인

```bash
# Kotlin Core
docker logs quantiq-core | grep "Slack"
# ✅ Slack 스레드 루트 생성: requestId=xxx, threadTs=xxx

# Python Data Engine
docker logs quantiq-data-engine | grep "스레드"
# 📌 Kotlin 루트 스레드 연결: thread_ts=xxx
# ✅ Slack 스레드 답글 발송: thread_ts=xxx
```

---

## 문제 해결

### ❌ Slack 알림이 오지 않음

#### 원인 1: Webhook URL 오류
```bash
# 환경 변수 확인
grep SLACK_WEBHOOK_URL .env.local

# URL 접근 가능 여부 확인
curl -I https://hooks.slack.com/services/.../...
```

#### 원인 2: Bot Token 누락
```bash
# Bot Token 확인
grep SLACK_BOT_TOKEN .env.local

# Docker 환경 변수 확인
docker exec quantiq-core printenv SLACK_BOT_TOKEN
docker exec quantiq-data-engine printenv SLACK_BOT_TOKEN
```

---

### ❌ `not_in_channel` 오류

**원인:** Bot이 채널에 초대되지 않음

**해결:**
1. Slack에서 해당 채널 열기
2. 채널 이름 클릭 → **Integrations** 탭
3. **Add apps** 클릭
4. `QuantIQ Alerts` 추가

---

### ❌ `channel_not_found` 오류

**원인:** 잘못된 채널 ID 또는 채널 이름 사용

**해결:**
- 채널 **ID** 사용 (`C`로 시작, 예: `C0A1XASTLH2`)
- 채널 이름 대신 ID 사용 (한글 인코딩 문제 방지)

---

### ❌ `threadTs=null` (스레드 실패)

**원인:** Slack API 응답에서 `ts` 없음

**확인 사항:**
1. Bot Token 권한: `chat:write.public` 있는지
2. Bot이 채널에 초대되었는지
3. 채널 ID가 정확한지

**로그 확인:**
```bash
docker logs quantiq-core | grep "threadTs"
```

**정상:** `✅ Slack 스레드 루트 생성: threadTs=1234567890.123456`
**비정상:** `⚠️ threadTs가 null입니다`

---

### ❌ 환경 변수 불일치

**증상:** Core는 성공, Python은 `not_in_channel`

**확인:**
```bash
docker exec quantiq-core printenv SLACK_CHANNEL
docker exec quantiq-data-engine printenv SLACK_CHANNEL
```

두 값이 **동일한 채널 ID**여야 합니다!

---

## 운영 및 모니터링

### 일일 알림 패턴

```
22:00  경제 데이터 수집 (요청 → 시작 → 완료)
23:05  기술적 분석 + 감정 분석 (매수 후보 알림)
00:30  자동 매수 (매수 알림)
07:00  포트폴리오 수익 보고
장중   자동 매도 (매도 알림)
```

### 알림 필터링

**Slack 채널 설정:**
1. 채널 설정 → **알림 설정**
2. **음소거 기간** 설정 (예: 야간 알림 음소거)
3. **스레드 자동 정리** 활성화

### 성능 모니터링

**정상 소요 시간:**
- 경제 데이터 수집: 7-12초
- 기술적 분석: 5-10초
- AI 예측: 30분 (Vertex AI)

**비정상 신호:**
- 수집 시간 > 30초 → API 응답 지연
- 수집 완료 알림 없음 → 프로세스 실패

### Slack App 활동 로그

1. [Slack API 대시보드](https://api.slack.com/apps)
2. 앱 선택
3. **Activity Logs** 확인
   - 메시지 발송 이력
   - 오류 로그
   - API 호출 통계

---

## 🔐 보안 주의사항

### Webhook URL & Bot Token 보안

⚠️ **절대 공개하지 마세요!**

- Git에 커밋하지 않기 (`.gitignore`에 `.env.local` 추가)
- 팀원과 안전하게 공유하기 (1Password, AWS Secrets Manager)
- 정기적으로 재생성하기
- 로그에 출력하지 않기

### URL/Token 재생성 방법

**Webhook URL:**
1. Slack API 대시보드 → 앱 선택
2. **Incoming Webhooks** → 기존 URL **Delete**
3. **Add New Webhook to Workspace** → 새 URL 복사

**Bot Token:**
1. Slack API 대시보드 → 앱 선택
2. **OAuth & Permissions** → **Revoke Token**
3. **Reinstall to Workspace** → 새 Token 복사

---

## 📚 관련 문서

### 아키텍처
- [스케줄러 아키텍처](../architecture/스케줄러_아키텍처.md) - Quartz 스케줄러 구조
- [이벤트 기반 아키텍처](../../../docs/infra/kafka-to-pubsub-migration.md) - 메시징 아키텍처 (Pub/Sub)

### 설정 가이드
- [환경설정 가이드](./환경설정_가이드.md) - 전체 환경 설정
- [환경변수 관리 가이드](./환경변수_관리_가이드.md) - 환경 변수 관리
- [스케줄러 운영 가이드](./스케줄러_운영_가이드.md) - 스케줄러 운영

### 분석 기능
- [분석 시스템](../analysis/ARCHITECTURE.md) - 분석 파이프라인
- [추천 시스템](../analysis/functions/추천시스템.md) - Composite Score

---

## ✅ 체크리스트

### 설정 완료
- [ ] Slack App 생성
- [ ] Webhook URL 복사
- [ ] Bot Token 복사
- [ ] 채널 ID 확인
- [ ] Bot을 채널에 초대
- [ ] `.env.local` 설정
- [ ] Docker Compose 확인

### 테스트 완료
- [ ] Webhook URL 테스트
- [ ] Bot Token 테스트
- [ ] 경제 데이터 수집 테스트
- [ ] 스레드 기능 확인
- [ ] 로그 확인

### 운영 준비
- [ ] 알림 채널 정리
- [ ] 알림 필터 설정
- [ ] 모니터링 대시보드 구성
- [ ] 팀원 교육

---

## 🎉 완료!

이제 Slack 알림 시스템이 완전히 구성되었습니다!

**다음 단계:**
1. 일일 알림 패턴 모니터링
2. 알림 효율성 개선
3. Slack Workflow 자동화 (선택)
