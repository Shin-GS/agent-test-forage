package com.testforge.service.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testforge.common.error.ApiException;
import com.testforge.dto.common.CursorPage;
import com.testforge.dto.common.StatusView;
import com.testforge.dto.recipe.RecipeCreateRequest;
import com.testforge.dto.recipe.RecipeDetailResponse;
import com.testforge.dto.recipe.RecipeSummaryResponse;
import com.testforge.dto.recipe.RecipeUpdateRequest;
import com.testforge.dto.recipe.RecipeVersionDetailResponse;
import com.testforge.dto.recipe.RecipeVersionSummary;
import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.recipe.RecipeVersion;
import com.testforge.entity.recipe.enums.Visibility;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.repository.recipe.RecipeVersionRepository;
import com.testforge.security.CurrentUser;
import com.testforge.utils.RecipeJsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 레시피 CRUD + 검증 + 버전 스냅샷/복원 + 복제 로직. (실행 엔진은 이번 스코프 아님 — 정의/관리만)
 * 스텝/변수/결과 JSON은 문자열로 저장하고, 상세 응답에서 다시 노드로 파싱해 내린다.
 *
 * <p><b>권한</b>: 조회/변경 규칙은 {@link RecipeAccessPolicy}에 집중한다. 404(존재 은폐) vs 403(권한 부족)
 * 원칙(auth.md)을 서비스가 정책 결과에 근거해 적용한다 — 남의 PRIVATE/소프트삭제/없음 = 404,
 * 공통을 non-admin이 변경 시도 = 403. userId/role은 항상 {@link CurrentUser}에서 도출한다(요청 바디 신뢰 금지).
 */
