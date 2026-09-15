// 버전 미리보기 모달 (레시피 편집 Case 12). 읽기 전용.
// 특정 버전 상세를 조회해 메타/스텝/결과정의/결과템플릿을 요약 렌더한다.
// 푸터 [이 버전으로 복원] 은 canRestore(=편집 가능)일 때만 노출.
// 오버레이 동작(ESC/배경클릭/포커스 트랩·복귀/중첩 top-most)은 AppModal(Base UI Dialog)에 위임.

import { useQuery } from "@tanstack/react-query";
import { recipesApi } from "../../api";
import { stepTypeLabel } from "./recipeForm";
import type { RecipeStep } from "../../api/types";
import { AppModal } from "../common/AppModal";

interface VersionPreviewModalProps {
  recipeId: number;
  versionNo: number;
  /** 복원 버튼 노출 여부 (읽기 전용 모드면 false) */
  canRestore: boolean;
  onClose: () => void;
  /** [이 버전으로 복원] 클릭 → 상위에서 복원 확인 모달을 연다 */
  onRestore: (versionNo: number) => void;
}

/** ISO → "YYYY-MM-DD HH:mm" (실패 시 원본 앞 16자) */
function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const parsed = Date.parse(value);
  if (Number.isNaN(parsed)) return value.slice(0, 16);
  const d = new Date(parsed);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function stepName(step: RecipeStep, index: number): string {
  if (step.type === "api" && step.label) return step.label;
  return step.name || `스텝 ${index + 1}`;
}

export function VersionPreviewModal({
  recipeId,
  versionNo,
  canRestore,
  onClose,
  onRestore,
}: VersionPreviewModalProps) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["recipe", recipeId, "version", versionNo],
    queryFn: () => recipesApi.getVersion(recipeId, versionNo),
  });

  const steps = data?.steps ?? [];
  const resultDefs = data?.resultDefinition ?? [];

  return (
    // 이 컴포넌트는 previewVersionNo != null 일 때만 마운트되므로 open 은 항상 true(마운트=열림).
    // 푸터에 [닫기]가 있으므로 헤더 ✕(showCloseButton)는 끈다(닫기 버튼 중복 방지).
    <AppModal open onClose={onClose} title="버전 미리보기 (읽기 전용)" showCloseButton={false} className="modal--wide">
      <div className="modal__body">
        {isLoading && (
          <div className="recipe-state" role="status" aria-live="polite">
            버전을 불러오는 중입니다…
          </div>
        )}
        {isError && (
          <div className="recipe-state recipe-state--error" role="alert">
            버전을 불러오지 못했습니다{error instanceof Error ? `: ${error.message}` : ""}
          </div>
        )}
        {data && (
          <>
            <div className="preview-meta">
              <span className="badge badge--neutral">v{data.versionNo}</span>
              <span>· {formatDateTime(data.createdAt)} 생성</span>
            </div>

            <div className="preview-block">
              <div className="preview-block__title">① 메타 정보</div>
              <div className="preview-field">
                <span className="preview-field__key">레시피명</span>
                <span className="preview-field__val">{data.name || "-"}</span>
              </div>
              <div className="preview-field">
                <span className="preview-field__key">설명</span>
                <span className="preview-field__val">{data.description || "-"}</span>
              </div>
              <div className="preview-field">
                <span className="preview-field__key">공개 범위</span>
                <span className="preview-field__val">
                  {data.visibility?.description ?? data.visibility?.code ?? "-"}
                </span>
              </div>
            </div>

            <div className="preview-block">
              <div className="preview-block__title">③ 스텝 ({steps.length}개)</div>
              {steps.length === 0 ? (
                <div className="preview-field">
                  <span className="preview-field__val">스텝 없음</span>
                </div>
              ) : (
                steps.map((step, i) => (
                  <div key={i} className="preview-field">
                    <span className="preview-field__key">
                      {i + 1}. {stepName(step, i)}
                    </span>
                    <span className="preview-field__val badge badge--neutral">
                      {stepTypeLabel(step.type)}
                    </span>
                  </div>
                ))
              )}
            </div>

            {resultDefs.length > 0 && (
              <div className="preview-block">
                <div className="preview-block__title">④ 결과 정의</div>
                {resultDefs.map((item, i) => (
                  <div key={i} className="preview-field">
                    <span className="preview-field__key">{item.key}</span>
                    <span className="preview-field__val">{item.label || item.key}</span>
                  </div>
                ))}
              </div>
            )}

            {data.resultTemplate && (
              <div className="preview-block" style={{ marginBottom: 0 }}>
                <div className="preview-block__title">⑤ 결과 메시지 템플릿</div>
                <div className="preview-field">
                  <span className="preview-field__val">{data.resultTemplate}</span>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      <div className="modal__footer">
        <button type="button" className="btn btn--secondary" onClick={onClose}>
          닫기
        </button>
        {canRestore && data && (
          <button type="button" className="btn btn--primary" onClick={() => onRestore(data.versionNo)}>
            이 버전으로 복원
          </button>
        )}
      </div>
    </AppModal>
  );
}
