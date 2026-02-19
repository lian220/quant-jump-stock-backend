# 인증 아키텍처

> Alpha Foundry 인증 시스템 전체 흐름 문서
> 최종 업데이트: 2026-02-13

---

## 개요

Alpha Foundry는 **JWT 기반 인증**과 **Spring Security OAuth2 Client**를 사용합니다.

### 구현 현황

| 인증 방식 | 설명 | 상태 |
|-----------|------|------|
| ID/PW 로그인 | 자체 회원가입 + 비밀번호 로그인 | ✅ 운영 중 |
| Google OAuth2 | Google 계정 소셜 로그인 | ✅ 운영 중 |
| Naver OAuth2 | 네이버 계정 소셜 로그인 | ✅ 운영 중 (검수 진행) |
| JWT 토큰 인증 | HMAC-SHA256 서명, nimbus-jose-jwt | ✅ 운영 중 |
| JwtAuthenticationFilter | Bearer 토큰 → SecurityContext | ✅ 운영 중 |
| Spring Security 설정 | SecurityFilterChain + OAuth2 Client | ✅ 운영 중 |
| OAuth2 계정 자동 연결 | 이메일 기반 기존 계정 연결 | ✅ 운영 중 |
| 회원가입 중복 검사 | userId, email 중복 체크 | ✅ 운영 중 |
| phone 필드 | users 테이블 + Naver mobile 수집 | ✅ 운영 중 (V40) |
| 무료 티어 자동 생성 | 가입 시 FREE 티어 자동 부여 | ✅ 운영 중 |
| Frontend AuthContext | 로그인/로그아웃/세션 관리 | ✅ 운영 중 |
| Frontend API Proxy | Next.js API Routes 프록시 | ✅ 운영 중 |
| 이메일 인증 | 이메일 소유 확인 | 📋 미구현 (Phase 1) |
| 전화번호 인증 | SMS 인증번호 확인 | 📋 미구현 (Phase 2) |
| phone 중복 검사 | existsByPhone | 📋 미구현 |
| JWT 블랙리스트 | 로그아웃 시 서버 토큰 무효화 | 📋 미구현 |

---

## 시스템 구조

```
┌─────────────────────────────────────────────────────────────────┐
│                     Frontend (Next.js :3000)                    │
│                                                                 │
│  AuthContext.tsx ─── useAuth() 훅                               │
│  ├─ signIn(userId, password)     → /api/auth/login (proxy)     │
│  ├─ signUp(userId, email, ...)   → /api/auth/signup (proxy)    │
│  ├─ signOut()                    → /api/auth/logout (proxy)    │
│  ├─ signInWithGoogle()           → Backend 직접 리다이렉트     │
│  ├─ signInWithNaver()            → Backend 직접 리다이렉트     │
│  └─ validateSession()            → /api/auth/me (proxy)        │
│                                                                 │
│  토큰 저장: localStorage('auth_token')                          │
│  콜백 페이지: /auth/callback                                    │
└────────────────────────────┬────────────────────────────────────┘
                             │
                   API Proxy (Next.js API Routes)
                   /api/auth/* → /api/v1/auth/*
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                  Backend (Spring Boot :10010)                    │
│                                                                 │
│  SecurityConfig.kt                                              │
│  ├─ JwtAuthenticationFilter (Bearer 토큰 파싱 → SecurityContext)│
│  ├─ OAuth2 Login (Spring Security OAuth2 Client)                │
│  │   ├─ authorizationEndpoint: /api/v1/auth/oauth2/authorize   │
│  │   ├─ redirectionEndpoint:   /api/v1/auth/oauth2/callback/*  │
│  │   ├─ userInfoEndpoint:      CustomOAuth2UserService          │
│  │   └─ successHandler:        OAuth2AuthenticationSuccessHandler│
│  └─ SessionCreationPolicy.IF_REQUIRED (OAuth2 플로우 지원)      │
│                                                                 │
│  AuthController.kt                                              │
│  ├─ POST /api/v1/auth/login     → ID/PW 로그인                 │
│  ├─ POST /api/v1/auth/signup    → 회원가입                      │
│  ├─ GET  /api/v1/auth/me        → JWT 검증 + 사용자 정보 반환   │
│  ├─ POST /api/v1/auth/logout    → 로그아웃 (stateless)          │
│  └─ GET  /api/v1/auth/oauth2/urls → OAuth2 URL 목록            │
│                                                                 │
│  AuthService.kt                                                 │
│  ├─ login() → findUser → 비밀번호 검증 → JWT 발급              │
│  ├─ signup() → 중복 검사(userId, email) → 저장 → 무료 티어 생성│
│  ├─ validateToken() → JWT 파싱 → 사용자 조회                   │
│  └─ logout() → stateless (클라이언트 토큰 삭제)                │
│                                                                 │
│  JwtService.kt                                                  │
│  ├─ HMAC-SHA256 (nimbus-jose-jwt)                               │
│  ├─ Claims: sub(userId), email, role, dbId, exp, iss            │
│  └─ 설정: jwt.secret / jwt.expiration-hours / jwt.issuer        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 인증 흐름

### 1. ID/PW 로그인

```
Frontend                    Next.js API Route           Backend
────────                    ─────────────────           ───────
signIn(userId, pw)
  → POST /api/auth/login
                            → POST /api/v1/auth/login
                                                        AuthService.login()
                                                        ├─ findUser(userId or email)
                                                        ├─ passwordEncoder.matches()
                                                        ├─ status == ACTIVE 확인
                                                        └─ jwtService.generateToken()
                            ← { success, token, user }
  ← localStorage.setItem('auth_token', token)
     setUser(user)
