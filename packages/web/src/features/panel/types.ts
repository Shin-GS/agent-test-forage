// 사이드 패널 공용 타입.

import type { ConversationRuntimeStatus } from "../../store/types";

/** 레시피 [▶] 실행 콜백. App 의 handleSend(referenceId=recipeId) 로 연결된다. */
export type RunRecipeFn = (recipeId: number, recipeName: string) => void;

export interface PanelContext {
  userId: number;
  /** 대화방 런타임 상태. idle 이 아니면 [▶] 비활성 */
  conversationStatus: ConversationRuntimeStatus;
  onRunRecipe: RunRecipeFn;
}
