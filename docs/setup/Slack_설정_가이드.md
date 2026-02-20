# 🔔 Slack 알림 설정 가이드

## 개요

경제 데이터 수집 스케줄러의 모든 이벤트를 Slack으로 알림받을 수 있도록 설정하는 가이드입니다.

**알림 종류:**
- ✅ 경제 데이터 수집 **요청**
- ✅ 경제 데이터 수집 **완료**
- ⚠️ 경제 데이터 수집 **오류**
- ⚠️ FRED API 오류
- ⚠️ Yahoo Finance 오류
- 🔄 스케줄러 상태 변경

---

## 📋 사전 준비

### 1. Slack 워크스페이스 생성 (없는 경우)

[Slack 공식 사이트](https://slack.com)에서 워크스페이스를 생성하세요.

### 2. Slack 채널 생성

```
채널명: #quantiq-alerts (또는 원하는 채널명)
설명: Quantiq 자동매매 시스템 알림
공개/비공개: 원하는 대로 선택
```

---

## 🔧 Slack Webhook URL 설정

### Step 1: Slack App 생성

1. [Slack API 대시보드](https://api.slack.com/apps)로 이동
2. **Create New App** 클릭
3. **From scratch** 선택
4. 앱 정보 입력:
   - **App name**: `Quantiq Economic Data Scheduler`
   - **Workspace**: 워크스페이스 선택
5. **Create App** 클릭

### Step 2: Incoming Webhooks 활성화

1. 왼쪽 메뉴에서 **Incoming Webhooks** 클릭
2. **Activate Incoming Webhooks** 토글 활성화
3. **Add New Webhook to Workspace** 클릭
4. 알림을 받을 채널 선택 (예: #quantiq-alerts)
5. **Allow** 클릭

### Step 3: Webhook URL 복사

1. **Webhook URLs for Your Workspace** 섹션에서
2. 생성된 URL을 복사합니다 (예: `https://hooks.slack.com/services/YOUR_WORKSPACE_ID/YOUR_CHANNEL_ID/YOUR_TOKEN`)

---

## ⚙️ 애플리케이션 설정

### quantiq-core (Spring Boot)

**파일:** `.env.local` 또는 `application.yml`

```yaml
# .env.local
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR_WORKSPACE_ID/YOUR_CHANNEL_ID/YOUR_TOKEN

# 또는 application.yml
slack:
  webhook-url: https://hooks.slack.com/services/YOUR_WORKSPACE_ID/YOUR_CHANNEL_ID/YOUR_TOKEN
```

### quantiq-data-engine (Python)

**파일:** `.env.local`

```bash
# .env.local
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR_WORKSPACE_ID/YOUR_CHANNEL_ID/YOUR_TOKEN
```

---

## ✅ 설정 확인

### 1. Webhook URL이 올바른지 확인

```bash
# Webhook URL 테스트 (curl)
curl -X POST https://hooks.slack.com/services/YOUR_WORKSPACE_ID/YOUR_CHANNEL_ID/YOUR_TOKEN \
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

### 2. Spring Boot 애플리케이션 시작

```bash
cd quantiq/quantiq-core
./gradlew bootRun
```

**로그 확인:**
```
INFO - Slack 알림 발송 완료
```

### 3. Python 데이터 엔진 시작

```bash
cd quantiq/quantiq-data-engine
python src/main.py
```

---

## 🧪 알림 테스트

### REST API를 통한 수동 트리거

```bash
curl -X POST http://localhost:10010/api/economic/trigger-update
```

**예상 Slack 알림:**

```
📊 경제 데이터 업데이트 요청

Quantiq Economic Data Scheduler

Title: 경제 데이터 수집 시작
Request ID: 550e8400-e29b-41d4-a716-446655440000
Timestamp: 2024-01-31T10:30:45.123Z
Source: quartz_scheduler
Status: Processing
```

---

## 📊 알림 종류

### 1️⃣ 경제 데이터 수집 시작

```
📊 경제 데이터 업데이트 요청

Request ID: [UUID]
Timestamp: [시간]
Source: quartz_scheduler or rest_api
Status: Processing
```

**색상:** 🟢 (초록색 - #36a64f)

### 2️⃣ 경제 데이터 수집 완료

```
✅ 경제 데이터 수집 완료

Collected At: [시간]
FRED Indicators: 16개
Yahoo Finance Indicators: 21개
Database: MongoDB stock_trading
```

**색상:** 🟢 (초록색 - #28a745)

### 3️⃣ 경제 데이터 수집 오류

```
⚠️ 경제 데이터 수집 오류

Request ID: [UUID]
Error: [에러 메시지]
Timestamp: [시간]
Action: 수동으로 재시도해주세요
```

**색상:** 🔴 (빨간색 - #dc3545)

### 4️⃣ FRED API 오류

```
⚠️ FRED API 오류

Indicator: T10YIE
Error: Connection timeout
Timestamp: [시간]
```

**색상:** 🟡 (노란색 - #ffc107)

### 5️⃣ Yahoo Finance 오류

```
⚠️ Yahoo Finance 오류

Ticker: ^GSPC
Error: Rate limit exceeded
Timestamp: [시간]
```

**색상:** 🟡 (노란색 - #ffc107)

---

## 🛠️ 문제 해결

### Slack 알림이 오지 않는 경우

#### 1. Webhook URL 확인

```bash
# .env 파일에 URL이 올바르게 설정되었는지 확인
grep SLACK_WEBHOOK_URL .env.local

# Spring Boot 로그에서 확인
grep -i "slack" <spring-log>

# Python 로그에서 확인
grep -i "slack" <python-log>
```

#### 2. 네트워크 연결 확인

```bash
# Webhook URL 접근 가능 여부 확인
curl -I https://hooks.slack.com/services/YOUR_WORKSPACE_ID/YOUR_CHANNEL_ID/YOUR_TOKEN
```

#### 3. Slack App 권한 확인

1. [Slack API 대시보드](https://api.slack.com/apps)로 이동
2. 앱 선택
3. **OAuth & Permissions** 확인
4. **Scopes** 확인
5. 필요시 **Reinstall to Workspace** 클릭

#### 4. 채널 확인

1. Slack 앱에서 채널 확인
2. 봇이 채널에 초대되었는지 확인
3. 필요시 `/invite @Quantiq` 명령어로 초대

---

## 📱 Slack 채널 설정 팁

### 알림 필터링

**Slack 채널 설정:**
1. 채널 이름 클릭
2. **⚙️ 설정** → **통합**
3. **앱 추가** → Quantiq 앱 추가

### 알림 음소거

**특정 시간대에 알림 음소거:**
1. 채널 설정 → **알림 설정**
2. **음소거 기간** 설정
3. 시간대 선택

### 알림 필터

**스레드로 정렬:**
1. 채널 설정 → **스레드 설정**
2. 자동 스레드 처리 활성화

---

## 🔐 보안 주의사항

### Webhook URL 보안

⚠️ **중요**: Webhook URL을 공개하지 마세요!

- Git에 커밋하지 않기
- 환경 변수로 관리하기
- 팀원과 안전하게 공유하기
- 노출된 경우 즉시 재생성하기

**URL 재생성 방법:**
1. [Slack API 대시보드](https://api.slack.com/apps)로 이동
2. 앱 선택
3. **Incoming Webhooks** → 기존 URL **삭제**
4. **Add New Webhook to Workspace** 클릭
5. 새 URL 복사

---

## 📈 모니터링

### Slack에서 활동 추적

```
/stats in #quantiq-alerts
```

**추적할 수 있는 항목:**
- 일일 메시지 수
- 피크 시간대
- 오류 빈도

### Slack App 로그 확인

1. [Slack API 대시보드](https://api.slack.com/apps)
2. 앱 선택
3. **Activity Logs** 확인

---

## 🎯 활용 예시

### 자동 알림으로 모니터링

```
매일 06:05 → 자동 알림 (경제 데이터 수집 시작)
↓
데이터 수집 중 (7-12초)
↓
자동 알림 (경제 데이터 수집 완료)
↓
MongoDB에 저장 완료
```

### Slack Workflow 자동화 (선택사항)

**Slack Workflow를 설정하여:**
- 오류 발생 시 자동 에스컬레이션
- 완료 시 자동 리포트 생성
- 정기 요약 레포트 발송

---

## 📞 지원

문제가 발생하면:

1. [Slack API 문서](https://api.slack.com/messaging/webhooks) 확인
2. 환경 변수 설정 재확인
3. 로그 파일 검토
4. Webhook URL 재생성 시도

---

## ✨ 완료!

이제 Slack 알림이 설정되었습니다! 🎉

**다음 확인:**
- ✅ Webhook URL 설정 완료
- ✅ 채널 생성 완료
- ✅ 애플리케이션 설정 완료
- ✅ 테스트 알림 수신 완료

---

## 📚 관련 문서

### 아키텍처
- [이벤트 기반 아키텍처](../architecture/이벤트_기반_아키텍처.md) - Slack 스레드 패턴
- [스케줄러 아키텍처](../architecture/스케줄러_아키텍처.md) - 스케줄 기반 알림
- [이벤트 스키마](../architecture/이벤트_스키마.md) - threadTs 필드 설명

### 설정 가이드
- [환경설정 가이드](./환경설정_가이드.md) - 전체 환경 설정
- [환경변수 관리 가이드](./환경변수_관리_가이드.md) - SLACK 환경변수
- [배포 운영 가이드](./배포_운영_가이드.md) - 알림 설정

### 추가 문서
- [Slack 스레드 알림](./Slack_스레드_알림.md) - 스레드 답글 패턴
- [Slack 알림 요약](./Slack_알림_요약.md) - 알림 종류 요약
