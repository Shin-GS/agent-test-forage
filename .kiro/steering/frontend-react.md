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

## 9. 프로젝트 디렉토리 구조

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

## 10. React Server Components (Next.js App Router 사용 시)

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
