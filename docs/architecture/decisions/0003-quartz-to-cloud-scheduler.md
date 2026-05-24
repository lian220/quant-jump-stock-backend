# ADR 0003 — Quartz Scheduler -> GCP Cloud Scheduler 전환

> 작성: 2026-05-24 (소급 기록)
> **Accepted: 2026-02-28**

## 배경

Core API 내부에서 Quartz Scheduler를 사용하여 11개의 배치 Job을 실행하고 있었다.

### 문제

| 항목 | 상세 |
|------|------|
| JVM 메모리 점유 | Quartz 스레드풀이 상시 메모리를 점유하여 Core API 리소스 부담 증가 |
| QRTZ_* 테이블 | Quartz 메타데이터 테이블 11개가 PostgreSQL에 존재하여 DB 오버헤드 발생 |
| Cloud Run 비호환 | Cloud Run은 scale-to-zero 설계인데, Quartz는 JVM이 항상 살아있어야 Job이 실행됨. 근본적 양립 불가 |
| 스케줄 관리 | 스케줄 변경 시 코드 수정 + 재배포 필요. cron 표현식이 코드에 하드코딩 |
| 장애 가시성 | Job 실패 시 로그에만 기록되어 모니터링 어려움 |

## 결정

**GCP Cloud Scheduler로 전환하고, Quartz를 완전 제거한다.**

- Cloud Scheduler가 HTTP 트리거로 Core API 엔드포인트를 호출하거나 Pub/Sub 토픽에 메시지를 발행
- 일부 단순 Job은 Cloud Function으로 분리
- 스케줄 정의는 `terraform/scheduler.tf`에서 IaC로 관리
- Core API에 Cloud Scheduler 전용 HTTP 엔드포인트 추가

### 구현 요약

```
변경 전: Quartz (JVM 내부) → Job 클래스 직접 실행
변경 후: Cloud Scheduler → HTTP/Pub/Sub → Core API 엔드포인트 또는 Cloud Function
```

- 스케줄 정의: `terraform/scheduler.tf`
- HTTP 엔드포인트: `adapter/input/rest/scheduler/CloudSchedulerController.kt`
- 데이터 파이프라인: Cloud Scheduler -> Pub/Sub -> Data Engine (체이닝)
- QRTZ_* 테이블 삭제 마이그레이션 적용

## 결과

### 긍정적

- **Quartz 완전 제거**: 라이브러리 의존성, 스레드풀, QRTZ_* 테이블 모두 삭제
- **Cloud Run 호환**: scale-to-zero와 양립 가능. 스케줄 트리거 시에만 인스턴스 기동
- **IaC 관리**: 스케줄 변경이 Terraform 코드 변경으로 관리되어 코드 리뷰 + 버전 관리 가능
- **모니터링 개선**: Cloud Scheduler 대시보드에서 Job 실행 이력, 성공/실패 상태 확인 가능
- **DB 정리**: QRTZ_* 메타데이터 테이블 11개 삭제로 PostgreSQL 스키마 단순화

### 부정적

- **GCP 종속성 심화**: 스케줄링이 GCP Cloud Scheduler에 완전 의존
- **콜드 스타트 지연**: Cloud Run scale-to-zero 상태에서 스케줄 트리거 시 콜드 스타트 발생 가능 (수 초)
- **HTTP 엔드포인트 보안**: Cloud Scheduler 전용 엔드포인트에 대한 인증/인가 필요 (OIDC 토큰)

## 검토한 대안

| 대안 | 불채택 사유 |
|------|------------|
| Cloud Run Jobs | batch 작업용 설계로 cron 스케줄링에는 부적합. 별도 컨테이너 관리 필요 |
| 자체 cron (VM 기반) | VM 운영 부담. Cloud Run 전환 목표와 상충 |
| Quartz 유지 (최소 인스턴스 1) | Cloud Run min-instances=1 설정 시 비용 절감 효과 상실. 근본 해결이 아님 |

## 결정 이력

| 날짜 | 변경 |
|------|------|
| 2026-02-28 | Quartz 완전 제거, Cloud Scheduler 전환 완료, Accepted |
| 2026-05-24 | ADR 소급 작성 |