```

### 2. OAuth2 로그인 (Google / Naver)

```
Frontend                         Backend                        OAuth Provider
────────                         ───────                        ──────────────
signInWithNaver()
  → window.location.href =
    backend/api/v1/auth/oauth2/authorize/naver
                                 Spring Security OAuth2 Client
                                 → 302 Redirect to Naver
                                                                로그인 + 동의
                                                                ← callback with code
                                 /api/v1/auth/oauth2/callback/naver
                                 ├─ code → accessToken 교환
                                 ├─ CustomOAuth2UserService.loadUser()
                                 │   ├─ 사용자 정보 추출 (email, name, phone, ...)
                                 │   └─ findOrCreateOAuthUser()
                                 │       ├─ 기존: provider+providerId로 조회
                                 │       ├─ 이메일 매칭: 기존 계정에 OAuth 연결
                                 │       └─ 신규: 새 계정 생성 + 무료 티어
                                 └─ OAuth2AuthenticationSuccessHandler
                                     ├─ jwtService.generateToken()
                                     └─ 302 Redirect → frontend/auth/callback?token=JWT

Frontend /auth/callback
  ├─ URL에서 token 추출
  ├─ localStorage.setItem('auth_token', token)
  ├─ GET /api/auth/me → 사용자 정보 조회
  └─ router.push('/') → 홈으로 이동
```

### 3. 세션 유지 (페이지 새로고침)

```
AuthContext useEffect (초기 로드)
  ├─ localStorage에서 token 확인
  ├─ 없으면 → loading = false (비로그인)
  └─ 있으면 → GET /api/auth/me
      ├─ 성공 → setUser(user)
      └─ 401/403 → 토큰 삭제 (세션 만료)
```

---

## 회원가입 필드

| 필드 | 필수 | 자체가입 | Google OAuth | Naver OAuth |
|------|------|----------|-------------|-------------|
| userId | Y | 사용자 입력 | 자동 생성 (`g_이름_랜덤`) | 자동 생성 (`n_이름_랜덤`) |
| email | Y | 사용자 입력 | Google 계정 이메일 | 네이버 계정 이메일 |
| password | Y(자체) | 사용자 입력 (6자+) | N/A | N/A |
| name | N | 선택 입력 | Google 이름 | 네이버 이름/닉네임 |
| phone | N | 선택 입력 | N/A | 네이버 연락처 (`response["mobile"]`) |
| profileImage | N | N/A | Google picture | 네이버 profile_image |

### 중복 검사
- **userId**: `userRepository.existsByUserId()` → "이미 사용 중인 아이디입니다"
- **email**: `userRepository.existsByEmail()` → "이미 사용 중인 이메일입니다"
- **phone**: 미구현 (향후 `existsByPhone` 추가 필요)

### OAuth 계정 연결
- 동일 이메일의 기존 계정이 있으면 자동 연결 (`User.linkOAuth()`)
- 동일 provider+providerId로 재로그인 시 기존 계정 반환
- Naver에서 phone 정보가 새로 들어오면 기존 사용자에게도 업데이트

---

## Naver OAuth 상세

### 스코프 및 수집 정보

```yaml
scope: name,email,profile_image,mobile
```

| 네이버 응답 필드 | 매핑 | 설명 |
|-----------------|------|------|
| `response.id` | providerId | 네이버 고유 ID |
| `response.email` | email | 이메일 |
| `response.name` | name | 이름 (없으면 nickname) |
| `response.profile_image` | profileImageUrl | 프로필 사진 URL |
| `response.mobile` | phone | 휴대전화번호 |

### 네이버 특이사항
- OIDC 미지원 → `application.yml`에 수동 provider 설정 필요
- 사용자 정보가 `response` 래퍼 필드 안에 있음
- `user-name-attribute: response` 으로 설정

### 네이버 검수 요구사항
- 최소한의 개인정보만 수집 (필요한 것만)
- 추가 정보 수집 시 사유 설명 필요
- 서비스 스크린샷 제출 필수 (로그인 화면, 동의 화면, 사용 화면)
- 이메일/비밀번호 찾기 기능은 필수 아님 (OAuth만 사용하는 경우)

---

## JWT 토큰

### 구조

```json
{
  "sub": "userId",        // 사용자 ID
  "email": "user@...",    // 이메일
  "role": "USER",         // 역할 (USER / ADMIN)
  "dbId": 123,            // DB PK
  "iss": "alpha-foundry", // 발급자
  "iat": 1707123456,      // 발급 시간
  "exp": 1707209856       // 만료 시간
}
```

### 설정

```yaml
jwt:
  secret: ${JWT_SECRET:quant-jump-stock-dev-secret-key-minimum-32-bytes}
  expiration-hours: 24
  issuer: alpha-foundry