@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);

    /** 버전 목록 페이지 기본/최대 크기 (versioning.md) */
    private static final int DEFAULT_VERSION_PAGE_SIZE = 20;
    private static final int MAX_VERSION_PAGE_SIZE = 50;

    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final RecipeValidator validator;
    private final RecipeAccessPolicy accessPolicy;

    // 버전 스냅샷 직렬화/역직렬화용 로컬 매퍼 (기존 패턴과 동일하게 공용 빈에 의존하지 않음)
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecipeService(RecipeRepository recipeRepository,
                         RecipeVersionRepository versionRepository,
                         RecipeValidator validator,
                         RecipeAccessPolicy accessPolicy) {
        this.recipeRepository = recipeRepository;
        this.versionRepository = versionRepository;
        this.validator = validator;
        this.accessPolicy = accessPolicy;
    }

    /**
     * 레시피 생성 (v1). 생성 시 검증을 수행하여 VALIDATION_STATUS를 반영한다.
     * 순환 참조가 있으면 저장 전에 400으로 거부된다.
     * 공개범위=COMMON 생성은 ADMIN만 허용(canSetCommon), 위반 시 403.
     */
    @Transactional
    public RecipeDetailResponse create(RecipeCreateRequest request) {
        validateRequiredMeta(request.ownerUserId(), request.apiSpecId(), request.name());

        Visibility visibility = request.visibility() == null ? Visibility.PRIVATE : request.visibility();
        if (visibility == Visibility.COMMON && !accessPolicy.canSetCommon(CurrentUser.role())) {
            throw ApiException.forbidden("Only ADMIN can create a COMMON recipe");
        }

        Recipe recipe = new Recipe(request.ownerUserId(), request.apiSpecId(), request.name());
        recipe.setDescription(request.description());
        recipe.setVisibility(visibility);
        recipe.setTags(RecipeJsonUtil.toJsonString(request.tags()));
        recipe.setVariablesJson(RecipeJsonUtil.toJsonString(request.variables()));
        recipe.setStepsJson(RecipeJsonUtil.toJsonString(request.steps()));
        recipe.setResultDefinitionJson(RecipeJsonUtil.toJsonString(request.resultDefinition()));
        recipe.setResultTemplate(request.resultTemplate());
        recipe.setCurrentVersion(1);

        // 검증: 순환이면 여기서 400 → 저장되지 않음
        RecipeValidationResult result = validator.validate(null, recipe.getApiSpecId(), recipe.getStepsJson());
        recipe.setValidationStatus(result.status());
        recipe.setValidationMessage(result.message());

        Recipe saved = recipeRepository.save(recipe);
        log.info("Recipe created: recipeId={}, validation={}", saved.getId(), result.status());
        return toDetail(saved);
    }

    /** 미삭제 레시피 상세. 없거나 삭제/남의 PRIVATE면 404(존재 은폐). */
    @Transactional(readOnly = true)
    public RecipeDetailResponse detail(Long id) {
        Recipe recipe = requireViewable(id);
        return toDetail(recipe);
    }

    /**
     * 목록 조회 (미삭제만) — 소유 격리 + 다중 필터(서비스/공개범위/태그) + keyword + 정렬.
     * 반환 범위는 "COMMON 전체 + 요청자 본인 PRIVATE"로 강제된다(리포지토리 WHERE).
     * 태그 다중 필터는 TAGS가 JSON 문자열이라 여기서 LIKE(OR) 후처리한다.
     *
     * @param apiSpecIds   대상 서비스 ID 목록 (빈/ null이면 무시)
     * @param visibilities 공개범위 코드 목록 (COMMON/PRIVATE, 빈/null이면 무시)
     * @param tags         태그 목록 (빈/null이면 무시). 하나라도 부분 일치하면 포함(OR)
     * @param keyword      name/description LIKE (빈/null이면 무시)
     * @param sort         정렬 기준 (recent/usage/name/updated, null이면 recent)
     * @param direction    정렬 방향 (asc/desc, null이면 기준별 기본 방향)
     */
    @Transactional(readOnly = true)
    public List<RecipeSummaryResponse> list(List<Long> apiSpecIds, List<String> visibilities,
                                            List<String> tags, String keyword,
                                            String sort, String direction) {
        Long actorId = CurrentUser.id();
        UserRole actorRole = CurrentUser.role();

        List<Long> specFilter = emptyToNull(apiSpecIds);
        List<Visibility> visibilityFilter = parseVisibilities(visibilities);
        String keywordFilter = blankToNull(keyword);
        List<String> tagFilter = normalizeTags(tags);

        String sortKey = sort == null || sort.isBlank() ? "recent" : sort.trim().toLowerCase(Locale.ROOT);
        boolean descending = resolveDescending(sortKey, direction);

        List<Recipe> recipes;
        if ("recent".equals(sortKey)) {
            // lastUsedAt 정렬은 null 후행을 보장해야 하므로 전용 쿼리 사용
            recipes = recipeRepository.searchRecent(specFilter, visibilityFilter, keywordFilter, actorId, descending);
        } else {
            recipes = recipeRepository.search(specFilter, visibilityFilter, keywordFilter, actorId,
                    resolveSort(sortKey, descending));
        }

        // 태그 다중 필터 후처리 (OR LIKE): 태그 문자열(JSON)에 후보 중 하나라도 포함되면 통과
        if (tagFilter != null) {
            recipes = recipes.stream().filter(r -> matchesAnyTag(r.getTags(), tagFilter)).toList();
        }

        return recipes.stream().map(r -> toSummary(r, actorId, actorRole)).toList();
    }

    /**
     * 레시피 수정. 저장 직전 상태를 RecipeVersion에 VERSION_NO=현재값으로 스냅샷한 뒤
     * Recipe를 갱신하고 CURRENT_VERSION을 +1한 후 재검증한다.
     * 순환 참조가 있으면 스냅샷/갱신 전에 400으로 거부된다.
     *
     * <p>권한: 남의 PRIVATE/삭제/없음 = 404, 공통을 non-admin이 수정 시도 = 403.
     * 공개범위 전환(COMMON으로 설정 or 기존 COMMON 변경)은 ADMIN만(canSetCommon) → 위반 시 403.
     */
    @Transactional
    public RecipeDetailResponse update(Long id, RecipeUpdateRequest request) {
        Recipe recipe = requireModifiable(id);

        if (request.name() == null || request.name().isBlank()) {
            throw ApiException.invalidRequest("name is required");
        }

        Visibility newVisibility = request.visibility() == null ? Visibility.PRIVATE : request.visibility();
        // 공개범위 전환 권한: 대상이 COMMON이거나 기존이 COMMON이면 ADMIN만 가능
        boolean touchesCommon = newVisibility == Visibility.COMMON || recipe.getVisibility() == Visibility.COMMON;
        if (touchesCommon && !accessPolicy.canSetCommon(CurrentUser.role())) {
            throw ApiException.forbidden("Only ADMIN can set or change COMMON visibility");
        }

        // 새 스텝 JSON을 먼저 검증 (순환이면 여기서 400 → 스냅샷/갱신 안 함)
        String newStepsJson = RecipeJsonUtil.toJsonString(request.steps());
        RecipeValidationResult result = validator.validate(id, recipe.getApiSpecId(), newStepsJson);

        applyUpdate(recipe, request.name(), request.description(), newVisibility,
                RecipeJsonUtil.toJsonString(request.tags()),
                RecipeJsonUtil.toJsonString(request.variables()),
                newStepsJson,
                RecipeJsonUtil.toJsonString(request.resultDefinition()),
                request.resultTemplate(),
                result);

        Recipe saved = recipeRepository.save(recipe);
        log.info("Recipe updated: recipeId={}, newVersion={}, validation={}",
                saved.getId(), saved.getCurrentVersion(), result.status());
        return toDetail(saved);
    }

    /**
     * 소프트 삭제 (DELETED_AT = now). 권한: 남의 PRIVATE/삭제/없음 = 404, 공통을 non-admin이 삭제 시도 = 403.
     */
    @Transactional
    public void softDelete(Long id) {
        Recipe recipe = requireModifiable(id);
        recipe.setDeletedAt(LocalDateTime.now());
        recipeRepository.save(recipe);
        log.info("Recipe soft-deleted: recipeId={}", id);
    }

    /**
     * 레시피 복제. 원본을 조회 가능(canView)하면 개인 사본으로 복사한다(auth.md 복제 규칙).
     * 공통 레시피 복제도 항상 요청자 소유의 PRIVATE 사본이 된다. 사본은 v1, usageCount=0.
     * 남의 PRIVATE/삭제/없음 원본 = 404.
     */
    @Transactional
    public RecipeDetailResponse duplicate(Long id) {
        Recipe origin = requireViewable(id);
        Long actorId = CurrentUser.id();

        Recipe copy = new Recipe(actorId, origin.getApiSpecId(), origin.getName() + " (사본)");
        copy.setDescription(origin.getDescription());
        copy.setVisibility(Visibility.PRIVATE); // 공통 복제도 개인 사본
        copy.setTags(origin.getTags());
        copy.setVariablesJson(origin.getVariablesJson());
        copy.setStepsJson(origin.getStepsJson());
        copy.setResultDefinitionJson(origin.getResultDefinitionJson());
        copy.setResultTemplate(origin.getResultTemplate());
        copy.setCurrentVersion(1);
        copy.setUsageCount(0);

        // 사본 검증 (원본 스텝을 그대로 검증). 순환이면 400 — 원본이 정상이면 사본도 정상
        RecipeValidationResult result = validator.validate(null, copy.getApiSpecId(), copy.getStepsJson());
        copy.setValidationStatus(result.status());
        copy.setValidationMessage(result.message());

        Recipe saved = recipeRepository.save(copy);
        log.info("Recipe duplicated: originId={}, copyId={}, owner={}", id, saved.getId(), actorId);
        return toDetail(saved);
    }

    // ── 버전 API ──

    /**
     * 버전 목록 커서 페이지 (versioning.md). 정렬 VERSION_NO DESC, 커서 versionNo &lt; cursor.
     * 권한은 조회 규칙과 동일(canView) — 남의 PRIVATE/삭제/없음 = 404.
     */
    @Transactional(readOnly = true)
    public CursorPage<RecipeVersionSummary> versions(Long recipeId, String cursor, Integer size) {
        requireViewable(recipeId); // 404 게이팅 (조회 권한)
        int limit = normalizeVersionSize(size);
        Integer cursorVersionNo = decodeVersionCursor(cursor);

        // hasNext 판정을 위해 limit+1건 조회
        List<RecipeVersion> rows = versionRepository.findByRecipeIdByCursor(
                recipeId, cursorVersionNo, PageRequest.of(0, limit + 1));

        boolean hasNext = rows.size() > limit;
        List<RecipeVersion> pageRows = hasNext ? rows.subList(0, limit) : rows;
        List<RecipeVersionSummary> items = pageRows.stream()
                .map(v -> new RecipeVersionSummary(v.getVersionNo(), v.getCreatedAt()))
                .toList();
        if (!hasNext) {
            return CursorPage.last(items);
        }
        RecipeVersion lastRow = pageRows.get(pageRows.size() - 1);
        return CursorPage.of(items, String.valueOf(lastRow.getVersionNo()));
    }

    /**
     * 특정 버전 상세 (versioning.md). 스냅샷을 펼쳐 RecipeDetailResponse 호환 + versionNo/createdAt로 내린다.
     * 권한은 조회 규칙과 동일(canView). 레시피 없음/삭제/남의PRIVATE 또는 versionNo 없음 = 404.
     */
    @Transactional(readOnly = true)
    public RecipeVersionDetailResponse versionDetail(Long recipeId, int versionNo) {
        Recipe recipe = requireViewable(recipeId);
        RecipeVersion version = versionRepository.findByRecipeIdAndVersionNo(recipeId, versionNo)
                .orElseThrow(() -> ApiException.recipeNotFound(recipeId));

        RecipeSnapshot snapshot = parseSnapshot(version.getSnapshotJson());
        boolean canEdit = accessPolicy.canModify(recipe, CurrentUser.id(), CurrentUser.role());

        return new RecipeVersionDetailResponse(
                recipeId,
                version.getVersionNo(),
                version.getCreatedAt(),
                recipe.getApiSpecId(),
                snapshot.name(),
                snapshot.description(),
                StatusView.of(snapshot.visibility()),
                RecipeJsonUtil.parseTags(snapshot.tagsJson()),
                RecipeJsonUtil.toObject(snapshot.variablesJson()),
                RecipeJsonUtil.toObject(snapshot.stepsJson()),
                RecipeJsonUtil.toObject(snapshot.resultDefinitionJson()),
                snapshot.resultTemplate(),
                canEdit);
    }

    /**
     * 버전 복원 (versioning.md 복원 정책). 대상 버전 스냅샷을 현재 레시피에 통짜 반영하되,
     * 그 반영을 <b>새 버전 커밋</b>으로 남긴다(직전 상태 스냅샷 + 반영 + CURRENT_VERSION+1 + 재검증).
     * 재검증 결과가 INVALID여도 복원은 성공한다(상태만 표시). 응답은 복원 후 상세.
     *
     * <p>권한은 변경 규칙과 동일(canModify) — 남의 PRIVATE/삭제/없음 = 404, 공통을 non-admin이 복원 = 403.
     * versionNo 없음 = 404.
     */
    @Transactional
    public RecipeDetailResponse restore(Long recipeId, int versionNo) {
        Recipe recipe = requireModifiable(recipeId);
        RecipeVersion version = versionRepository.findByRecipeIdAndVersionNo(recipeId, versionNo)
                .orElseThrow(() -> ApiException.recipeNotFound(recipeId));

        RecipeSnapshot snapshot = parseSnapshot(version.getSnapshotJson());

        // 스냅샷 스텝을 대상 apiSpecId 기준으로 재검증 (INVALID여도 복원 허용)
        RecipeValidationResult result = validator.validate(recipeId, recipe.getApiSpecId(), snapshot.stepsJson());

        applyUpdate(recipe, snapshot.name(), snapshot.description(), snapshot.visibility(),
                snapshot.tagsJson(), snapshot.variablesJson(), snapshot.stepsJson(),
                snapshot.resultDefinitionJson(), snapshot.resultTemplate(), result);

        Recipe saved = recipeRepository.save(recipe);
        log.info("Recipe restored: recipeId={}, fromVersion={}, newVersion={}, validation={}",
                recipeId, versionNo, saved.getCurrentVersion(), result.status());
        return toDetail(saved);
    }

    // ── 권한 게이팅 헬퍼 ──

    /** 조회 게이팅: 미삭제 + canView. 없음/삭제/남의 PRIVATE = 404 (존재 은폐) */
    private Recipe requireViewable(Long id) {
        Recipe recipe = recipeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.recipeNotFound(id));
        if (!accessPolicy.canView(recipe, CurrentUser.id(), CurrentUser.role())) {
            // 남의 PRIVATE → 존재 자체를 은폐 (404)
            throw ApiException.recipeNotFound(id);
        }
        return recipe;
    }

    /**
     * 변경 게이팅: 미삭제 + (canView 통과) + canModify. 4vs3 원칙:
     * - 조회조차 불가(남의 PRIVATE)/삭제/없음 = 404
     * - 조회는 되나 변경 불가(공통을 non-admin이) = 403
     */
    private Recipe requireModifiable(Long id) {
        Recipe recipe = recipeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.recipeNotFound(id));
        Long actorId = CurrentUser.id();
        UserRole actorRole = CurrentUser.role();
        if (!accessPolicy.canView(recipe, actorId, actorRole)) {
            throw ApiException.recipeNotFound(id); // 남의 PRIVATE → 404
        }
        if (!accessPolicy.canModify(recipe, actorId, actorRole)) {
            throw ApiException.forbidden("You do not have permission to modify this recipe"); // 공통 non-admin → 403
        }
        return recipe;
    }

    // ── 공통 갱신 로직 ──

    /**
     * 현재 상태를 버전 스냅샷으로 남긴 뒤 새 값으로 갱신하고 CURRENT_VERSION을 +1한다.
     * update/restore가 공유한다(직전 상태 스냅샷 + 반영 + 버전 증가 + 검증 상태 반영).
     */
    private void applyUpdate(Recipe recipe, String name, String description, Visibility visibility,
                             String tagsJson, String variablesJson, String stepsJson,
                             String resultDefinitionJson, String resultTemplate,
                             RecipeValidationResult result) {
        // 저장 직전 상태를 스냅샷 (VERSION_NO = 현재 버전)
        String snapshot = snapshotOf(recipe);
        versionRepository.save(new RecipeVersion(recipe.getId(), recipe.getCurrentVersion(), snapshot));

        recipe.setName(name);
        recipe.setDescription(description);
        recipe.setVisibility(visibility == null ? Visibility.PRIVATE : visibility);
        recipe.setTags(tagsJson);
        recipe.setVariablesJson(variablesJson);
        recipe.setStepsJson(stepsJson);
        recipe.setResultDefinitionJson(resultDefinitionJson);
        recipe.setResultTemplate(resultTemplate);
        recipe.setCurrentVersion(recipe.getCurrentVersion() + 1);
        recipe.setValidationStatus(result.status());
        recipe.setValidationMessage(result.message());
    }

    // ── 필터/정렬 헬퍼 ──

    /** 빈 리스트/null을 null로 정규화 (쿼리에서 IS NULL 조건으로 무시되게) */
    private <T> List<T> emptyToNull(List<T> list) {
        return (list == null || list.isEmpty()) ? null : list;
    }

    /** 공백 문자열을 null로 */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** 태그 후보 정규화: 공백 제거 + 빈 값 제외. 결과가 비면 null */
    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return null;
        }
        List<String> normalized = tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .toList();
        return normalized.isEmpty() ? null : normalized;
    }

    /** 태그 문자열(JSON)에 후보 중 하나라도 부분 일치(대소문자 무시)하면 true */
    private boolean matchesAnyTag(String tagsJson, List<String> candidates) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return false;
        }
        String lower = tagsJson.toLowerCase(Locale.ROOT);
        return candidates.stream().anyMatch(lower::contains);
    }

    /** 공개범위 코드 문자열 목록을 enum으로 파싱. 알 수 없는 값은 400. 빈/null이면 null(무시) */
    private List<Visibility> parseVisibilities(List<String> visibilities) {
        List<String> cleaned = visibilities == null ? null : visibilities.stream()
                .filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
        if (cleaned == null || cleaned.isEmpty()) {
            return null;
        }
        try {
            return cleaned.stream()
                    .map(v -> Visibility.valueOf(v.toUpperCase(Locale.ROOT)))
                    .toList();
        } catch (IllegalArgumentException e) {
            throw ApiException.invalidRequest("Unknown visibility filter: " + cleaned);
        }
    }

    /** 정렬 방향 해석. direction 명시 없으면 기준별 기본(모두 DESC 기본, name만 ASC 기본) */
    private boolean resolveDescending(String sortKey, String direction) {
        if (direction != null && !direction.isBlank()) {
            return !"asc".equalsIgnoreCase(direction.trim());
        }
        // 기본 방향: name은 오름차순, 나머지(recent/usage/updated)는 내림차순
        return !"name".equals(sortKey);
    }

    /** name/usage/updated 정렬을 Spring Data Sort로 변환 (recent는 searchRecent 전용 처리) */
    private Sort resolveSort(String sortKey, boolean descending) {
        Sort.Direction dir = descending ? Sort.Direction.DESC : Sort.Direction.ASC;
        return switch (sortKey) {
            case "usage" -> Sort.by(dir, "usageCount").and(Sort.by(Sort.Direction.DESC, "id"));
            case "name" -> Sort.by(dir, "name").and(Sort.by(Sort.Direction.DESC, "id"));
            case "updated" -> Sort.by(dir, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"));
            default -> throw ApiException.invalidRequest("Unknown sort key: " + sortKey);
        };
    }

    // ── 버전 커서/사이즈 헬퍼 ──

    /** 버전 페이지 size 정규화 (기본 20, 1~50 클램프) */
    private int normalizeVersionSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_VERSION_PAGE_SIZE;
        }
        return Math.min(size, MAX_VERSION_PAGE_SIZE);
    }

    /** 버전 커서 디코딩(versionNo). null/빈/형식불량이면 첫 페이지(null) */
    private Integer decodeVersionCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(cursor.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid version cursor, treating as first page: {}", cursor);
            return null;
        }
    }

    // ── 스냅샷 직렬화/역직렬화 ──

    /** 생성 시 필수 메타(작성자/스펙/이름) 검증 */
    private void validateRequiredMeta(Long ownerUserId, Long apiSpecId, String name) {
        if (ownerUserId == null) {
            throw ApiException.invalidRequest("ownerUserId is required");
        }
        if (apiSpecId == null) {
            throw ApiException.invalidRequest("apiSpecId is required");
        }
        if (name == null || name.isBlank()) {
            throw ApiException.invalidRequest("name is required");
        }
    }

    /**
     * 수정 이력 복원용 전체 스냅샷 JSON 생성 (메타+스텝+변수+결과).
     * <b>주의</b>: stepsJson/variablesJson/resultDefinitionJson/tags는 이미 JSON 문자열인데
     * {@code put(String)}으로 다시 문자열로 감싼다(이중 인코딩). {@link #parseSnapshot}가 이를 정확히 역으로 푼다.
     */
    private String snapshotOf(Recipe recipe) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", recipe.getName());
        node.put("description", recipe.getDescription());
        node.put("visibility", recipe.getVisibility().name());
        node.put("tags", recipe.getTags());
        node.put("variablesJson", recipe.getVariablesJson());
        node.put("stepsJson", recipe.getStepsJson());
        node.put("resultDefinitionJson", recipe.getResultDefinitionJson());
        node.put("resultTemplate", recipe.getResultTemplate());
        node.put("versionNo", recipe.getCurrentVersion());
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize recipe snapshot", e);
        }
    }

    /**
     * {@link #snapshotOf}가 만든 스냅샷 JSON을 역으로 파싱한다.
     * 각 JSON 필드(tags/variablesJson/stepsJson/resultDefinitionJson)는 스냅샷에 <b>문자열로</b> 저장되어 있으므로
     * 노드에서 텍스트 값을 그대로 꺼내 Recipe가 보관하던 원래 JSON 문자열을 복원한다.
     *
     * <p>구형/신형 스냅샷을 관대하게 읽는다: 해당 필드가 문자열 노드면 그 텍스트를 쓰고, (혹시) 객체/배열
     * 노드로 저장돼 있으면 다시 직렬화해 문자열로 되돌린다. null/누락 필드는 null로 둔다.
     * visibility 파싱 실패 시 PRIVATE로 폴백(강등이 아니라 손상 방어 — 정상 스냅샷엔 항상 값이 있음).
     */
    private RecipeSnapshot parseSnapshot(String snapshotJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(snapshotJson);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse recipe snapshot", e);
        }
        String name = textOrNull(root, "name");
        String description = textOrNull(root, "description");
        Visibility visibility = parseVisibilityText(textOrNull(root, "visibility"));
        String tagsJson = jsonStringField(root, "tags");
        String variablesJson = jsonStringField(root, "variablesJson");
        String stepsJson = jsonStringField(root, "stepsJson");
        String resultDefinitionJson = jsonStringField(root, "resultDefinitionJson");
        String resultTemplate = textOrNull(root, "resultTemplate");
        return new RecipeSnapshot(name, description, visibility, tagsJson, variablesJson,
                stepsJson, resultDefinitionJson, resultTemplate);
    }

    /** 텍스트 필드 추출 (null/누락이면 null) */
    private String textOrNull(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    /**
     * "원래 JSON 문자열"이었던 필드를 복원한다. 스냅샷에 문자열 노드로 저장돼 있으면 그 텍스트를 그대로,
     * (구/변형 스냅샷에서) 객체/배열 노드로 저장돼 있으면 다시 직렬화해 문자열로 되돌린다.
     */
    private String jsonStringField(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String text = node.asText();
            return text.isBlank() ? null : text;
        }
        // 문자열이 아니면(객체/배열) 다시 직렬화해 문자열 JSON으로 복원
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    /** visibility 텍스트 파싱 (손상 방어: 파싱 실패 시 PRIVATE) */
    private Visibility parseVisibilityText(String text) {
        if (text == null || text.isBlank()) {
            return Visibility.PRIVATE;
        }
        try {
            return Visibility.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // 스냅샷 visibility 값이 손상돼 파싱 불가 → 안전하게 PRIVATE로 강등. 조용한 강등 추적용 경고.
            log.warn("Corrupt snapshot visibility '{}', defaulting to PRIVATE", text);
            return Visibility.PRIVATE;
        }
    }

    /** 파싱된 스냅샷 값 홀더 (내부 전용). 각 JSON 필드는 원래 JSON 문자열 형태 */
    private record RecipeSnapshot(
            String name,
            String description,
            Visibility visibility,
            String tagsJson,
            String variablesJson,
            String stepsJson,
            String resultDefinitionJson,
            String resultTemplate) {
    }

    // ── 응답 매핑 ──

    /** 목록 행 매핑 (canEdit = 요청자 기준 변경 가능 여부) */
    private RecipeSummaryResponse toSummary(Recipe recipe, Long actorId, UserRole actorRole) {
        return new RecipeSummaryResponse(
                recipe.getId(),
                recipe.getName(),
                recipe.getDescription(),
                recipe.getApiSpecId(),
                StatusView.of(recipe.getVisibility()),
                RecipeJsonUtil.parseTags(recipe.getTags()),
                StatusView.of(recipe.getValidationStatus()),
                recipe.getCurrentVersion(),
                recipe.getUsageCount(),
                recipe.getLastUsedAt(),
                accessPolicy.canModify(recipe, actorId, actorRole));
    }

    /** 상세 매핑 (JSON 문자열을 노드로 파싱, canEdit = 요청자 기준 변경 가능 여부) */
    private RecipeDetailResponse toDetail(Recipe recipe) {
        Object variables = RecipeJsonUtil.toObject(recipe.getVariablesJson());
        Object steps = RecipeJsonUtil.toObject(recipe.getStepsJson());
        Object resultDefinition = RecipeJsonUtil.toObject(recipe.getResultDefinitionJson());
        boolean canEdit = accessPolicy.canModify(recipe, CurrentUser.id(), CurrentUser.role());

        return new RecipeDetailResponse(
                recipe.getId(),
                recipe.getOwnerUserId(),
                recipe.getApiSpecId(),
                recipe.getName(),
                recipe.getDescription(),
                StatusView.of(recipe.getVisibility()),
                RecipeJsonUtil.parseTags(recipe.getTags()),
                variables,
                steps,
                resultDefinition,
                recipe.getResultTemplate(),
                recipe.getCurrentVersion(),
                StatusView.of(recipe.getValidationStatus()),
                recipe.getValidationMessage(),
                recipe.getUsageCount(),
                recipe.getLastUsedAt(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt(),
                canEdit);
    }
}
