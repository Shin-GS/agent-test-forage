// 첫 접속 온보딩 (디자인 명세 chat.html Case 1).
// - 🚀 빠른 시작 타이틀
// - quick-action 4개: 클릭 시 해당 문구를 그대로 전송(onQuickAction)
// - 자유 입력 힌트
// 데모 도메인(demo-shop, 커머스) 기준 예시 액션.

interface Props {
  onQuickAction: (content: string) => void;
}

// 커머스(demo-shop) 도메인 quick-action.
const QUICK_ACTIONS = [
  { label: "🛒 상품 주문 생성", prompt: "1번 상품 2개 주문해줘" },
  { label: "👤 테스트 회원 생성", prompt: "테스트 회원 하나 만들어줘" },
  { label: "💳 결제 테스트", prompt: "주문 결제 테스트 데이터 만들어줘" },
  { label: "↩️ 주문 취소", prompt: "주문 하나 만들고 취소까지 해줘" },
] as const;

export function Onboarding({ onQuickAction }: Props) {
  return (
    <div className="onboarding">
      <div className="onboarding__title">🚀 빠른 시작</div>
      <div className="onboarding__actions">
        {QUICK_ACTIONS.map((action) => (
          <button
            key={action.label}
            type="button"
            className="quick-action"
            onClick={() => onQuickAction(action.prompt)}
          >
            {action.label}
          </button>
        ))}
      </div>
      <div className="onboarding__hint">또는 자유롭게 입력하세요...</div>
    </div>
  );
}
