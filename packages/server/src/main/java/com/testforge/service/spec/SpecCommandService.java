package com.testforge.service.spec;

import com.testforge.common.error.ApiException;
import com.testforge.dto.common.StatusView;
import com.testforge.dto.spec.DuplicateEndpointRequest;
import com.testforge.dto.spec.EndpointDetailResponse;
import com.testforge.dto.spec.ManualEndpointRequest;
import com.testforge.dto.spec.ManualSpecRequest;
import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.AuthProfile;
import com.testforge.entity.spec.enums.AuthProfileStatus;
import com.testforge.entity.spec.enums.EndpointSource;
import com.testforge.entity.spec.enums.EndpointStatus;
import com.testforge.entity.spec.enums.SpecStatus;
import com.testforge.entity.recipe.Recipe;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import com.testforge.repository.spec.AuthProfileRepository;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.utils.RecipeJsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 수동 스펙/엔드포인트 쓰기 로직 (admin.md 스펙 관리 — 수동 등록/편집).
 * 조회는 {@link SpecQueryService}가, 라이브러리 자동 등록은 {@link SpecRegistrationService}가 담당한다.
 *
 * <p>권한(ADMIN)은 컨트롤러가 게이트하며, 이 서비스는 도메인 검증/보존 규칙에 집중한다:
 * <ul>
 *   <li>baseUrl은 http/https 스킴 필수, 기존 미삭제 스펙과 겹치면 신규 대신 병합</li>
 *   <li>엔드포인트는 (specId, method, path) 유니크 — 중복 생성/복제 차단</li>
 *   <li>삭제는 하드 삭제 금지 — 레시피가 PK를 참조하므로 DEPRECATED로 상태 전이</li>
 *   <li>LIBRARY 엔드포인트 수정은 메타(excluded/confirm)만 반영, 스키마는 무시</li>
 * </ul>
 */
@Service
public class SpecCommandService {

    private static final Logger log = LoggerFactory.getLogger(SpecCommandService.class);
    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private final ApiSpecRepository specRepository;
    private final ApiEndpointRepository endpointRepository;
    private final AuthProfileRepository authProfileRepository;
    private final RecipeRepository recipeRepository;
    private final EndpointOperationSerializer operationSerializer;

    // 복제 시 기본 사본 path의 "-copy" 자동 suffix 탐색 상한 (무한루프 방지).
    private static final int MAX_COPY_SUFFIX = 100;

    // capabilities를 JSON 배열 문자열로 직렬화하기 위한 로컬 매퍼(공용 빈 비의존, 등록 서비스와 대칭).
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpecCommandService(ApiSpecRepository specRepository,
                              ApiEndpointRepository endpointRepository,
                              AuthProfileRepository authProfileRepository,
                              RecipeRepository recipeRepository,
                              EndpointOperationSerializer operationSerializer) {
        this.specRepository = specRepository;
        this.endpointRepository = endpointRepository;
        this.authProfileRepository = authProfileRepository;
        this.recipeRepository = recipeRepository;
        this.operationSerializer = operationSerializer;
    }

    // ─────────────────────────────── 스펙 ───────────────────────────────

    /**
     * 서버 수동 생성. baseUrl이 기존 미삭제 스펙과 같으면 신규 생성 대신 그 스펙에 메타를 병합한다.
     * 생성/병합 모두 adminEdited=true, ACTIVE로 설정하며 스펙 id를 반환한다.
     */
    @Transactional
    public Long createSpec(ManualSpecRequest request) {
        String name = trimToNull(request.name());
        String baseUrl = trimToNull(request.baseUrl());
        if (name == null) {
            throw ApiException.invalidRequest("name is required");
        }
        if (baseUrl == null) {
            throw ApiException.invalidRequest("baseUrl is required");
        }
        validateBaseUrl(baseUrl);

        // baseUrl 병합 규칙: 이미 등록된 미삭제 스펙이면 신규 생성 대신 병합.
        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(baseUrl)
                .orElseGet(() -> new ApiSpec(baseUrl));

        spec.setName(name);
        applyMeta(spec, request);
        spec.setAdminEdited(true);
        spec.setStatus(SpecStatus.ACTIVE);

        ApiSpec saved = specRepository.save(spec);
        replaceAuthProfiles(saved.getId(), request.authProfiles());

        log.info("Manual spec saved: specId={}, baseUrl={}", saved.getId(), saved.getBaseUrl());
        return saved.getId();
    }

