# Backend 문서

Backend 문서는 Core API/Data Engine의 구현 상세를 다룹니다.  
전사 정책/공통 아키텍처는 루트 [docs](../../docs/README.md)에서 관리합니다.

## 문서 구조

```
docs/
├── README.md
├── architecture/         # 백엔드 아키텍처/리팩토링
├── plans/                # 구현 계획
├── features/             # 기능 상세
├── database/             # DB 스키마
├── setup/                # 환경설정/배포/운영 (로컬·테스트·트러블슈팅 포함)
├── guidelines/           # 코딩/문서 가이드
├── analysis/             # 데이터 분석 결과
├── strategy/             # 전략/백테스트 관련
└── kis/                  # KIS API 연동
```

## 빠른 시작

| 상황 | 문서 |
|------|------|
| 개발 환경 구성 | [setup](./setup/) |
| 구조 이해 | [architecture](./architecture/) |
| 기능 구현 계획 확인 | [plans](./plans/) |
| 스키마 확인 | [database](./database/) |
| 테스트·로컬 실행 | [setup/로컬_테스트_가이드](./setup/로컬_테스트_가이드.md), [setup/](./setup/) |
| 장애 대응 | [setup/배포_운영_가이드](./setup/배포_운영_가이드.md) §트러블슈팅 |

## 섹션 안내

### Architecture
- 도메인 경계, 이벤트 흐름, 리팩토링 방향

### Features
- 백엔드 기능별 요구사항, 구현 상태, 제약사항

### Setup/Deployment
- 환경변수, 로컬 실행, 배포 절차, 운영 명령
- **프로덕션 배포**: Core API·Data Engine 모두 **Cloud Run** (2026-03-03). 상세는 루트 [docs/infra/현재_인프라.md](../../docs/infra/현재_인프라.md), [구글클라우드플랫폼_배포.md](../../docs/infra/구글클라우드플랫폼_배포.md) 참고.

### Setup (테스트·장애 대응 포함)
- 환경변수, 로컬 실행, 배포 절차, [로컬_테스트_가이드](setup/로컬_테스트_가이드.md), [배포_운영_가이드](setup/배포_운영_가이드.md) §트러블슈팅

## 루트 문서 연결

- [전사 문서 허브](../../docs/README.md)
- [공통 아키텍처](../../docs/architecture/)
- [API 계약](../../docs/api/)
- [인프라](../../docs/infra/)
- [테스트 규칙](../../docs/testing/테스트_규칙.md)
