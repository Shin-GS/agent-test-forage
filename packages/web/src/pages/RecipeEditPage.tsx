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
import type { RecipeCreateRequest, RecipeDetail, RecipeUpdateRequest } from "../api/types";
import { MetaSection } from "../components/recipe/MetaSection";
import { VariablesSection } from "../components/recipe/VariablesSection";
import { StepsSection } from "../components/recipe/StepsSection";
import { ResultDefinitionSection } from "../components/recipe/ResultDefinitionSection";
import { VersionDrawer } from "../components/recipe/VersionDrawer";
import { VersionPreviewModal } from "../components/recipe/VersionPreviewModal";
import { ConfirmModal } from "../components/common/ConfirmModal";
import { useToastStore } from "../store/toastStore";
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
  const showToast = useToastStore((s) => s.show);

  const recipeId = params.id ? Number(params.id) : null;
  const isEdit = recipeId != null;

  const [form, setForm] = useState<RecipeFormState>(emptyForm());
  const [validation, setValidation] = useState<RecipeValidationResult>(EMPTY_VALIDATION);
  const [serverError, setServerError] = useState<string | null>(null);

  // 현재 레시피 버전/권한 (상세 로드 후 채워짐)
  const [currentVersion, setCurrentVersion] = useState(0);
  const [canEdit, setCanEdit] = useState(true);

  // 버전 UI 상태
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [previewVersionNo, setPreviewVersionNo] = useState<number | null>(null);
  const [restoreVersionNo, setRestoreVersionNo] = useState<number | null>(null);
  const versionTriggerRef = useRef<HTMLButtonElement>(null);

  // 읽기 전용: 편집 모드 + canEdit=false (공통 레시피를 non-admin 이 연 경우)
  const readOnly = isEdit && !canEdit;

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

  const applyDetail = useCallback((loadedDetail: RecipeDetail) => {
    const loaded = detailToForm(loadedDetail);
    setForm(loaded);
    initialSnapshotRef.current = formSnapshot(loaded);
    setCurrentVersion(loadedDetail.currentVersion ?? 0);
    setCanEdit(loadedDetail.canEdit ?? true);
  }, []);

  useEffect(() => {
    if (detail) applyDetail(detail);
  }, [detail, applyDetail]);

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

  // --- 버전 복원 ---
  const restoreMutation = useMutation({
    mutationFn: (versionNo: number) => recipesApi.restoreVersion(recipeId as number, versionNo),
    onSuccess: (restored, versionNo) => {
      applyDetail(restored);
      void queryClient.invalidateQueries({ queryKey: ["recipe", recipeId] });
      void queryClient.invalidateQueries({ queryKey: ["recipe", recipeId, "versions"] });
      void queryClient.invalidateQueries({ queryKey: ["recipes"] });
      setRestoreVersionNo(null);
      setPreviewVersionNo(null);
      setDrawerOpen(false);
      const invalid = (restored.validationStatus?.code ?? "").toUpperCase() === "INVALID";
      showToast(
        `v${versionNo} 내용으로 복원했습니다 (현재 v${restored.currentVersion})${invalid ? " · 유효성 경고 있음" : ""}`,
        invalid ? "warning" : "success",
      );
    },
    onError: (err) => {
      setRestoreVersionNo(null);
      showToast(
        err instanceof ApiError ? err.message : err instanceof Error ? err.message : "복원에 실패했습니다",
        "error",
      );
    },
  });

  // --- 개인 사본으로 복제 (읽기 전용 배너) ---
  const duplicateMutation = useMutation({
    mutationFn: () => recipesApi.duplicate(recipeId as number),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ["recipes"] });
      showToast(`'${created.name}' 개인 사본을 만들었습니다`, "success");
      navigate(`/recipes/${created.id}/edit`);
    },
    onError: (err) =>
      showToast(
        err instanceof ApiError ? err.message : err instanceof Error ? err.message : "복제에 실패했습니다",
        "error",
      ),
  });

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
        <span className="page-header__title">
          {readOnly ? `${form.name || "레시피"} (읽기 전용)` : title}
        </span>
        <div className="page-header__actions">
          {isEdit && (
            <button
              ref={versionTriggerRef}
              type="button"
              className="btn btn--secondary version-badge-btn"
              aria-haspopup="true"
              aria-expanded={drawerOpen}
              onClick={() => setDrawerOpen((prev) => !prev)}
            >
              🕘 버전 기록 <span className="badge badge--neutral">v{currentVersion}</span>
            </button>
          )}
          {!readOnly && (
            <button
              type="button"
              className="btn btn--primary"
              disabled={saveMutation.isPending}
              onClick={handleSave}
            >
              {saveMutation.isPending ? "저장 중…" : "저장"}
            </button>
          )}
        </div>
      </div>

      <div className="page-body">
        {/* 읽기 전용 배너 (공통 레시피 × non-admin) */}
        {readOnly && (
          <div className="alert alert--info readonly-banner">
            <span>
              🔒 <strong>공통 레시피 · 읽기 전용</strong> — 수정하려면 개인 사본으로 복제하세요.
            </span>
            <button
              type="button"
              className="btn btn--primary btn--sm"
              disabled={duplicateMutation.isPending}
              onClick={() => duplicateMutation.mutate()}
            >
              개인 사본으로 복제
            </button>
          </div>
        )}

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

        {/* 읽기 전용이면 fieldset[disabled] 로 모든 입력을 네이티브 비활성화 */}
        <fieldset className="recipe-form-fieldset" disabled={readOnly}>
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
              {/* 작성 도움말 툴팁: 라벨 옆 ⓘ, 기존 .tooltip 컴포넌트 재사용(작성 위치 근처). */}
              <span
                className="tooltip"
                tabIndex={0}
                aria-label="결과 메시지 템플릿 작성 도움말"
                style={{ marginLeft: "var(--space-1)", cursor: "help", color: "var(--color-text-tertiary)" }}
              >
                ⓘ
                <span className="tooltip__content" style={{ textAlign: "left", whiteSpace: "normal", width: 280 }}>
                  마크다운 + Handlebars 문법으로 작성해요.
                  <br />• 값: <code>{"{{orderId}}"}</code>, <code>{"{{userInput.수량}}"}</code>
                  <br />• 반복: <code>{"{{#each items}}- {{this.name}}{{/each}}"}</code>
                  <br />• 조건: <code>{"{{#if balance}}...{{/if}}"}</code>
                  <br />• 헬퍼: <code>{"{{formatNumber amount}}"}</code>(콤마), <code>{'{{default v "-"}}'}</code>
                  <br />• 표: 마크다운 표 <code>| 열 |</code> 사용 가능
                  <br />쓸 수 있는 값은 ④ 결과 정의 + ② 사용자 입력 변수예요.
                </span>
              </span>
            </div>
            <textarea
              className="textarea"
              style={{ minHeight: "80px" }}
              placeholder="예: **{{ownerName}}**님의 잔액은 {{formatNumber balance}}원입니다. (마크다운 + {{변수}})"
              aria-label="결과 메시지 템플릿"
              value={form.resultTemplate}
              onChange={(e) => patchForm({ resultTemplate: e.target.value })}
            />
            <p className="recipe-hint" style={{ marginTop: "var(--space-2)" }}>
              마크다운 + Handlebars 문법 지원(반복·조건·표). 사용 가능한 변수: 결과 정의(④) + 사용자 입력 변수(②). 미입력 시 결과값을 자동 요약합니다.
            </p>
          </div>
        </fieldset>
      </div>

      {/* 버전 기록 drawer (편집 모드) */}
      {isEdit && drawerOpen && recipeId != null && (
        <VersionDrawer
          recipeId={recipeId}
          currentVersion={currentVersion}
          canEdit={canEdit}
          triggerRef={versionTriggerRef}
          onClose={() => setDrawerOpen(false)}
          onPreview={(versionNo) => setPreviewVersionNo(versionNo)}
          onRestore={(versionNo) => setRestoreVersionNo(versionNo)}
        />
      )}

      {/* 버전 미리보기 모달 */}
      {isEdit && previewVersionNo != null && recipeId != null && (
        <VersionPreviewModal
          recipeId={recipeId}
          versionNo={previewVersionNo}
          canRestore={canEdit}
          onClose={() => setPreviewVersionNo(null)}
          onRestore={(versionNo) => setRestoreVersionNo(versionNo)}
        />
      )}

      {/* 복원 확인 모달 */}
      <ConfirmModal
        open={restoreVersionNo != null}
        title={restoreVersionNo != null ? `v${restoreVersionNo}으로 복원` : "복원"}
        description={
          restoreVersionNo != null
            ? `v${restoreVersionNo} 내용으로 새 버전(v${currentVersion + 1})을 만듭니다. 현재 내용(v${currentVersion})은 버전으로 보관되어 안전하며, 되돌리기도 이력에 남습니다. 선택한 버전 이후 스펙이 변경돼 복원 결과가 유효하지 않을(INVALID) 수 있으며, 이 경우에도 복원은 되고 실행 전 유효성 경고로 안내됩니다.`
            : undefined
        }
        confirmLabel="이 버전으로 복원"
        onConfirm={() => restoreVersionNo != null && restoreMutation.mutate(restoreVersionNo)}
        onCancel={() => setRestoreVersionNo(null)}
      />
    </div>
  );
}