    /**
     * 서버 메타 수정. baseUrl은 식별 키이므로 요청에 와도 무시한다. name/description/domain/
     * capabilities/notes/authProfiles를 반영하고 adminEdited=true로 표시한다.
     */
    @Transactional
    public void updateSpec(Long id, ManualSpecRequest request) {
        ApiSpec spec = requireSpec(id);

        String name = trimToNull(request.name());
        if (name != null) {
            spec.setName(name);
        }
        applyMeta(spec, request);
        spec.setAdminEdited(true);
        specRepository.save(spec);

        replaceAuthProfiles(id, request.authProfiles());
        log.info("Manual spec updated: specId={}", id);
    }

    /** 서비스 메타(description/domain/capabilities/notes) 반영 */
    private void applyMeta(ApiSpec spec, ManualSpecRequest request) {
        spec.setServiceDescription(trimToNull(request.description()));
        spec.setServiceDomain(trimToNull(request.domain()));
        spec.setServiceCapabilities(writeCapabilities(request.capabilities()));
        spec.setServiceNotes(trimToNull(request.notes()));
    }

    /**
     * 인증 프로필 교체. authProfiles가 null이면 변경하지 않는다(부분 수정). 명시되면
     * 요청에 있는 이름은 ACTIVE로 upsert(PK 유지), 사라진 이름은 INACTIVE로 표시한다.
     */
    private void replaceAuthProfiles(Long specId, List<ManualSpecRequest.AuthProfileInput> inputs) {
        if (inputs == null) {
            return;
        }
        List<AuthProfile> existing = authProfileRepository.findByApiSpecId(specId);
        java.util.Map<String, AuthProfile> byName = new java.util.HashMap<>();
        for (AuthProfile p : existing) {
            byName.put(p.getName(), p);
        }

        List<AuthProfile> toSave = new java.util.ArrayList<>();
        for (ManualSpecRequest.AuthProfileInput in : inputs) {
            if (in == null || in.name() == null || in.name().isBlank()) {
                continue;
            }
            AuthProfile profile = byName.remove(in.name());
            if (profile == null) {
                profile = new AuthProfile(specId, in.name(), in.loginPageUrl());
            }
            profile.setLoginPageUrl(in.loginPageUrl());
            profile.setStatus(AuthProfileStatus.ACTIVE);
            toSave.add(profile);
        }
        // 요청에서 사라진 프로필은 삭제하지 않고 INACTIVE로 표시(부활 가능).
        for (AuthProfile stale : byName.values()) {
            if (stale.getStatus() != AuthProfileStatus.INACTIVE) {
                stale.setStatus(AuthProfileStatus.INACTIVE);
                toSave.add(stale);
            }
        }
        authProfileRepository.saveAll(toSave);
    }

    // ───────────────────────────── 엔드포인트 ─────────────────────────────

    /** 엔드포인트 편집용 단건 조회. 스펙/엔드포인트 소속 불일치 시 404. */
    @Transactional(readOnly = true)
    public EndpointDetailResponse getEndpoint(Long specId, Long endpointId) {
        requireSpec(specId);
        ApiEndpoint endpoint = requireEndpoint(specId, endpointId);
        return toDetail(endpoint);
    }

