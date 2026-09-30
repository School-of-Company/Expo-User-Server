# Commit & PR Conventions

## Commit Message Format

`type(scope): description`

- **Type**: `feat` / `fix` / `refactor` / `docs` / `chore` / `test`
- **Scope**: 이 프로젝트의 도메인 이름. 고정 목록이 아니라 **레포가 이미 쓰는 어휘를 그대로** 쓴다

  ```bash
  git log --pretty=%s -200 | grep -oE '^[a-z]+\(([^)]+)\)' | sed -E 's/.*\((.*)\)/\1/' | sort | uniq -c | sort -rn
  ```

  히스토리에 어휘가 없으면 변경 경로에서 도메인 세그먼트를 뽑는다 — `domain/<name>/`이 곧 scope
  (예: `domain/auth/` → `auth`, `domain/admin/` → `admin`). 여러 도메인에 걸치면 `global`, 빌드·CI
  전용이면 `ci`

- **Description**: 한글, 명사형 종결, 마침표 없음
  - Good examples: `로그인 요청 검증 추가`, `관리자 승인 대기 목록 조회 구현`
- Subject line only (no body) — breaking change일 때만 예외적으로 본문에 `BREAKING CHANGE: <설명>` 추가

## PR Title Format

`description`

- 한글로 변경 내용을 직접 표현하고 대괄호 접두사를 붙이지 않는다
- `[server]`, `[user]`, `[global]`, `[ci/cd]` 같은 scope 표기는 PR 제목에 사용하지 않는다
- 커밋 메시지의 Conventional Commit scope 규칙은 그대로 유지한다

## Version Bump Rule (releases to `main`)

A `develop` → `main` PR's title is not a plain description but a bare `vX.Y.Z`. Decide which of X/Y/Z
to bump by looking at the commits going into `main` this time:

- **X (Major)** — an architecture change, or a large change to a major feature.
- **Y (Minor)** — a feature addition/change that changes the API spec. Backward compatibility is not
  guaranteed.
- **Z (Patch)** — a plain bug fix, refactor, or performance improvement with no API spec change.
  Backward compatibility must be guaranteed (bumping only Z must still work correctly).

Reset every field below the one you bump to 0 (e.g. bumping Minor resets Patch to 0). Bump `build.gradle.kts`'s
`version` to match, committed in the same PR.
