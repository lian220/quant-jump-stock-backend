# Backend 기술 개선사항

## 아키텍처 개선
- [ ] 헥사고날 아키텍처 마이그레이션 (현재 65/100 → 목표 100/100)
  - 상세: [BACKEND_ARCHITECTURE.md](./docs/architecture/refactor/BACKEND_ARCHITECTURE.md)
- [ ] ArchUnit 위반 해소 (27건 → 0건)
- [ ] Application → JPA 직접 의존성 제거 (11개 서비스)

## 데이터
- [ ] Stock 데이터 PostgreSQL 마이그레이션 후속 (Adapter 패턴 적용)
- [ ] MongoDB 단계적 제거 (dual-write → RDB only → 삭제)

## 기능
- [ ] Vertex AI CustomJob 파라미터 기능 추가
- [ ] 이메일/전화번호 인증 구현

## 성능
- [ ] JPA N+1 쿼리 최적화
- [ ] Kafka consumer 안정성 개선

## 테스트
- [ ] 단위 테스트 커버리지 (0% → 80%)
