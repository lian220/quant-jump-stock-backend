# Slack 사용자 공지안 — PR 3b (Negative AI Veto)

> 작성: 2026-05-22
> 머지 직전 Slack 분석 채널에 pinned 메시지로 1회 발송

## 공지 메시지 (한글)

```
📢 추천 알고리즘 개선 안내 (2026-05-XX 적용)

AI 모델이 음수 예측한 종목은 **추천 대상에서 자동 제외**됩니다.

**이전**: AI 음수 예측이어도 기술 분석/감정 분석 점수가 좋으면 강력 추천 표시
**변경**: AI 음수 예측 = 추천 차단 (composite 강제 0)

**이유**:
- 추천과 AI 신호의 정합성 향상
- "AI 하락 예측 + 강력 추천" 의 모순 출력 방지 (2026-05-20 사용자 의문 사건 대응)

**영향**:
- 일평균 추천 수: 약 5개 → 약 4-5개 (dry-run 결과)
- 약세장에 추천 0개인 날 발생 가능 (시장 신호 부족 시)
- 추천 0개 인 날: 평소 메시지 + "AI 하락 예측 차단 N개" 안내 포함

**투명성**:
- 매일 메시지에 "ℹ AI 하락 예측 차단: N개 종목" 정보 표시
- PostgreSQL `prediction_results.veto_reasons` 에 차단 사유 기록 (감사 추적)

**Rollback**:
- 부작용 발생 시 운영자 즉시 yaml flip + 재배포 (수분)
- 사용자 영향 없이 이전 동작 복귀 가능

**문의**: #data-engine 채널
```

## 적용 절차

1. PR 3b 머지 직전 운영자가 Slack 분석 채널 (C0A1XASTLH2) 에 위 메시지 발송
2. 메시지 pin 처리 (해당 채널 운영자)
3. 1 주일 후 unpin (자연 인식 후)

## 발송 명령 (참고)

```bash
# Slack webhook 으로 발송 (텍스트만, blocks 없이 단순)
curl -X POST -H 'Content-Type: application/json' \
  --data '{"text": "(위 본문)"}' \
  "$SLACK_WEBHOOK_URL_ANALYSIS"
```

또는 운영자가 Slack UI 직접 입력 (channel ops).

## 변경 이력

| 날짜 | 작성자 | 변경 |
|------|--------|------|
| 2026-05-22 | PR 3b | 초안 작성 |
