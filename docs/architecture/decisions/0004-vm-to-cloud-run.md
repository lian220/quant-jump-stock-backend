# ADR 0004 — GCE VM -> Cloud Run 전환

> 작성: 2026-05-24 (소급 기록)
> **Accepted: 2026-03-03**

## 배경

Core API를 GCE(Google Compute Engine) e2-medium VM 인스턴스에서 운영하고 있었다.

### 문제

| 항목 | 상세 |
|------|------|
| 고정 비용 | e2-medium VM 월 ~$30 고정 비용 (트래픽 유무와 무관) |
| 배포 프로세스 | SSH 접속 -> git pull -> 빌드 -> 재시작. 수동 배포로 인한 오류 가능성과 다운타임 |
| scale-to-zero 불가 | VM은 24/7 실행. 트래픽이 없는 시간에도 비용 발생 |
| 인프라 관리 | OS 패치, 디스크 관리, 방화벽 설정 등 VM 운영 부담 |
| 수평 확장 어려움 | 트래픽 증가 시 수동으로 인스턴스 추가 필요 |

## 결정

**모든 서비스를 Cloud Run으로 전환하여 All Cloud Run 아키텍처를 달성한다.**

- Core API: GraalVM Native Image로 빌드하여 Cloud Run에 배포
- 리소스: 1Gi 메모리 / 2 vCPU
- scale-to-zero 활성화 (min-instances=0)
- 배포 자동화: GitHub Actions -> Cloud Build -> Cloud Run

### 구현 요약

```
변경 전: GitHub → SSH 수동 배포 → GCE VM (e2-medium, 항상 실행)
변경 후: GitHub → GitHub Actions → Cloud Build → Cloud Run (scale-to-zero)
```

- All Cloud Run 아키텍처: Frontend, Backoffice, Core API, Data Engine 모두 Cloud Run
- Dockerfile: GraalVM Native Image 빌드 (멀티스테이지)
- CI/CD: `.github/workflows/` GitHub Actions 워크플로우
- 인프라: `terraform/` Cloud Run 서비스 정의

## 결과

### 긍정적

- **비용 절감**: $30/월 -> ~$5/월 수준 (scale-to-zero + 사용량 기반 과금)
- **배포 자동화**: `git push origin main` -> 자동 빌드 + 배포 (~10분). 수동 SSH 접속 불필요
- **All Cloud Run 아키텍처**: 4개 서비스 모두 동일한 배포/운영 모델로 통일
- **자동 수평 확장**: 트래픽 증가 시 Cloud Run이 자동으로 인스턴스 추가
- **인프라 관리 제거**: OS 패치, 디스크, 방화벽 등 VM 운영 작업 불필요
- **롤백 용이**: Cloud Run 리비전 기반 즉시 롤백 가능

### 부정적

- **콜드 스타트**: scale-to-zero에서 첫 요청 시 콜드 스타트 발생 (GraalVM Native Image로 완화, ~2-3초)
- **실행 시간 제한**: Cloud Run 요청 타임아웃 제한 (기본 300초, 최대 3600초)
- **로컬 상태 불가**: 파일 시스템이 ephemeral. 영속 상태는 외부 스토리지 필수
- **GCP 종속성**: Cloud Run 전용 설정 (서비스 어카운트, VPC 커넥터 등)

## 검토한 대안

| 대안 | 불채택 사유 |
|------|------------|
| Cloud Run Jobs | HTTP 서버 운영에 부적합. batch/일회성 작업용 설계 |
| GKE Autopilot | 1인 운영 프로젝트에 오버킬. 클러스터 관리 비용 + 학습 곡선 |
| App Engine | Cloud Run 대비 유연성 부족. 커스텀 런타임 제약 |
| GCE VM 유지 (비용 최적화) | scale-to-zero 불가, 수동 배포 문제 해결 불가. 근본적 한계 |

## 결정 이력

| 날짜 | 변경 |
|------|------|
| 2026-03-03 | GCE VM -> Cloud Run 전환 완료, Accepted |
| 2026-05-24 | ADR 소급 작성 |
