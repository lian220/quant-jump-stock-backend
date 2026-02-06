# Quantiq Backend 문서

알고리즘 트레이딩 플랫폼 Quantiq Backend 문서입니다.

## 폴더 구조

```
docs/
├── todo/           # 현재 진행 중인 작업
├── architecture/   # 시스템 아키텍처
├── setup/          # 개발 환경 & 배포
├── features/       # 주요 기능 & 분석
├── database/       # 데이터베이스 스키마
├── guidelines/     # 개발 가이드라인
├── analysis/       # 데이터 분석 결과
├── kis/            # KIS API 연동
└── legacy/         # 아카이브 (완료된 작업)
    ├── todo/       # 완료된 TODO 항목
    └── migration/  # 마이그레이션 가이드 (완료)
```

## 빠른 시작

| 상황 | 문서 |
|------|------|
| 다음 작업 확인 | [todo/](./todo/) |
| 처음 시작 | [환경설정 가이드](./setup/환경설정_가이드.md) |
| 시스템 구조 이해 | [아키텍처](./architecture/시스템_아키텍처.md) |
| 배포 방법 | [배포 가이드](./setup/배포_운영_가이드.md) |
| 스케줄러 운영 | [스케줄러 가이드](./setup/스케줄러_운영_가이드.md) |

## 주요 섹션

### [TODO](./todo/)
현재 진행 중인 작업:
- `Phase3_스펙.md` - 자동 매매 시스템 (85% 완료)
- `기능_로드맵.md` - 전체 기능 개발 계획
- `다음_작업.md` - 즉시 수행할 작업

### [아키텍처](./architecture/)
- 시스템 아키텍처, 이벤트 기반 구조, DB 전략
- Kafka 이벤트 스키마, 스케줄러 구조

### [설정 및 배포](./setup/)
- 환경 설정, 인증, 환경변수 관리
- 배포 가이드, 스케줄러 운영, Slack 연동

### [데이터베이스](./database/)
- PostgreSQL + MongoDB 스키마
- 테이블/컬렉션 관계도

### [KIS API](./kis/)
- 한국투자증권 API 연동 가이드
- 사용자별 KIS 계정 관리

### [레거시](./legacy/)
완료된 작업 아카이브:
- `todo/` - 완료된 Phase 1-2, 마이그레이션 TODO
- `migration/` - MongoDB → PostgreSQL 마이그레이션 (완료)

## 현재 상태

### 완료됨
- Phase 1: 기본 인프라 구축
- Phase 2: Event-Driven Architecture
- PostgreSQL + MongoDB 하이브리드 DB
- 9개 Quartz Scheduler Job
- Vertex AI 예측 모델 통합

### 진행 중
- Phase 3: 자동 매매 시스템 (85%)
  - 경제 데이터, 기술적 분석, 감정 분석, AI 예측 완료
  - 실시간 매도 로직 진행 중

### 예정 (Phase 4)
- 실시간 시세 연동
- 포트폴리오 최적화
- 백테스팅 시스템

---

**마지막 업데이트**: 2026-02-06
