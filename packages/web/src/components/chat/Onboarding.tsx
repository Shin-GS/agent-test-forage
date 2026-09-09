// 첫 접속 온보딩 (디자인 명세 chat.html Case 1).
// - 🚀 빠른 시작 타이틀
// - quick-action: 선택된 서비스의 레시피(미선택이면 전체 레시피) 중 랜덤 4개.
//   버튼 라벨 = 전송 발화 = 레시피 이름(라벨과 실제 전송 문구를 일치시켜 혼란 방지).
//   클릭 시 레시피 이름을 그대로 발화로 전송(referenceId 없이) → AI 가 Tool Use 로 매칭.
// - 자유 입력 힌트

import { useMemo } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { recipesApi } from "../../api";
import type { RecipeSummary } from "../../api/types";
import { useChatStore } from "../../store/chatStore";

interface Props {
  onQuickAction: (content: string) => void;
}

/** 빠른 시작에 노출할 레시피 개수 */
const QUICK_ACTION_COUNT = 4;

/** 배열에서 무작위 n개를 뽑는다(원본 불변, Fisher–Yates 부분 셔플). */
function pickRandom<T>(items: readonly T[], n: number): T[] {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy.slice(0, n);
}

export function Onboarding({ onQuickAction }: Props) {
  // 온보딩은 항상 "새 대화" 상태이므로 선택된 서비스는 pendingApiSpecId 하나로 결정된다.
  // (null = 미선택 → 전체 레시피 대상)
  const pendingApiSpecId = useChatStore((state) => state.pendingApiSpecId);

  // 선택된 서비스(없으면 전체)의 레시피 목록. 온보딩 전용 queryKey(패널 목록과 별도 캐시).
  // 서비스 전환 시 새 키로 바뀌어도 이전 데이터를 잠깐 유지(keepPreviousData)해 로딩 깜빡임을 줄인다.
  const { data: recipes, isLoading } = useQuery<RecipeSummary[]>({
    queryKey: ["recipes", "onboarding", pendingApiSpecId ?? "all"],
    queryFn: () =>
      recipesApi.list({
        apiSpecId: pendingApiSpecId != null ? [pendingApiSpecId] : undefined,
      }),
    placeholderData: keepPreviousData,
  });

  // 랜덤 4개 고정: 레시피 구성(id 목록)이 바뀔 때만 다시 뽑는다.
  // → 단순 리렌더로는 버튼이 흔들리지 않고, 서비스 전환/목록 변경 시에만 갱신된다.
  const recipeIdsKey = (recipes ?? []).map((r) => r.id).join(",");
  const picks = useMemo(
    () => pickRandom(recipes ?? [], QUICK_ACTION_COUNT),
    // recipeIdsKey 로 레시피 집합의 변화를 감지(내용 동일하면 재추첨 안 함).
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [recipeIdsKey]
  );

  return (
    <div className="onboarding">
      <div className="onboarding__title">🚀 빠른 시작</div>

      {isLoading ? (
        <div className="onboarding__hint">레시피를 불러오는 중...</div>
      ) : picks.length > 0 ? (
        <div className="onboarding__actions">
          {picks.map((recipe) => (
            <button
              key={recipe.id}
              type="button"
              className="quick-action"
              title={recipe.name}
              onClick={() => onQuickAction(recipe.name)}
            >
              🧩 {recipe.name}
            </button>
          ))}
        </div>
      ) : (
        <div className="onboarding__hint">
          아직 등록된 레시피가 없어요. 아래에 자유롭게 입력해보세요.
        </div>
      )}

      <div className="onboarding__hint">또는 자유롭게 입력하세요...</div>
    </div>
  );
}
