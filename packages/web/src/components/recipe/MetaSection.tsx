// ① 메타 정보 섹션.
// 레시피명 / 대상 서비스(스펙 드롭다운) / 설명 / 태그(칩 입력) / 공개범위(라디오).

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { specsApi } from "../../api";
import type { RecipeFormState } from "./recipeForm";

interface MetaSectionProps {
  form: RecipeFormState;
  onChange: (patch: Partial<RecipeFormState>) => void;
  /** 유효성 에러 필드 집합 */
  errors?: { name?: boolean; description?: boolean; apiSpecId?: boolean };
}

export function MetaSection({ form, onChange, errors = {} }: MetaSectionProps) {
  const [tagInput, setTagInput] = useState("");
  const { data: specs } = useQuery({ queryKey: ["specs"], queryFn: () => specsApi.list() });

  function addTag() {
    const value = tagInput.trim();
    if (value && !form.tags.includes(value)) {
      onChange({ tags: [...form.tags, value] });
    }
    setTagInput("");
  }

  function removeTag(tag: string) {
    onChange({ tags: form.tags.filter((t) => t !== tag) });
  }

  return (
    <div className="section">
      <div className="section__title">
        <span className="section__number">1</span> 메타 정보
      </div>
      <div className="field-grid">
        <div className="form-group">
          <label className="form-label" htmlFor="recipe-name">
            레시피명 *
          </label>
          <input
            id="recipe-name"
            className={`input${errors.name ? " input--error" : ""}`}
            type="text"
            placeholder="레시피 이름"
            aria-invalid={errors.name || undefined}
            value={form.name}
            onChange={(e) => onChange({ name: e.target.value })}
          />
          {errors.name && <span className="form-error">레시피명은 필수입니다</span>}
        </div>

        <div className="form-group">
          <label className="form-label" htmlFor="recipe-spec">
            대상 서비스 *
          </label>
          <select
            id="recipe-spec"
            className={`input${errors.apiSpecId ? " input--error" : ""}`}
            aria-invalid={errors.apiSpecId || undefined}
            value={form.apiSpecId ?? ""}
            onChange={(e) => onChange({ apiSpecId: e.target.value === "" ? null : Number(e.target.value) })}
          >
            <option value="">서비스 선택...</option>
            {(specs ?? []).map((spec) => (
              <option key={spec.id} value={spec.id}>
                {spec.name}
              </option>
            ))}
          </select>
          {errors.apiSpecId && <span className="form-error">서비스를 선택해주세요</span>}
        </div>

        <div className="form-group field-full">
          <label className="form-label" htmlFor="recipe-desc">
            설명 *
          </label>
          <textarea
            id="recipe-desc"
            className={`textarea${errors.description ? " input--error" : ""}`}
            placeholder="이 레시피가 하는 일을 설명해주세요 (AI 매칭에 활용됩니다)"
            style={{ minHeight: "60px" }}
            aria-invalid={errors.description || undefined}
            value={form.description}
            onChange={(e) => onChange({ description: e.target.value })}
          />
          {errors.description && <span className="form-error">설명은 필수입니다</span>}
        </div>

        <div className="form-group">
          <label className="form-label" htmlFor="recipe-tag-input">
            태그
          </label>
          <div className="tag-input">
            {form.tags.map((tag) => (
              <span key={tag} className="tag-chip">
                {tag}
                <span
                  className="tag-chip__remove"
                  role="button"
                  tabIndex={0}
                  aria-label={`태그 ${tag} 제거`}
                  onClick={() => removeTag(tag)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") removeTag(tag);
                  }}
                >
                  ✕
                </span>
              </span>
            ))}
            <input
              id="recipe-tag-input"
              className="tag-input__field"
              type="text"
              placeholder="태그 입력 후 Enter"
              value={tagInput}
              onChange={(e) => setTagInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  addTag();
                }
              }}
            />
          </div>
        </div>

        <div className="form-group">
          <span className="form-label">공개 범위 *</span>
          <div className="radio-row">
            <label className="radio-row__item">
              <input
                type="radio"
                name="visibility"
                checked={form.visibility === "COMMON"}
                onChange={() => onChange({ visibility: "COMMON" })}
              />
              공통
            </label>
            <label className="radio-row__item">
              <input
                type="radio"
                name="visibility"
                checked={form.visibility === "PRIVATE"}
                onChange={() => onChange({ visibility: "PRIVATE" })}
              />
              개인
            </label>
          </div>
        </div>
      </div>
    </div>
  );
}