```

### 보안 사항
- HMAC-SHA256 서명 (nimbus-jose-jwt)
- Secret 최소 32바이트 필요
- 개발용 기본 secret 사용 시 경고 로그 출력
- Stateless: 로그아웃 시 클라이언트에서 토큰 삭제 (서버 블랙리스트 미구현)

---

## API 엔드포인트 정리

### Backend API (`/api/v1/auth`)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/v1/auth/login` | ID/PW 로그인 | 불필요 |
| POST | `/api/v1/auth/signup` | 회원가입 | 불필요 |
| GET | `/api/v1/auth/me` | 현재 사용자 정보 | Bearer JWT |
| POST | `/api/v1/auth/logout` | 로그아웃 | Bearer JWT |
| GET | `/api/v1/auth/oauth2/urls` | OAuth2 URL 목록 | 불필요 |
| GET | `/api/v1/auth/oauth2/authorize/{provider}` | OAuth2 시작 (리다이렉트) | 불필요 |
| GET | `/api/v1/auth/oauth2/callback/{provider}` | OAuth2 콜백 (Spring 처리) | 불필요 |

### Frontend API Proxy (`/api/auth`)

| Frontend Route | Backend Target |
|----------------|----------------|
| POST `/api/auth/login` | POST `/api/v1/auth/login` |
| POST `/api/auth/signup` | POST `/api/v1/auth/signup` |
| GET `/api/auth/me` | GET `/api/v1/auth/me` |
| POST `/api/auth/logout` | POST `/api/v1/auth/logout` |
| POST `/api/auth/reset-password` | POST `/api/v1/auth/reset-password` |

### Security Filter Chain 접근 제어

```
/api/v1/admin/**        → ROLE_ADMIN 필수
/api/v1/auth/**         → permitAll
/api/v1/portfolios/**   → authenticated
/api/v1/strategies/*/default-stocks/** → authenticated
그 외                   → permitAll
```

---

## Frontend 인증 구조

### 주요 파일

| 파일 | 역할 |
|------|------|
| `src/contexts/AuthContext.tsx` | AuthProvider + useAuth 훅 |
| `src/types/auth.ts` | AuthUser, AuthContextType, LoginResponse 등 타입 |
| `src/app/auth/page.tsx` | 로그인/회원가입 페이지 |
| `src/app/auth/callback/page.tsx` | OAuth2 콜백 처리 페이지 |
| `src/app/api/auth/*/route.ts` | Backend API 프록시 라우트 |
| `src/app/mypage/page.tsx` | 마이페이지 (사용자 정보 표시) |
| `src/lib/api-client.ts` | Axios 인스턴스 (Bearer 토큰 자동 첨부) |

### AuthUser 타입

```typescript
interface AuthUser {
  userId: string;
  name?: string;
  email: string;
  phone?: string;
  role: string;     // "USER" | "ADMIN"
  status: string;   // "ACTIVE" | "SUSPENDED" | "WITHDRAWN"
}
```

### AuthContext 제공 기능

