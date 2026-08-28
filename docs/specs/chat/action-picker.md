---
status: confirmed
last-updated: 2026-08-27
---

# 액션 피커

## 개요

채팅 input 영역에 겹쳐 노출되는 구조화된 입력 UI. AI가 레시피 실행에 필요한 데이터를 수집할 때 또는 실행 중 사용자 입력이 필요할 때 표시.

## 동작 규칙

- AI가 액션 피커를 띄우는 질문 → 사용자가 답변하지 않은 상태에서만 유효
- 사용자가 채팅 input으로 직접 메시지를 보내면 → 액션 피커 자동 닫힘
- 취소 클릭 → AI "취소되었습니다" 응답 → 채팅 input 복귀
- 취소 버튼 상시 노출

## 필드 타입

| 필드 타입 | 설명 | 예시 |
|-----------|------|------|
| `text` | 단일 텍스트 | 상품명, 이메일 |
| `number` | 숫자 | 수량, 금액 |
| `textarea` | 여러 줄 텍스트 | 메모, 설명 |
| `select` | 단일 선택 | 카테고리, 상태값 |
| `multi-select` | 복수 선택 | 태그, 옵션 |
| `radio` | 라디오 | 2~4개 선택지 |
| `checkbox` | 체크박스 | 동의, on/off |
| `date` | 날짜 선택 | 시작일, 종료일 |
| `search-select` | 검색형 선택 (대량 옵션) | 공고 검색, 상품 검색 |
| `json` | JSON 에디터 | 복잡한 구조 직접 입력 |

## 변수 정의 스키마

레시피의 사용자 입력 스텝에서 정의하며, 실행 시 액션 피커로 렌더링됨:

```json
{
  "variables": [
    {
      "key": "productId",
      "label": "상품 ID",
      "type": "text",
      "required": true,
      "placeholder": "상품 ID를 입력하세요"
    },
    {
      "key": "quantity",
      "label": "수량",
      "type": "number",
      "required": true,
      "default": 1,
      "min": 1,
      "max": 100
    },
    {
      "key": "category",
      "label": "카테고리",
      "type": "select",
      "required": true,
      "options": [
        { "label": "식품", "value": "food" },
        { "label": "전자기기", "value": "electronics" }
      ]
    }
  ]
}
```

## search-select 상세

대량 옵션(수천 개 이상)을 처리하기 위한 검색형 선택:

- 사용자 타이핑 → debounce 후 서버 검색
- 필터링된 결과 노출 → 선택
- `optionsFrom` 필드로 이전 스텝 결과를 데이터 소스로 지정 가능