    /** 엔드포인트 수동 생성 (source=MANUAL). method/path 검증 + 중복 차단 후 operationJson 직렬화 저장. */
    @Transactional
    public Long createEndpoint(Long specId, ManualEndpointRequest request) {
        requireSpec(specId);

        String method = normalizeMethod(request.method());
        String path = validatePath(request.path());
        if (endpointRepository.existsByApiSpecIdAndHttpMethodAndPath(specId, method, path)) {
            throw ApiException.invalidRequest("Endpoint already exists: " + method + " " + path);
        }

        ApiEndpoint endpoint = new ApiEndpoint(specId, method, path);
        endpoint.setSource(EndpointSource.MANUAL);
        endpoint.setStatus(EndpointStatus.ACTIVE);
        applySchema(endpoint, request, method);
        applyMeta(endpoint, request);

        ApiEndpoint saved = endpointRepository.save(endpoint);
        log.info("Manual endpoint created: specId={}, endpointId={}, {} {}",
                specId, saved.getId(), method, path);
        return saved.getId();
    }

    /**
     * 엔드포인트 수정. MANUAL이면 스키마+메타 전체를 반영하고, LIBRARY이면 메타(excluded/confirm)만
     * 반영하고 스키마 필드(method/path/parameters/requestBody/responses)는 조용히 무시한다.
     */
    @Transactional
    public void updateEndpoint(Long specId, Long endpointId, ManualEndpointRequest request) {
        requireSpec(specId);
        ApiEndpoint endpoint = requireEndpoint(specId, endpointId);

        if (endpoint.getSource() == EndpointSource.MANUAL) {
            String method = normalizeMethod(request.method());
            String path = validatePath(request.path());
            // method/path가 바뀌는 경우에만 중복 검사 (자기 자신 제외).
            boolean keyChanged = !method.equals(endpoint.getHttpMethod()) || !path.equals(endpoint.getPath());
            if (keyChanged
                    && endpointRepository.existsByApiSpecIdAndHttpMethodAndPath(specId, method, path)) {
                throw ApiException.invalidRequest("Endpoint already exists: " + method + " " + path);
            }
            endpoint.setHttpMethod(method);
            endpoint.setPath(path);
            applySchema(endpoint, request, method);
        }
        // LIBRARY/MANUAL 공통: 메타는 항상 반영.
        applyMeta(endpoint, request);
        endpointRepository.save(endpoint);
        log.info("Manual endpoint updated: specId={}, endpointId={}, source={}",
                specId, endpointId, endpoint.getSource());
    }

    /**
     * 엔드포인트 삭제. source와 레시피 참조 여부에 따라 하드 삭제 vs 소프트 삭제(DEPRECATED)로 분기한다.
     * <ul>
     *   <li>LIBRARY: 라이브러리 재등록으로 되살아날 수 있으므로 항상 DEPRECATED로 보존. 이미 DEPRECATED면 no-op.</li>
     *   <li>MANUAL + 참조 레시피 있음: 참조 무결성 보호를 위해 DEPRECATED로 보존(하드 삭제 금지).</li>
     *   <li>MANUAL + 참조 레시피 없음: 행을 실제로 제거(하드 삭제) — 같은 (method,path) 재추가를 허용.</li>
     * </ul>
     */
    @Transactional
    public void deleteEndpoint(Long specId, Long endpointId) {
        requireSpec(specId);
        ApiEndpoint endpoint = requireEndpoint(specId, endpointId);

        boolean hardDeletable = endpoint.getSource() == EndpointSource.MANUAL
                && !isReferencedByRecipe(specId, endpointId);
        if (hardDeletable) {
            endpointRepository.delete(endpoint);
            log.info("Manual endpoint hard-deleted (no recipe reference): specId={}, endpointId={}",
                    specId, endpointId);
            return;
        }

        // LIBRARY 또는 참조 레시피가 있는 MANUAL: DEPRECATED로 보존 (멱등).
        if (endpoint.getStatus() != EndpointStatus.DEPRECATED) {
            endpoint.setStatus(EndpointStatus.DEPRECATED);
            endpointRepository.save(endpoint);
            log.info("Endpoint soft-deleted (DEPRECATED): specId={}, endpointId={}, source={}",
                    specId, endpointId, endpoint.getSource());
        }
    }

