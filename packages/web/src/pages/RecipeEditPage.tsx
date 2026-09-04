// 레시피 생성/수정 페이지 (라우트 "/recipes/new", "/recipes/:id/edit").
// 디자인 명세: docs/design/web/recipe-editor.html Case2~6, authoring.md 정본.
// 섹션: ① 메타 ② 사용자 입력 변수 ③ 스텝(3종) ④ 결과 정의 ⑤ 결과 메시지 템플릿.
//
// 데이터: 편집 시 GET /recipes/{id} 로 로드(detailToForm). 저장은 create/update mutation →
//         성공 시 목록 invalidate + /recipes 로 이동. 400(검증/순환참조)은 상단 alert.
// 저장 전 클라이언트 유효성 검증(validateRecipe)으로 필드 하이라이트.

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, recipesApi } from "../api";
import type { RecipeCreateRequest, RecipeUpdateRequest } from "../api/types";
import { MetaSection } from "../components/recipe/MetaSection";
import { VariablesSection } from "../components/recipe/VariablesSection";
import { StepsSection } from "../components/recipe/StepsSection";
import { ResultDefinitionSection } from "../components/recipe/ResultDefinitionSection";
import {
  detailToForm,
  emptyForm,
  formToCreateRequest,
  formToUpdateRequest,
  type RecipeFormState,
} from "../components/recipe/recipeForm";

/** dirty 비교용 스냅샷: 직렬화 가능한 폼 내용(_uid 제외) + apiSpecId */
function formSnapshot(form: RecipeFormState): string {
  return JSON.stringify({ ...formToUpdateRequest(form), apiSpecId: form.apiSpecId });
}
import { validateRecipe, type RecipeValidationResult } from "../components/recipe/recipeValidation";

const EMPTY_VALIDATION: RecipeValidationResult = {
  valid: true,
  messages: [],
  meta: {},
  errorStepIndexes: [],
  stepMappingErrors: {},
};

