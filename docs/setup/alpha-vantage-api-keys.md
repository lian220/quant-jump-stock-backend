# Alpha Vantage API Keys 관리 가이드

## 📌 개요

Alpha Vantage API는 무료 제한이 있어 여러 개의 키를 로테이션하며 사용합니다.

### 무료 제한
- **일일 제한**: 25 calls/day per key
- **분당 제한**: 5 calls/min per key

### 다중 키 전략
- 키 3개 → 75 calls/day (효과적으로 15 calls/min)
- 키 5개 → 125 calls/day (효과적으로 25 calls/min)

---

## 🔑 API 키 발급

### 1. Alpha Vantage 사이트 방문
https://www.alphavantage.co/support/#api-key

### 2. 무료 키 발급
- 이메일 입력
- 즉시 발급 (별도 인증 불필요)
- 하루 제한: 25 calls

### 3. 여러 이메일로 추가 키 발급
- Gmail alias 활용: `yourname+alpha1@gmail.com`, `yourname+alpha2@gmail.com`
- 각각 별도 키 발급 가능

---

## ⚙️ 설정 방법

### .env.common 파일 (권장)

```bash
# .env.common 파일에 키 추가
ALPHA_VANTAGE_API_KEY_1=96TGH4FIWVK4CLBE
ALPHA_VANTAGE_API_KEY_2=S250U5T2N60E0ROO
ALPHA_VANTAGE_API_KEY_3=your_third_key_here
ALPHA_VANTAGE_API_KEY_4=
ALPHA_VANTAGE_API_KEY_5=
```

### 자동 감지
- `ALPHA_VANTAGE_API_KEY_1` ~ `ALPHA_VANTAGE_API_KEY_5` 순서로 로드
- 빈 값은 자동 스킵
- 최소 1개, 최대 5개까지 지원

---

## 🔄 로테이션 전략

### 1. 라운드 로빈
```python
키 순서: Key1 → Key2 → Key3 → Key1 → ...
```

### 2. Rate Limit 감지
```python
# HTTP 429 또는 API 메시지 감지
if "rate limit" in response:
    다음 키로 즉시 전환 (대기 없음)
```

### 3. 백오프 전략
```python
if 모든_키가_Rate_Limit:
    60초 대기
    다시 Key1부터 시도
```

---

## 📊 통계 모니터링

### 로그 확인
```bash
# Data Engine 로그
docker logs qjs-data-engine | grep "API Key"

# 예시 출력
🔑 API Key Rotator 초기화: 3개 키 로드됨
⚠️ Rate Limit 감지: 96TG...CLBE (total: 1회)
다음 키로 전환 (대기 없음)
🛑 모든 키가 Rate Limit 상태. 60초 대기...
📊 API Key 사용 통계: 75회 호출, 성공률: 98.67%, Rate Limit: 3회
```

### 통계 항목
- **total_calls**: 전체 API 호출 횟수
- **successful_calls**: 성공한 호출
- **failed_calls**: 실패한 호출
- **rate_limited_count**: Rate Limit 감지 횟수
- **success_rate**: 성공률 (%)

---

## 🚀 사용 예시

### 종목당 뉴스 수집 시간 계산

```python
# 키 1개: 5 calls/min → 12초 대기
35개 종목 × 12초 = 7분

# 키 3개: 라운드 로빈 → 대기 최소화
35개 종목 ÷ 3 = 12개씩 처리
12 × 12초 = 2.4분 (약 3배 빠름)
```

### 일일 수집 가능 종목

```python
# 키 1개: 25 calls/day
최대 25개 종목

# 키 3개: 75 calls/day
최대 75개 종목 (현재 35개 → 여유)

# 키 5개: 125 calls/day
최대 125개 종목 (확장 가능)
```

---

## 🛠️ 트러블슈팅

### 문제: "No API keys configured"
```bash
# 원인: .env.common에 키가 없음
# 해결: 키 추가 후 Docker 재시작
make down && make local
```

### 문제: "모든 키가 Rate Limit"
```bash
# 원인: 일일 제한 도달
# 해결1: 추가 키 발급
# 해결2: 내일까지 대기 (UTC 기준 리셋)
```

### 문제: "API key invalid"
```bash
# 원인: 잘못된 키 입력
# 해결: https://www.alphavantage.co/support/#api-key 에서 재확인
```

---

## 📋 체크리스트

- [ ] Alpha Vantage 사이트에서 키 발급
- [ ] `.env.common`에 키 추가 (`ALPHA_VANTAGE_API_KEY_1`, `_2`, ...)
- [ ] Docker 재시작 (`make down && make local`)
- [ ] 로그에서 "🔑 API Key Rotator 초기화: N개 키 로드됨" 확인
- [ ] 감정 분석 실행 후 통계 확인

---

## 💡 Best Practices

1. **최소 3개 키 권장**: 일일 75 calls (여유)
2. **Gmail alias 활용**: 추가 이메일 계정 불필요
3. **.env.common 사용**: 개발/운영 환경 공통 관리
4. **로그 모니터링**: Rate Limit 발생 시 키 추가 검토

---

## 🔗 관련 문서

- [Alpha Vantage API Documentation](https://www.alphavantage.co/documentation/)
- [News Sentiment API](https://www.alphavantage.co/documentation/#news-sentiment)
- [API Key 관리](https://www.alphavantage.co/support/#api-key)
