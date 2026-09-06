// 대상 서비스 드롭다운 옵션 계산 훅 (비활성 스펙 참조 보존).
//
// recipe-editor.md "대상 서비스 선택 (비활성 스펙 참조 보존)" 정책 구현.
// - 기본은 ACTIVE 스펙 목록(GET /specs)만 옵션으로 노출한다.
// - 현재 참조 중인 apiSpecId 가 ACTIVE 목록에 없으면(관리자가 비활성화한 경우 등),
//   단건 조회(GET /specs/{id})로 그 스펙을 얻어 "{서비스명} (비활성)" 옵션으로 추가한다.
//   → 현재 값 보존. 단건 조회는 INACTIVE 도 반환하고, 삭제(soft delete)만 404.
// - 단건 조회가 404(삭제됨)면 옵션 복원 불가 → deletedReference=true 로 안내를 유도한다.
// - 새 선택은 ACTIVE 만(비활성 옵션은 "현재 참조된 그 스펙"에 한정).

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { specsApi } from "../../api";
import { ApiError } from "../../api/client";
import type { SpecListItem } from "../../api/types";

/** 드롭다운에 렌더할 서비스 옵션 한 줄 */
export interface ServiceOption {
  id: number;
  /** 표시 라벨. 비활성 보존 옵션은 "{name} (비활성)" */
  label: string;
  /** 현재 참조 보존용으로 추가된 비활성/목록밖 옵션 여부 */
  inactive: boolean;
}

export interface ServiceOptionsResult {
  /** 렌더할 옵션 목록 (ACTIVE + 필요 시 비활성 참조 1개) */
  options: ServiceOption[];
  /** 참조 스펙이 삭제되어(404) 옵션 복원 불가한 상태 */
  deletedReference: boolean;
}

/**
 * @param currentApiSpecId 현재 폼/스텝이 참조 중인 apiSpecId (없으면 null)
 */
export function useServiceOptions(currentApiSpecId: number | null): ServiceOptionsResult {
  const { data: specs } = useQuery<SpecListItem[]>({
    queryKey: ["specs"],
    queryFn: () => specsApi.list(),
  });

  const activeList = useMemo(() => specs ?? [], [specs]);
  // 현재 참조가 ACTIVE 목록에 존재하는지 (존재하면 단건 조회 불필요)
  const existsInActiveList =
    currentApiSpecId != null && activeList.some((s) => s.id === currentApiSpecId);

  // ACTIVE 목록에 현재 참조가 없을 때만 단건 조회한다.
  const needsLookup = currentApiSpecId != null && specs != null && !existsInActiveList;

  const specQuery = useQuery({
    queryKey: ["spec", currentApiSpecId],
    queryFn: () => specsApi.getSpec(currentApiSpecId as number),
    enabled: needsLookup,
    retry: false,
  });

  return useMemo<ServiceOptionsResult>(() => {
    const options: ServiceOption[] = activeList.map((s) => ({
      id: s.id,
      label: s.name,
      inactive: false,
    }));

    // 삭제(404)로 단건 조회 실패 → 옵션 복원 불가
    const deletedReference =
      needsLookup && specQuery.error instanceof ApiError && specQuery.error.status === 404;

    // 비활성/목록밖 참조 스펙을 보존 옵션으로 맨 앞에 추가
    if (needsLookup && specQuery.data) {
      const spec = specQuery.data;
      // status 가 있으면 그걸로, 없으면 "ACTIVE 목록에 없음 = 비활성/목록밖"으로 간주
      const isInactive = spec.status ? spec.status.code !== "ACTIVE" : true;
      options.unshift({
        id: spec.id,
        label: isInactive ? `${spec.name} (비활성)` : spec.name,
        inactive: isInactive,
      });
    }

    return { options, deletedReference };
  }, [activeList, needsLookup, specQuery.data, specQuery.error]);
}
