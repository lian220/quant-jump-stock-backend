# Jira 티켓 시작

**사용법**:
- `jira:start LAD-42 [브랜치명]` - 티켓 ID 지정
- `jira:start` - 현재 브랜치명에서 티켓 ID 자동 추출

티켓 ID와 브랜치명을 받아 개발 환경을 설정하고 티켓 상태를 자동으로 업데이트합니다.

## 인자
- 첫 번째: Jira 티켓 ID (선택, 예: LAD-42)
  - **생략 시**: 현재 브랜치명에서 자동 추출 (예: `feature/LAD-42-xxx` → `LAD-42`)
- 두 번째: 브랜치명 (선택, 예: region-selection-map)
  - 없으면 티켓 제목 기반으로 자동 생성

## 실행 내용

$ARGUMENTS

### 단계 0: 티켓 ID 결정 (인자가 없는 경우)
**인자가 없으면 현재 브랜치명에서 티켓 ID를 자동 추출**:
1. `git branch --show-current`로 현재 브랜치명 확인
2. 브랜치명에서 티켓 ID 패턴 추출 (정규식: `LAD-\d+`)
   - 예: `feature/LAD-42-implement-region-map` → `LAD-42`
   - 예: `LAD-53-docker-setup` → `LAD-53`
3. 추출 실패 시 사용자에게 티켓 ID 입력 요청
4. 추출 성공 시 해당 티켓 ID로 진행

### 단계 1: 티켓 정보 확인 및 상태 변경
1. Jira 티켓 정보 조회 (mcp__atlassian__getJiraIssue)
2. 티켓 제목, 설명, 수락 조건 확인
3. 티켓 현재 상태 확인
4. **티켓 상태 자동 전환**:
   - 현재 상태가 "해야 할 일"(To Do) → "진행 중"(In Progress)로 전환
   - 이미 "진행 중"이면 상태 유지
   - 완료된 티켓은 경고 메시지 표시
   - mcp__atlassian__getTransitionsForJiraIssue로 가능한 전환 확인
   - mcp__atlassian__transitionJiraIssue로 상태 전환 실행
5. 브랜치명이 없는 경우 (새 브랜치 생성 시에만):
   - 티켓 제목을 kebab-case로 변환
   - 형식: `feature/{티켓ID}-{티켓제목-kebab-case}`
   - 예: `feature/LAD-42-implement-region-selection-map`

### 단계 2: 브랜치 생성 및 전환
1. 현재 git 상태 확인
2. main/develop 브랜치에서 최신 코드 pull
3. **브랜치 생성 규칙 (MANDATORY)**:
   - ✅ **반드시 이 형식 사용**: `feature/{티켓ID}-{티켓제목-kebab-case}`
   - ✅ 예시: `feature/LAD-42-implement-region-selection-map`
   - ❌ **절대 사용 금지**: `사용자명/티켓ID` (예: `imdoyeong/LAD-42`)
   - ❌ **절대 사용 금지**: `feature/{티켓ID}` (제목 없이 ID만)
   - 티켓 제목은 반드시 kebab-case로 변환
   - 브랜치명에 사용자 이름이나 특수문자 포함 금지
4. 브랜치로 전환
5. 관련 파일/컴포넌트 파악
6. 작업 범위 요약 제공

### 출력
- 생성된 브랜치명
- 티켓 요약 정보
- **티켓 상태 변경 확인** (해야 할 일 → 진행 중)
- 수락 조건 (Acceptance Criteria) 목록
- 예상 작업 파일 목록
- 다음 단계 안내

## 출력 예시

```markdown
✅ 티켓 LAD-42 시작 준비 완료!

📋 티켓 정보:
- ID: LAD-42
- 제목: Implement region selection map
- 상태: 진행 중 ✅ (자동으로 변경됨: 해야 할 일 → 진행 중)
- 브랜치: feature/LAD-42-implement-region-selection-map

📝 수락 조건 (Acceptance Criteria):
- [ ] AC 1: 사용자가 지도에서 지역을 선택할 수 있다
- [ ] AC 2: 선택한 지역이 하이라이트된다

📂 예상 작업 파일:
- frontend/src/components/RegionMap.tsx
- backend/src/presentation/rest/region/RegionController.kt

🚀 다음 단계:
1. 코드 구현 시작
2. 커밋 시 "[LAD-42]" 포함
3. 완료 후 `jira-complete LAD-42` 실행
```
