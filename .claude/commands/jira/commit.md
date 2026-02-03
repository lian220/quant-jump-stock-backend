# Jira 커밋 및 진행 업데이트

**사용법**:
- `jira:commit LAD-42 [커밋메시지]` - 티켓 ID 지정
- `jira:commit [커밋메시지]` - 현재 브랜치명에서 티켓 ID 자동 추출
- `jira:commit` - 티켓 ID와 커밋 메시지 모두 자동 생성

변경사항을 커밋하고 Jira 티켓 진행 상황을 업데이트합니다.

## 인자
- 첫 번째: Jira 티켓 ID (선택, 예: LAD-42, SCRUM-202)
  - **생략 시**: 현재 브랜치명에서 자동 추출 (예: `feature/SCRUM-202-xxx` → `SCRUM-202`)
- 두 번째: 커밋 메시지 (선택, 예: "feat: 지역 선택 맵 UI 구현")
  - 없으면 git diff 분석하여 자동 생성

## 실행 내용

$ARGUMENTS

### 단계 0: 티켓 ID 결정 (인자가 없는 경우)
**인자가 없거나 티켓 ID가 아닌 경우 현재 브랜치명에서 자동 추출**:
1. `git branch --show-current`로 현재 브랜치명 확인
2. 브랜치명에서 티켓 ID 패턴 추출 (정규식: `[A-Z]+-\d+`)
   - 예: `feature/SCRUM-202-implement-api` → `SCRUM-202`
   - 예: `LAD-53-docker-setup` → `LAD-53`
3. 추출 실패 시 사용자에게 티켓 ID 입력 요청
4. 추출 성공 시 해당 티켓 ID로 진행

### 단계 6: 변경사항 커밋
1. git status로 변경 파일 확인
2. git diff로 변경 내용 검토
3. 커밋 메시지가 없는 경우:
   - 변경된 파일과 diff 내용 분석
   - Conventional Commits 타입 자동 결정 (feat/fix/refactor 등)
   - scope 추출 (변경된 모듈/컴포넌트 기준)
   - 간결하고 명확한 메시지 자동 생성
   - 형식: `{type}({scope}): {message} [티켓ID]`
4. 관련 파일만 선택적 staging
5. Conventional Commit 형식으로 커밋
   - 예: `feat(map): 지역 선택 UI 구현 [LAD-42]`

### 단계 7: Jira 진행 업데이트
1. 티켓에 작업 로그 추가 (mcp__atlassian 사용)
2. 진행 상태 업데이트 (In Progress → In Review)
3. 커밋 해시 및 변경 요약 코멘트 추가

### 커밋 타입
- **feat**: 새로운 기능
- **fix**: 버그 수정
- **refactor**: 리팩토링
- **style**: 스타일 변경
- **docs**: 문서 수정
- **test**: 테스트 추가/수정
- **chore**: 기타 작업

### 출력
- 커밋 정보 (해시, 메시지)
- 변경된 파일 목록
- Jira 업데이트 상태
- 다음 단계 안내
