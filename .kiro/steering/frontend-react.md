---
sourceId: frontend-react
sourceVersion: "1.1"
sourceUpdatedAt: 2026-08-27
inclusion: fileMatch
fileMatchPattern: "**/*.tsx,**/*.jsx,**/*.ts"
---

# Frontend React 규칙

## 1. 함수형 컴포넌트만 사용

```tsx
function Card({ title, description }: CardProps) {
  return (
    <div className="rounded-lg p-4">
      <h3 className="text-lg font-bold">{title}</h3>
      <p className="text-gray-400">{description}</p>
    </div>
  );
}
```

## 2. 컴포넌트 파일 구조

```tsx
// 1. imports (React → 외부 라이브러리 → 내부 모듈 순)
// 2. types (Props interface)
// 3. component (hooks → handlers → render)
// 4. export default
```

## 3. 네이밍 규칙

- 컴포넌트: PascalCase (`UserCard.tsx`)
- hooks: camelCase, `use` 접두사 (`useAuth.ts`)
- 유틸리티: camelCase (`formatDate.ts`)
- 타입/인터페이스: PascalCase (`UserResponse`)
- 상수: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)

## 4. Tailwind CSS

- 유틸리티 클래스 우선 사용
- 반복되는 스타일은 컴포넌트로 추출 (CSS 추출 지양)
- 인라인 스타일 사용 금지

## 5. 상태 관리

| 유형 | 도구 | 용도 |
|------|------|------|
| 로컬 UI | useState | 단순 토글, 입력값 |
| 복잡 로컬 | useReducer | 다단계 폼, 복잡한 상태 전이 |
| 서버 상태 | @tanstack/react-query | API 데이터 캐싱, 로딩/에러 상태 |
| 전역 상태 | zustand | 인증 정보, 앱 전역 상태 등 |

## 6. API 호출 규칙

```tsx
// services/resourceApi.ts
const API_BASE = "/api/v1";

export async function getResources(): Promise<Resource[]> {
  const res = await fetch(`${API_BASE}/resources`);
  if (!res.ok) throw new Error("Failed to fetch resources");
  return res.json();
}
```

- API 호출 함수는 `services/` 디렉토리에 도메인별로 분리
- 컴포넌트에서 직접 fetch 호출 금지 — 서비스 레이어 경유

## 7. TypeScript 규칙

- `any` 타입 사용 금지 (불가피한 경우 주석으로 사유 명시)
- 타입 단언(`as`) 최소화, 타입 가드 활용
- Props: 같은 파일 내 interface 정의
- API 응답/공유 타입: `types/` 디렉토리

## 8. 피해야 할 패턴

- `any` 타입 남용
- useEffect 내 직접 API 호출 → 커스텀 hook 또는 react-query로 분리
- props drilling 3단계 이상 → Context 또는 composition 패턴
- index.ts barrel 파일 남용 → 순환 참조 위험
- `console.log` 프로덕션 코드에 남기기
- 인라인 스타일 사용 → Tailwind 사용

## 9. 디자인 문서 참조 규칙

FE 컴포넌트 구현 시 반드시 대응하는 디자인 문서를 먼저 확인하라.

### 참조 파일

- 디자인 토큰: #[[file:docs/design/shared/tokens.css]]
- 공통 컴포넌트 CSS: #[[file:docs/design/shared/components.css]]
- 레이아웃 기본: #[[file:docs/design/shared/base.css]]

### 화면별 매핑

| FE 경로 패턴 | 참조 디자인 (cases.md → HTML) |
|-------------|-------------------------------|
| pages/Login*, components/auth/* | docs/design/web/login.cases.md |
| pages/Chat*, components/chat/*, components/message/* | docs/design/web/chat.cases.md |
| components/panel/*, components/sidebar/* | docs/design/web/panel.cases.md |
| pages/Recipe*, components/recipe/* | docs/design/web/recipe-editor.cases.md |

### 구현 순서

1. 해당 화면의 `cases.md` 읽기 — 모든 케이스/상태 파악
2. 해당 화면의 `.html` 열어서 구조 확인
3. tokens.css 변수를 Tailwind로 매핑하여 사용
4. components.css의 클래스 구조를 React 컴포넌트로 1:1 변환

### 토큰 → Tailwind 매핑 규칙

| CSS 변수 | Tailwind 사용 방식 |
|----------|-------------------|
| --color-bg-primary | `bg-[var(--color-bg-primary)]` 또는 tailwind.config에서 커스텀 |
| --space-4 | `p-4` (1rem = 16px 동일) |
| --radius-lg | `rounded-xl` (12px ≈ 0.75rem) |

- tailwind.config.ts에서 tokens.css 변수를 커스텀 테마로 등록하는 것을 권장
- 하드코딩된 색상값/간격값 사용 금지 — 토큰 경유 필수

## 10. 프로젝트 디렉토리 구조

```
src/
├── components/
│   ├── ui/          # 재사용 UI 컴포넌트
│   └── layout/      # 레이아웃 컴포넌트
├── pages/           # 페이지 컴포넌트
├── hooks/           # 커스텀 hooks
├── services/        # API 호출 레이어
├── stores/          # Zustand 상태 관리
├── types/           # TypeScript 타입 정의
├── constants/       # 상수
└── utils/           # 유틸리티 함수
```

## 11. React Server Components (Next.js App Router 사용 시)

> 이 섹션은 Next.js App Router를 사용하는 프로젝트에만 적용한다.
> Vite + React SPA 프로젝트라면 이 섹션은 무시한다.

### 핵심 원칙

**기본값은 Server Component. Client Component는 필요할 때만.**

| 상황 | 컴포넌트 타입 |
|------|-------------|
| 데이터 fetch, DB 직접 접근 | Server |
| 정적 UI 렌더링 (상태 없음) | Server |
| useState, useEffect, useRef 필요 | Client (`'use client'`) |
| 이벤트 핸들러 (onClick, onChange 등) | Client |
| 브라우저 API 사용 (localStorage, window) | Client |

### 피해야 할 패턴 (RSC)

- 전체 페이지를 `'use client'`로 감싸기
- Server Component에서 useState/useEffect 사용 시도
- 불필요한 client boundary
