# ADR 0001 — Kafka -> GCP Pub/Sub 전환

> 작성: 2026-05-24 (소급 기록)
> **Accepted: 2026-02-15**

## 배경

초기 아키텍처에서 Apache Kafka + Zookeeper를 메시지 브로커로 사용했다.

### 문제

| 항목 | 상세 |
|------|------|
| 운영 부담 | Kafka + Zookeeper 클러스터를 직접 관리해야 하며, 1인 운영 환경에서 장애 대응이 불가능 |
| 리소스 사용량 | Kafka 브로커 + Zookeeper 합산 메모리 1GB 이상 상시 점유 |
| Docker Compose 복잡성 | 로컬 개발 환경에서도 Kafka/Zookeeper 컨테이너가 필요하여 docker-compose 파일 비대화 |
| 비용 | VM 기반 운영 시 Kafka 전용 리소스에 대한 고정 비용 발생 |
| scale-to-zero 불가 | Kafka는 항상 실행 상태를 유지해야 하므로 서버리스 아키텍처와 양립 불가 |

## 결정

**GCP Pub/Sub (관리형 메시지 서비스)로 전환한다.**

- Push 구독과 Pull 구독을 혼합하여 사용
- Core API와 Data Engine 간 이벤트 메시징에 Pub/Sub 활용
- 로컬 개발 환경에서는 Pub/Sub 에뮬레이터 사용 (`localhost:8681`)
- Pub/Sub 토픽/구독 정의는 Terraform IaC로 관리

### 구현 요약

```
변경 전: Producer → Kafka Broker (+ Zookeeper) → Consumer
변경 후: Producer → GCP Pub/Sub Topic → Subscription → Consumer
```

- `adapter/output/messaging/`: Pub/Sub 프로듀서
- `adapter/input/messaging/`: Pub/Sub 컨슈머
- `docker-compose.yml`: Kafka/Zookeeper 제거, Pub/Sub 에뮬레이터 추가

## 결과

### 긍정적

- **운영 부담 제거**: 관리형 서비스이므로 브로커 장애 대응, 파티션 리밸런싱 등 운영 작업 불필요
- **비용 절감**: 사용량 기반 과금, 유휴 시 비용 거의 발생하지 않음
- **scale-to-zero 가능**: Cloud Run과 자연스럽게 통합되어 서버리스 아키텍처에 적합
- **docker-compose 단순화**: Kafka/Zookeeper 2개 컨테이너 제거, 에뮬레이터 1개로 대체
- **GCP 생태계 통일**: Cloud Scheduler, Cloud Run 등 다른 GCP 서비스와 네이티브 통합

### 부정적

- **GCP 종속성 증가**: 다른 클라우드 이전 시 메시징 레이어 전면 교체 필요
- **에뮬레이터 제약**: Pub/Sub 에뮬레이터는 프로덕션 기능 일부 미지원 (dead letter 등)

## 검토한 대안

| 대안 | 불채택 사유 |
|------|------------|
| RabbitMQ | GCP에서 관리형 서비스가 없어 직접 운영 필요. Kafka와 동일한 운영 부담 문제 반복 |
| Amazon SQS/SNS | GCP 통일 원칙 위반. 멀티 클라우드 관리 복잡성 증가 |
| Kafka (유지) | 1인 운영에서 Kafka 클러스터 안정성 보장 불가. 비용 대비 효용 낮음 |
| Redis Streams | 메시지 영속성과 재처리 보장이 Pub/Sub 대비 약함 |

## 결정 이력

| 날짜 | 변경 |
|------|------|
| 2026-02-15 | Kafka → GCP Pub/Sub 전환 완료, Accepted |
| 2026-05-24 | ADR 소급 작성 |
