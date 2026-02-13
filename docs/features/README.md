# 기능 개발 로드맵

통합 주식 플랫폼 개발을 위한 기능 분석 및 구현 계획

## 분석 기능 상세 문서

스케줄러 기반 분석 파이프라인의 기능별 상세 문서입니다.

- **[ANALYSIS_ARCHITECTURE.md](./ANALYSIS_ARCHITECTURE.md)** - 분석 기능 전체 개요 및 인덱스
- **[데이터수집.md](./데이터수집.md)** - 경제 데이터 수집 (FRED + Yahoo Finance)
- **[기술적분석.md](./기술적분석.md)** - 기술적 지표 분석 (SMA, RSI, MACD)
- **[뉴스감정분석.md](./뉴스감정분석.md)** - 뉴스 감정 분석 (Alpha Vantage)
- **[AI분석.md](./AI분석.md)** - Vertex AI 주가 예측 (Transformer 모델)

## 플랫폼 분석 및 로드맵

- **[PLATFORM_ANALYSIS.md](./PLATFORM_ANALYSIS.md)** - 4개 플랫폼(Seeking Alpha, RiverQuant, 알파스퀘어, QuantKit) 상세 분석
- **[FEATURE_ROADMAP.md](./FEATURE_ROADMAP.md)** - Phase별 개발 로드맵 및 타임라인
- **[PHASE_1_SPECS.md](./PHASE_1_SPECS.md)** - Phase 1 (핵심 기능) 상세 구현 스펙
- **[PHASE_2_SPECS.md](./PHASE_2_SPECS.md)** - Phase 2 (경쟁 기능) 상세 구현 스펙
- **[PHASE_3_SPECS.md](./PHASE_3_SPECS.md)** - Phase 3 (차별화 기능) 상세 구현 스펙

## 프로젝트 목표

4개 주식 플랫폼의 장점을 결합한 통합 플랫폼 구축:

- **Seeking Alpha**: 글로벌 분석, Quant Rating, 전문가 인사이트
- **RiverQuant**: AI 자동매매, 24시간 운영, 감정 제거
- **알파스퀘어**: 한국 시장 최적화, 사용자 친화적 UI
- **QuantKit**: 검증된 전략 라이브러리, 고급 리스크 관리

## 개발 단계 개요

### Phase 1: 핵심 기능 (8주) - MVP
- 완전 자동매매 시스템
- 고급 리스크 관리
- 백테스팅 & 성과 검증
- 실시간 모니터링 & 알림

### Phase 2: 경쟁 기능 (12주)
- 커뮤니티 플랫폼
- 초보자 교육 & 온보딩
- 포트폴리오 최적화
- 커스텀 전략 구축

### Phase 3: 차별화 기능 (12주)
- 글로벌 펀더멘탈 분석
- 다중 시장 통합 (국내+해외+암호화폐)
- 팀 협업 기능
- 고급 리포트 생성

---

**마지막 업데이트**: 2026-02-13
