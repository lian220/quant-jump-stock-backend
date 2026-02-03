# Jira 티켓 완료

**사용법**:
- `jira:complete LAD-42` - 티켓 ID 지정
- `jira:complete` - 현재 브랜치명에서 티켓 ID 자동 추출

최종 검증 후 PR을 생성하고 티켓을 완료 처리합니다.

## 인자
- 첫 번째: Jira 티켓 ID (선택, 예: LAD-42, SCRUM-202)
  - **생략 시**: 현재 브랜치명에서 자동 추출 (예: `feature/SCRUM-202-xxx` → `SCRUM-202`)
- 두 번째: PR 번호 (선택, 이미 PR이 있는 경우만)
  - 보통은 생략 (자동으로 PR 생성)

## 실행 내용

$ARGUMENTS

### 단계 0: 티켓 ID 결정 (인자가 없는 경우)
**인자가 없으면 현재 브랜치명에서 티켓 ID를 자동 추출**:
1. `git branch --show-current`로 현재 브랜치명 확인
2. 브랜치명에서 티켓 ID 패턴 추출 (정규식: `[A-Z]+-\d+`)
   - 예: `feature/SCRUM-202-implement-api` → `SCRUM-202`
   - 예: `LAD-53-docker-setup` → `LAD-53`
3. 추출 실패 시 사용자에게 티켓 ID 입력 요청
4. 추출 성공 시 해당 티켓 ID로 진행

### 단계 8: 최종 검증
1. 모든 테스트 통과 확인
2. 코드 품질 기준 충족 확인
3. 수락 조건(AC) 체크리스트 검토
4. 변경사항 최종 요약

### 단계 9: PR 생성 및 티켓 완료
1. 원격 브랜치로 push
2. PR 생성 (gh cli 사용)
   - 제목: `[티켓ID] {티켓 제목}`
   - 본문: 변경사항 요약, 테스트 결과, AC 체크리스트
3. Jira 티켓 상태 업데이트 (Done / In Review)
4. PR 링크를 Jira 티켓에 연결

### PR 템플릿 내용
```markdown
## Summary
- Jira 티켓: [티켓ID](티켓 링크)
- 변경 요약

## Changes
- 주요 변경사항 목록

## Test Plan
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] E2E 테스트 통과

## Acceptance Criteria
- [ ] AC 항목들...
```

### 출력
- PR URL
- 최종 변경사항 요약
- Jira 티켓 상태
- 리뷰어 할당 안내
