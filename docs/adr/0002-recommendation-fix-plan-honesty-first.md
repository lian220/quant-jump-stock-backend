# 추천 시스템 수정 plan은 "정직성 우선"으로 한정한다

**Status**: proposed (2026-05-12) — (b) plan 코드 변경 미진행. (a) Incident 대응만 부분 완료: Vertex AI 운영 정상화 완료 ([정상화 문서](../analysis/ai/추천_파이프라인_정상화_2026-05-13.md)), (b) 정직성 패치(`_dynamic_weights` 제거, freshness 메타, sync_service fallback 차단 등)는 별도 PR로 재시작 필요. (c) 산식 재설계 trace는 미시작.

2026-02-27 이후 75일간 AI 적재가 정지된 사고에 대응하여 추천 시스템을 수정하되, 본 plan은 **(b) "Incident + 정직성 패치"** 범위로 한정한다. 데이터 freshness 도메인 규칙, 거짓 라벨링 제거, 응답 스키마에 freshness 메타데이터 추가, sync_service fallback 가짜 적재 제거, projection 결함 수정까지 포함. **각 점수 컴포넌트(Tech/AI/Sentiment)의 산식 자체에 대한 학술적 검증과 재설계는 본 plan에서 제외**하고, 별도 trace (c)로 분리한다.

산식 자체는 검증된 적이 없는 직관일 가능성이 매우 크지만 (`docs/analysis/ai/추천_점수_산식_검토_2026-05-12.md` 참조), 산식 검증은 *깨끗한 데이터*를 전제로 한다. 75일 묵은 데이터 + 가짜 라벨 위에서는 어떤 검증도 신뢰할 수 없으므로, **(b)가 정직성 인프라를 깐 다음 (c)에서 산식 자체를 검증**하는 순서로 분리.

## Considered Options

- **(a) Incident 대응만**: P0 차단 + Vertex AI 재가동 + 알람. 산식·라벨링 정책은 그대로 — 다음 사고가 같은 모양으로 재발.
- **(b) Incident + 정직성 패치 (채택)**: 산식은 그대로 유지하되 데이터 부재/stale에 대한 거짓말 제거. 1~2주 작업.
- **(c) 전면 재설계**: 컴포넌트 산식·가중치·regime detection까지 1~3개월. (b) 정직성 인프라 위에서 진행하는 것이 안전.

## Consequences

- (b) 완료 시점에 시스템은 *정직한 신호*만 발신. (c)에서 그 신호로 모은 데이터를 산식 검증 입력으로 사용.
- (b)와 (c) 사이 기간 동안 사용자에게 노출되는 추천은 *진정성은 보장되지만 산식 정당성은 미검증* 상태.
- (c) trace가 별도 ADR(미작성)로 확장 시 본 ADR을 superseded by 처리.