    /**
     * 대상 endpointId를 참조하는 미삭제 레시피가 있는지 확인한다. 역방향 조회 쿼리가 없으므로
     * 같은 스펙의 미삭제 레시피만 후보로 로드해 각 스텝을 스캔한다(type=api && endpointId 일치).
     */
    private boolean isReferencedByRecipe(Long specId, Long endpointId) {
        List<Recipe> candidates = recipeRepository.findByApiSpecIdAndDeletedAtIsNull(specId);
        for (Recipe recipe : candidates) {
            List<Map<String, Object>> steps;
            try {
                steps = RecipeJsonUtil.parseSteps(recipe.getStepsJson());
            } catch (IllegalArgumentException e) {
                // 파싱 불가한 스텝 JSON은 참조로 볼 수 없으므로 건너뛴다.
                continue;
            }
            for (Map<String, Object> step : steps) {
                if ("api".equals(asString(step.get("type")))
                        && endpointId.equals(asLong(step.get("endpointId")))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 엔드포인트 복제. 사본은 항상 source=MANUAL, status=ACTIVE. (method, path) 유니크 충돌을 피하기 위해
     * 요청 path가 있으면 사용하고, 없으면 원본 path에 "-copy" suffix를 붙인다. 충돌 시 400.
     */
    @Transactional
    public Long duplicateEndpoint(Long specId, Long endpointId, DuplicateEndpointRequest request) {
        requireSpec(specId);
        ApiEndpoint source = requireEndpoint(specId, endpointId);

        String method = source.getHttpMethod();
        String newPath;
        if (request != null && request.path() != null && !request.path().isBlank()) {
            // 요청 path가 온 경우: 검증 후 충돌 시 400 유지.
            newPath = validatePath(request.path());
            if (endpointRepository.existsByApiSpecIdAndHttpMethodAndPath(specId, method, newPath)) {
                throw ApiException.invalidRequest("Endpoint already exists: " + method + " " + newPath);
            }
        } else {
            // 기본 사본 path: "{path}-copy" 부터 시작해 충돌 시 "-copy-2", "-copy-3"... 순으로 빈 것 탐색.
            newPath = resolveCopyPath(specId, method, source.getPath());
        }

        ApiEndpoint copy = new ApiEndpoint(specId, method, newPath);
        copy.setSource(EndpointSource.MANUAL);
        copy.setStatus(EndpointStatus.ACTIVE);
        copy.setSummary(source.getSummary());
        copy.setOperationJson(source.getOperationJson());
        copy.setExcluded(source.isExcluded());
        copy.setConfirmRequired(source.isConfirmRequired());
        copy.setConfirmMessage(source.getConfirmMessage());

        ApiEndpoint saved = endpointRepository.save(copy);
        log.info("Endpoint duplicated: specId={}, fromId={}, newId={}, {} {}",
                specId, endpointId, saved.getId(), method, newPath);
        return saved.getId();
    }

    /** 스키마(operationJson) 직렬화 후 반영. GET/DELETE는 요청 바디 미포함. */
    private void applySchema(ApiEndpoint endpoint, ManualEndpointRequest request, String method) {
        endpoint.setSummary(trimToNull(request.summary()));
        boolean bodyAllowed = !"GET".equals(method) && !"DELETE".equals(method);
        String operationJson = operationSerializer.serialize(request, bodyAllowed);
        validateJson(operationJson);
        endpoint.setOperationJson(operationJson);
    }

    /** 메타 컬럼(excluded/confirmRequired/confirmMessage) 반영. null이면 기본값(false/유지 없음). */
    private void applyMeta(ApiEndpoint endpoint, ManualEndpointRequest request) {
        endpoint.setExcluded(Boolean.TRUE.equals(request.excluded()));
        boolean confirmRequired = Boolean.TRUE.equals(request.confirmRequired());
        endpoint.setConfirmRequired(confirmRequired);
        endpoint.setConfirmMessage(confirmRequired ? trimToNull(request.confirmMessage()) : null);
    }

    // ─────────────────────────────── 헬퍼 ───────────────────────────────

    /** 미삭제 스펙 조회 후 없으면 404 */
    private ApiSpec requireSpec(Long id) {
        return specRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.specNotFound(id));
    }

    /** 스펙 소속 엔드포인트 조회 후 없으면 404 (specId 불일치 포함) */
    private ApiEndpoint requireEndpoint(Long specId, Long endpointId) {
        return endpointRepository.findByIdAndApiSpecId(endpointId, specId)
                .orElseThrow(() -> ApiException.invalidRequest("Endpoint not found: " + endpointId));
    }

    /** baseUrl 형식 검증 (http/https 스킴 필수) */
    private void validateBaseUrl(String baseUrl) {
        String lower = baseUrl.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw ApiException.invalidRequest("baseUrl must start with http:// or https://");
        }
    }

    /** HTTP 메서드 정규화 + 화이트리스트 검증 */
    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            throw ApiException.invalidRequest("method is required");
        }
        String upper = method.trim().toUpperCase();
        if (!ALLOWED_METHODS.contains(upper)) {
            throw ApiException.invalidRequest("Unsupported HTTP method: " + method);
        }
        return upper;
    }

