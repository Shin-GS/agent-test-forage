// 토스트 표시 컨테이너. App 최상단에 한 번 렌더한다.
// store.toasts 를 우하단에 쌓아 보여주고, 클릭 시 즉시 닫는다.

import { useToastStore } from "../../store/toastStore";

export function ToastContainer() {
  const toasts = useToastStore((state) => state.toasts);
  const dismiss = useToastStore((state) => state.dismiss);

  if (toasts.length === 0) return null;

  return (
    <div className="toast-container" role="status" aria-live="polite">
      {toasts.map((t) => (
        <div
          key={t.id}
          className={`toast toast--${t.level}`}
          onClick={() => dismiss(t.id)}
          role="button"
          tabIndex={0}
        >
          {t.message}
        </div>
      ))}
    </div>
  );
}
