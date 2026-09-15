// 공통 복사 버튼.
// - 채팅 답변 복사(MessageItem.CopyButton)와 동일한 상호작용을 공통화한 컴포넌트다:
//   navigator.clipboard.writeText → "복사되었습니다" 토스트 → 잠깐 ✓ 피드백(1.5s) → 원복.
// - 접근성: aria-label 명시 + 복사 완료 시 sr-only aria-live 안내.
// - 히스토리 상세(결과값/스텝 JSON) 및 향후 다른 복사 지점에서 재사용한다.

import { useState, type CSSProperties } from "react";
import { useToastStore } from "../../store/toastStore";

interface CopyButtonProps {
  /** 복사할 텍스트. 비어 있으면 no-op. */
  text: string;
  /** 접근성 라벨. 예: "created 값 복사" */
  label: string;
  /** 버튼 클래스. 기본 "copy-btn"(history.html 복사 버튼 스타일) */
  className?: string;
  style?: CSSProperties;
}

export function CopyButton({ text, label, className = "copy-btn", style }: CopyButtonProps) {
  const showToast = useToastStore((s) => s.show);
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      showToast("복사되었습니다", "success");
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      showToast("복사에 실패했어요", "error");
    }
  }

  return (
    <button type="button" className={className} aria-label={label} style={style} onClick={handleCopy}>
      <span aria-hidden>{copied ? "✓ 복사됨" : "복사"}</span>
      <span className="sr-only" aria-live="polite">
        {copied ? "복사되었습니다" : ""}
      </span>
    </button>
  );
}