    /** 경로 검증 (/ 로 시작) */
    private String validatePath(String path) {
        String trimmed = trimToNull(path);
        if (trimmed == null || !trimmed.startsWith("/")) {
            throw ApiException.invalidRequest("path must start with '/'");
        }
        return trimmed;
    }

    /** operationJson이 유효 JSON인지 파싱 검증 */
    private void validateJson(String json) {
        if (json == null) {
            return;
        }
        try {
            objectMapper.readTree(json);
        } catch (Exception e) {
            throw ApiException.invalidRequest("Invalid operation JSON");
        }
    }

    /** capabilities를 JSON 배열 문자열로 직렬화 (null이면 빈 배열) */
    private String writeCapabilities(List<String> capabilities) {
        List<String> safe = capabilities != null ? capabilities : List.of();
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 편집용 단건 응답 매핑 */
    private EndpointDetailResponse toDetail(ApiEndpoint endpoint) {
        return new EndpointDetailResponse(
                endpoint.getId(),
                endpoint.getApiSpecId(),
                endpoint.getHttpMethod(),
                endpoint.getPath(),
                endpoint.getSummary(),
                StatusView.of(endpoint.getStatus()),
                StatusView.of(endpoint.getSource()),
                endpoint.isExcluded(),
                endpoint.isConfirmRequired(),
                endpoint.getConfirmMessage(),
                endpoint.getOperationJson());
    }

    /**
     * 기본 사본 path를 유니크하게 해석한다. "{basePath}-copy"를 먼저 시도하고, (method,path)가 이미
     * 있으면 "-copy-2", "-copy-3"... 순으로 빈 자리를 찾는다. 상한({@link #MAX_COPY_SUFFIX})까지도
     * 못 찾으면 마지막 후보로 400을 던진다.
     */
    private String resolveCopyPath(Long specId, String method, String basePath) {
        String candidate = basePath + "-copy";
        if (!endpointRepository.existsByApiSpecIdAndHttpMethodAndPath(specId, method, candidate)) {
            return candidate;
        }
        for (int i = 2; i <= MAX_COPY_SUFFIX; i++) {
            candidate = basePath + "-copy-" + i;
            if (!endpointRepository.existsByApiSpecIdAndHttpMethodAndPath(specId, method, candidate)) {
                return candidate;
            }
        }
        throw ApiException.invalidRequest("Endpoint already exists: " + method + " " + candidate);
    }

    /** Object를 문자열로 (null 허용) — 스텝 JSON의 type 판정용 */
    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** Object를 Long으로 (숫자/문자열 모두 허용, 실패 시 null) — 스텝 JSON의 endpointId 판정용 */
    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 공백을 null로 정규화 */
    private String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
