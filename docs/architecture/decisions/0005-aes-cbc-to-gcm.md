# ADR 0005 — AES-CBC -> AES-GCM 암호화 전환

> 작성: 2026-05-24 (소급 기록)
> **Accepted: 2026-05 (Phase 1A 보안 PRE 과정)**
> 관련 브랜치: `feature/phase-1a-security-pre`

## 배경

KIS API 키 등 민감 정보를 `UserKisAccountService`에서 AES-CBC 모드로 암호화하여 PostgreSQL에 저장하고 있었다.

### 문제

| 항목 | 상세 |
|------|------|
| 패딩 오라클 공격 | CBC 모드는 패딩 오라클 공격(Padding Oracle Attack)에 취약. 암호문 조작으로 평문 유추 가능 |
| 무결성 검증 없음 | CBC는 기밀성만 제공하고 무결성(integrity)을 보장하지 않음. 암호문 변조 탐지 불가 |
| 인증 부재 | 암호화와 인증이 분리되어 있어 Encrypt-then-MAC 등 별도 구현 필요 |
| 업계 권고 | NIST, OWASP 등에서 CBC 대신 AEAD(인증된 암호화) 모드 사용을 권고 |

## 결정

**AES-GCM(Galois/Counter Mode)으로 전환한다. 기존 CBC는 v1으로, GCM은 v2로 버전 관리한다.**

- 신규 암호화는 모두 AES-GCM(v2)으로 수행
- 기존 AES-CBC(v1)로 암호화된 데이터는 복호화만 지원 (하위 호환)
- 암호문에 버전 프리픽스를 부여하여 v1/v2 자동 판별
- 기존 데이터는 복호화 -> GCM 재암호화로 자동 마이그레이션 지원

### 구현 요약

```
변경 전: 평문 → AES-CBC 암호화 (PKCS5Padding) → 암호문 저장
변경 후: 평문 → AES-GCM 암호화 (AEAD) → [v2 프리픽스 + IV + 암호문 + AuthTag] 저장
```

- 버전 판별: 암호문 프리픽스로 v1(CBC) / v2(GCM) 자동 구분
- 복호화 흐름: v1이면 CBC로 복호화, v2이면 GCM으로 복호화
- 암호화 흐름: 항상 GCM(v2)으로 암호화
- v1 -> v2 마이그레이션: 복호화(CBC) -> 재암호화(GCM)로 점진적 전환

## 결과

### 긍정적

- **AEAD 보장**: 인증된 암호화(Authenticated Encryption with Associated Data)로 기밀성 + 무결성 동시 보장
- **패딩 오라클 방어**: GCM은 스트림 암호 기반이라 패딩 자체가 없어 패딩 오라클 공격 원천 차단
- **변조 탐지**: GCM의 Authentication Tag로 암호문 변조 시 즉시 감지 (DecryptionException 발생)
- **하위 호환**: v1(CBC) 데이터도 복호화 가능하여 기존 데이터 손실 없음
- **점진적 마이그레이션**: 서비스 중단 없이 v1 -> v2 자동 전환 가능
- **JVM 네이티브**: `javax.crypto` 패키지의 AES/GCM/NoPadding으로 외부 라이브러리 없이 구현

### 부정적

- **복잡성 증가**: v1/v2 버전 분기 로직이 암/복호화 코드에 추가
- **전환 기간**: 모든 데이터가 v2로 마이그레이션 완료될 때까지 v1 복호화 코드 유지 필요
- **IV 관리**: GCM에서는 동일 키로 IV(Nonce)를 절대 재사용하면 안 됨. 매 암호화마다 SecureRandom 생성 필수

## 검토한 대안

| 대안 | 불채택 사유 |
|------|------------|
| HashiCorp Vault | 별도 인프라 운영 필요. 1인 운영 환경에서 Vault 클러스터 관리 부담. 비용 증가 |
| AWS KMS | GCP 통일 원칙 위반. 멀티 클라우드 키 관리 복잡성 증가 |
| GCP Cloud KMS | 가능한 대안이지만, 현재 암호화 볼륨에서는 오버킬. 추후 키 로테이션 필요 시 검토 |
| libsodium (NaCl) | JVM에서 네이티브 지원 부족. JNI 바인딩 또는 별도 라이브러리 필요 |
| AES-CBC + HMAC | Encrypt-then-MAC 패턴으로 무결성 보완 가능하지만, GCM이 단일 알고리즘으로 더 간결 |

## 결정 이력

| 날짜 | 변경 |
|------|------|
| 2026-05 | Phase 1A 보안 PRE 과정에서 AES-GCM 전환 완료, Accepted |
| 2026-05-24 | ADR 소급 작성 |
