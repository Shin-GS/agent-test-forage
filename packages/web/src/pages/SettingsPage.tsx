// 설정 페이지 (라우트 "/settings").
// 디자인 명세: docs/design/web/settings.html Case1(기본, 읽기 전용) 기준.
// 대부분 읽기 전용 — AI 설정/레시피 실행 설정은 서버 설정 파일로만 변경한다.
// 편집 가능한 항목은 계정(비밀번호 변경)뿐이나, 인증/계정 API 미구현이므로 이번엔 버튼 disabled.
//
// 데이터: GET /api/v1/settings (settingsApi.get) 을 React Query 로 조회. 로딩/에러 처리.

import { useQuery } from "@tanstack/react-query";
import { settingsApi } from "../api";
import type { SettingsResponse } from "../api/types";

/** 읽기 전용 설정 한 줄 (라벨/설명 + 값) */
function SettingRow({ label, desc, value }: { label: string; desc?: string; value: string }) {
  return (
    <div className="settings-row">
      <div>
        <div className="settings-row__label">{label}</div>
        {desc && <div className="settings-row__desc">{desc}</div>}
      </div>
      <div className="settings-row__readonly">{value}</div>
    </div>
  );
}

/** 파일로만 변경 안내 문구 */
function FileOnlyNote() {
  return (
    <div className="settings-note">ⓘ 이 값들은 서버 설정 파일로만 변경됩니다 (화면에서 편집 불가)</div>
  );
}

function SettingsContent({ data }: { data: SettingsResponse }) {
  return (
    <>
      {/* AI 설정 (읽기 전용) */}
      <section className="settings-section" aria-labelledby="settings-ai-title">
        <h2 id="settings-ai-title" className="settings-section__title">
          🤖 AI 설정 <span className="badge badge--neutral">읽기 전용</span>
        </h2>
        <SettingRow label="AI Provider" desc="OpenAI 호환 (OpenRouter 고정)" value={data.provider} />
        <SettingRow label="Reasoning 모델" desc="의도 분석/플랜/조회 판단" value={data.reasoningModel} />
        <SettingRow label="Fast 모델" desc="필드 생성/요약" value={data.fastModel} />
        <SettingRow label="대화 이력 전달 수" desc="AI에게 전달하는 최근 대화 건수" value={`${data.historyLimit} 건`} />
        <SettingRow label="호출 타임아웃" desc="AI 호출 최대 대기 시간" value={`${data.aiTimeoutSeconds} 초`} />
        <FileOnlyNote />
      </section>

      {/* 레시피 실행 설정 (읽기 전용) */}
      <section className="settings-section" aria-labelledby="settings-exec-title">
        <h2 id="settings-exec-title" className="settings-section__title">
          ⚡ 레시피 실행 설정 <span className="badge badge--neutral">읽기 전용</span>
        </h2>
        <SettingRow label="스텝 타임아웃" desc="개별 스텝 최대 대기 시간" value={`${data.stepTimeoutSeconds} 초`} />
        <SettingRow label="전체 타임아웃" desc="레시피 전체 최대 실행 시간" value={`${data.recipeTimeoutSeconds} 초`} />
        <FileOnlyNote />
      </section>

      {/* 계정 */}
      <section className="settings-section" aria-labelledby="settings-account-title">
        <h2 id="settings-account-title" className="settings-section__title">👤 계정</h2>
        <div className="settings-row">
          <div>
            <div className="settings-row__label">비밀번호 변경</div>
            <div className="settings-row__desc">유일하게 편집 가능한 항목</div>
          </div>
          <button
            type="button"
            className="btn btn--secondary btn--sm"
            disabled
            title="인증 구현 후 제공"
          >
            변경하기
          </button>
        </div>
      </section>
    </>
  );
}

export function SettingsPage() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["settings"],
    queryFn: () => settingsApi.get(),
  });

  return (
    <div className="settings-page">
      <div className="settings-page__header">
        <span className="settings-page__title">설정</span>
      </div>
      <div className="settings-page__body">
        {isLoading && (
          <div className="settings-state" role="status" aria-live="polite">
            설정을 불러오는 중입니다…
          </div>
        )}

        {isError && (
          <div className="settings-state settings-state--error" role="alert">
            <div>설정을 불러오지 못했습니다{error instanceof Error ? `: ${error.message}` : ""}</div>
            <button type="button" className="btn btn--secondary btn--sm" onClick={() => void refetch()}>
              다시 시도
            </button>
          </div>
        )}

        {data && <SettingsContent data={data} />}
      </div>
    </div>
  );
}