```typescript
interface AuthContextType {
  user: AuthUser | null;
  loading: boolean;
  signIn(userId, password): Promise<{ error? }>;
  signUp(userId, email, password, name?, phone?): Promise<{ error? }>;
  signOut(): Promise<void>;
  signInWithGoogle(): Promise<{ error? }>;
  signInWithNaver(): Promise<{ error? }>;
  resetPassword(email): Promise<{ error? }>;
}
```

---

## DB 스키마 (users 테이블)

```sql
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    user_id       VARCHAR(50)  UNIQUE NOT NULL,
    email         VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255),            -- OAuth 전용 사용자는 NULL
    name          VARCHAR(100),
    phone         VARCHAR(20),             -- V40 마이그레이션으로 추가
    status        VARCHAR(20) DEFAULT 'ACTIVE',
    role          VARCHAR(20) DEFAULT 'USER',
    oauth_provider    VARCHAR(20),         -- GOOGLE / NAVER
    oauth_provider_id VARCHAR(255),
    profile_image_url TEXT,
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);
```

---

## 향후 계획

### Phase 1: 이메일 인증 (우선)

**목적**: 이메일 소유 확인으로 서비스 신뢰도 향상

**구현 방식**: Spring Boot `JavaMailSender` + Gmail SMTP

**Backend 변경**:
1. DB: `email_verified BOOLEAN DEFAULT FALSE` 컬럼 추가
2. DB: `email_verifications` 테이블 (id, user_id, token, expires_at, verified_at)
3. `EmailService`: 인증 메일 발송
4. 엔드포인트:
   - `POST /api/v1/auth/send-verification-email`: 인증 이메일 발송
   - `GET /api/v1/auth/verify-email?token={token}`: 이메일 인증 처리

**Frontend 변경**:
1. 회원가입 후 "이메일을 확인해주세요" 안내 화면
2. 마이페이지에 인증 상태 표시 (인증됨/미인증)
3. "인증 이메일 재발송" 버튼

**의존성**: `spring-boot-starter-mail`

### Phase 2: 전화번호 인증 (이후)

**목적**: 전화번호 소유 확인

**구현 방식 후보**:
| 서비스 | 비용 | 특징 |
|--------|------|------|
| NHN Cloud SMS | 건당 ~9원 | 국내 안정적 |
| CoolSMS | 건당 ~16원 | 간편 연동 |
| Twilio | 건당 ~$0.0079 | 글로벌 |

**Backend 변경**:
1. DB: `phone_verified BOOLEAN DEFAULT FALSE` 컬럼 추가
2. DB: `phone_verifications` 테이블 (id, phone, code, expires_at, verified_at)
3. `SmsService`: SMS 발송
4. 엔드포인트:
   - `POST /api/v1/auth/send-phone-verification`: 인증번호 발송
   - `POST /api/v1/auth/verify-phone`: 인증번호 확인
5. Rate limiting: 동일 번호 하루 5회, IP 기반 제한

**Frontend 변경**:
1. 전화번호 입력 옆 "인증번호 발송" 버튼
2. 6자리 인증번호 입력 필드 + 3분 타이머
3. 마이페이지 인증 상태 표시

---

## 관련 파일 목록

### Backend

| 파일 | 경로 |
|------|------|
| SecurityConfig | `config/SecurityConfig.kt` |
| JwtService | `infrastructure/security/JwtService.kt` |
| JwtAuthenticationFilter | `config/JwtAuthenticationFilter.kt` |
| CustomOAuth2UserService | `infrastructure/security/CustomOAuth2UserService.kt` |
| OAuth2SuccessHandler | `infrastructure/security/OAuth2AuthenticationSuccessHandler.kt` |
| AuthController | `adapter/input/rest/auth/AuthController.kt` |
| AuthService | `application/auth/AuthService.kt` |
| User (도메인 모델) | `domain/model/user/User.kt` |
| OAuthProvider (도메인) | `domain/model/user/OAuthProvider.kt` |
| UserEntity (JPA) | `adapter/output/persistence/jpa/UserEntity.kt` |

### Frontend

| 파일 | 경로 |
|------|------|
| AuthContext | `src/contexts/AuthContext.tsx` |
| Auth 타입 | `src/types/auth.ts` |
| 로그인 페이지 | `src/app/auth/page.tsx` |
| OAuth 콜백 | `src/app/auth/callback/page.tsx` |
| 마이페이지 | `src/app/mypage/page.tsx` |
| API Proxy (login) | `src/app/api/auth/login/route.ts` |
| API Proxy (signup) | `src/app/api/auth/signup/route.ts` |
| API Proxy (me) | `src/app/api/auth/me/route.ts` |
| API Proxy (logout) | `src/app/api/auth/logout/route.ts` |