export function RecipeEditPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const params = useParams<{ id?: string }>();

  const recipeId = params.id ? Number(params.id) : null;
  const isEdit = recipeId != null;

  const [form, setForm] = useState<RecipeFormState>(emptyForm());
  const [validation, setValidation] = useState<RecipeValidationResult>(EMPTY_VALIDATION);
  const [serverError, setServerError] = useState<string | null>(null);

  // dirty 추적: 초기 스냅샷과 현재 폼 비교 (저장 성공 시 이탈 경고 해제)
  const initialSnapshotRef = useRef<string>(formSnapshot(emptyForm()));
  const [saved, setSaved] = useState(false);
  const isDirty = !saved && formSnapshot(form) !== initialSnapshotRef.current;

  // 편집 모드: 상세 로드
  const {
    data: detail,
    isLoading: isDetailLoading,
    isError: isDetailError,
    error: detailError,
  } = useQuery({
    queryKey: ["recipe", recipeId],
    queryFn: () => recipesApi.detail(recipeId as number),
    enabled: isEdit,
  });

  useEffect(() => {
    if (detail) {
      const loaded = detailToForm(detail);
      setForm(loaded);
      initialSnapshotRef.current = formSnapshot(loaded);
    }
  }, [detail]);

  // 브라우저 새로고침/닫기/뒤로가기 시 dirty 경고
  useEffect(() => {
    if (!isDirty) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [isDirty]);

  // 목록 이동(뒤로가기 버튼) 시 dirty 확인
  const navigateAway = useCallback(
    (to: string) => {
      if (isDirty && !window.confirm("저장하지 않은 변경사항이 있습니다. 목록으로 이동할까요?")) {
        return;
      }
      navigate(to);
    },
    [isDirty, navigate],
  );

  function patchForm(patch: Partial<RecipeFormState>) {
    setForm((prev) => ({ ...prev, ...patch }));
  }

  const saveMutation = useMutation({
    mutationFn: (body: RecipeCreateRequest | RecipeUpdateRequest) =>
      isEdit
        ? recipesApi.update(recipeId as number, body as RecipeUpdateRequest)
        : recipesApi.create(body as RecipeCreateRequest),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["recipes"] });
      if (isEdit) void queryClient.invalidateQueries({ queryKey: ["recipe", recipeId] });
      setSaved(true); // 이탈 경고 해제 후 이동
      navigate("/recipes");
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        setServerError(err.message);
      } else {
        setServerError(err instanceof Error ? err.message : "저장에 실패했습니다");
      }
    },
  });

  function handleSave() {
    setServerError(null);
    const result = validateRecipe(form);
    setValidation(result);
    if (!result.valid) return;
    const body = isEdit ? formToUpdateRequest(form) : formToCreateRequest(form);
    saveMutation.mutate(body);
  }

  const title = useMemo(() => {
    if (!isEdit) return "새 레시피";
    return form.name ? `${form.name} 편집` : "레시피 편집";
  }, [isEdit, form.name]);

  // 편집 로드 상태
  if (isEdit && isDetailLoading) {
    return (
      <div className="recipe-page">
        <div className="page-body">
          <div className="recipe-state" role="status" aria-live="polite">
            레시피를 불러오는 중입니다…
          </div>
        </div>
      </div>
    );
  }

  if (isEdit && isDetailError) {
    return (
      <div className="recipe-page">
        <div className="page-body">
          <div className="recipe-state recipe-state--error" role="alert">
            레시피를 불러오지 못했습니다
            {detailError instanceof Error ? `: ${detailError.message}` : ""}
          </div>
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => navigate("/recipes")}>
            목록으로
          </button>
          {/* 위 에러 상태는 로드 실패 화면(폼 미표시)이므로 dirty 경고 불필요 */}
        </div>
      </div>
    );
  }

  return (
    <div className="recipe-page">
      <div className="page-header">
        <button
          type="button"
          className="page-header__back"
          onClick={() => navigateAway("/recipes")}
        >
          ← 목록으로
        </button>
        <span className="page-header__title">{title}</span>
        <div className="page-header__actions">
          <button
            type="button"
            className="btn btn--primary"
            disabled={saveMutation.isPending}
            onClick={handleSave}
          >
            {saveMutation.isPending ? "저장 중…" : "저장"}
          </button>
        </div>
      </div>

      <div className="page-body">
        {/* 에러 요약 */}
        {(serverError || (!validation.valid && validation.messages.length > 0)) && (
          <div className="alert alert--error" role="alert">
            {serverError ? (
              <div>✕ 저장할 수 없습니다 — {serverError}</div>
            ) : (
              <>
                <div>✕ 저장할 수 없습니다 — {validation.messages.length}개의 문제를 해결해주세요</div>
                <ul className="alert__list">
                  {validation.messages.map((msg, i) => (
                    <li key={i}>{msg}</li>
                  ))}
                </ul>
              </>
            )}
          </div>
        )}

        <MetaSection form={form} onChange={patchForm} errors={validation.meta} />

        <VariablesSection
          variables={form.variables}
          onChange={(next) => patchForm({ variables: next })}
        />

        <StepsSection
          steps={form.steps}
          onChange={(next) => patchForm({ steps: next })}
          userVariables={form.variables}
          currentRecipeId={recipeId}
          stepMappingErrors={validation.stepMappingErrors}
          errorStepIndexes={validation.errorStepIndexes}
        />

        <ResultDefinitionSection
          items={form.resultDefinition}
          onChange={(next) => patchForm({ resultDefinition: next })}
        />

        {/* ⑤ 결과 메시지 템플릿 */}
        <div className="section">
          <div className="section__title">
            <span className="section__number">5</span> 결과 메시지 템플릿
          </div>
          <textarea
            className="textarea"
            style={{ minHeight: "80px" }}
            placeholder="레시피 성공 시 표시할 메시지. 변수는 [변수명]으로 삽입."
            aria-label="결과 메시지 템플릿"
            value={form.resultTemplate}
            onChange={(e) => patchForm({ resultTemplate: e.target.value })}
          />
          <p className="recipe-hint" style={{ marginTop: "var(--space-2)" }}>
            미입력 시 AI가 자동 요약합니다. 사용 가능한 변수: 결과 정의(④) + 사용자 입력 변수(②).
          </p>
        </div>
      </div>
    </div>
  );
}
