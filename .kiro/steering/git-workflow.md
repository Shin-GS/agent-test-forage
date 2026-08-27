---
sourceId: git-workflow
sourceVersion: "1.1"
sourceUpdatedAt: 2026-07-01
inclusion: always
---

# Git 워크플로우 규칙

## 1. 명시적 스테이징

- `git add -A` 사용 금지 — 변경 대상 파일만 명시적으로 스테이징
- 의도하지 않은 파일(임시 파일, 환경 설정 등)이 포함되는 것을 방지

```bash
# ❌ 금지
git add -A
git add .

# ✅ 허용
git add src/service/UserService.java
git add src/controller/UserController.java src/dto/UserResponse.java
```

## 2. 커밋 전 확인

- 커밋 전 `git diff --staged`로 스테이징된 변경 사항 확인
- untracked 파일 목록 확인 (`git status`)하여 필요한 파일 누락 방지
- lint, 테스트 통과 확인 후 커밋

```bash
# 커밋 전 체크리스트
git status              # untracked 파일 확인
git diff --staged       # 스테이징된 내용 리뷰
# lint / test 실행
git commit -m "feat: add user profile endpoint"
```

## 3. Untracked 파일 처리

- 새 파일 생성 시 의도적으로 스테이징 대상에 포함할지 판단
- `.gitignore`에 추가해야 할 파일: `.env`, IDE 설정, 빌드 산출물, `node_modules/`
- 의도적으로 커밋하지 않을 파일이 `git status`에 남아있으면 `.gitignore`에 등록

## 4. Conventional Commit 형식

커밋 메시지는 아래 형식을 따른다:

```
<type>(<scope>): <description>

[optional body]
[optional footer]
```

### Type 목록

| Type | 용도 |
|------|------|
| feat | 새 기능 추가 |
| fix | 버그 수정 |
| refactor | 리팩토링 (기능 변경 없음) |
| docs | 문서 변경 |
| style | 코드 포맷팅 (기능 변경 없음) |
| test | 테스트 추가/수정 |
| chore | 빌드, 설정 등 기타 변경 |
| perf | 성능 개선 |

### 예시

```bash
git commit -m "feat(auth): add login API endpoint"
git commit -m "fix(wallet): correct balance calculation rounding"
git commit -m "refactor(service): extract common validation logic"
git commit -m "chore: update gradle wrapper to 8.5"
```

### 규칙
- subject는 명령형, 소문자 시작, 마침표 없음
- 50자 이내로 간결하게 작성
- body가 필요한 경우 빈 줄로 분리 후 상세 설명
- breaking change가 있으면 footer에 `BREAKING CHANGE:` 명시

## 5. 커밋 분리

상세 기준은 `commit-granularity` rule 참조.

핵심 요약:
- 하나의 커밋 = 하나의 논리적 변경
- 기능 추가와 리팩토링을 같은 커밋에 섞지 않는다
- 각 커밋은 단독으로 빌드 통과해야 한다
