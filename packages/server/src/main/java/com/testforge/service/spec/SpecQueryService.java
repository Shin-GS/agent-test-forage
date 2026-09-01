package com.testforge.service.spec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.common.error.ApiException;
import com.testforge.dto.spec.SpecDetailResponse;
import com.testforge.dto.spec.SpecSummaryResponse;
import com.testforge.dto.common.StatusView;
import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.AuthProfile;
import com.testforge.entity.spec.enums.EndpointStatus;
import com.testforge.entity.spec.enums.SpecStatus;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import com.testforge.repository.spec.AuthProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 스펙 조회/관리(비활성/활성/삭제) 로직. 관리자 화면(admin.md 스펙 관리)이 소비한다.
 * 등록/heartbeat 흐름과 분리하여, 라이브러리 등록 토큰과 무관한 조회 API를 담당한다.
 */
@Service
public class SpecQueryService {

    private static final Logger log = LoggerFactory.getLogger(SpecQueryService.class);

    private final ApiSpecRepository specRepository;
    private final ApiEndpointRepository endpointRepository;
    private final AuthProfileRepository authProfileRepository;

    // capabilities(JSON 문자열)를 List<String>으로 파싱하기 위한 로컬 매퍼.
    // 등록 서비스가 수동 직렬화를 쓰는 것과 대칭으로, 공용 매퍼 빈에 의존하지 않는다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpecQueryService(ApiSpecRepository specRepository,
                            ApiEndpointRepository endpointRepository,
                            AuthProfileRepository authProfileRepository) {
        this.specRepository = specRepository;
        this.endpointRepository = endpointRepository;
        this.authProfileRepository = authProfileRepository;
    }

    /** 미삭제 스펙 목록 (name 오름차순). apiCount는 ACTIVE 엔드포인트 수. */
    @Transactional(readOnly = true)
    public List<SpecSummaryResponse> list() {
        List<ApiSpec> specs = specRepository.findByDeletedAtIsNullOrderByNameAsc();
        return specs.stream().map(this::toSummary).toList();
    }

    /** 미삭제 스펙 상세. 없거나 삭제된 경우 404. */
    @Transactional(readOnly = true)
    public SpecDetailResponse detail(Long id) {
        ApiSpec spec = specRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.specNotFound(id));

        List<ApiEndpoint> endpoints =
                endpointRepository.findByApiSpecIdOrderByPathAscHttpMethodAsc(id);
        List<AuthProfile> profiles = authProfileRepository.findByApiSpecId(id);

        return toDetail(spec, endpoints, profiles);
    }

    /** 스펙 수동 비활성화 (STATUS = INACTIVE). AI 매칭/실행 대상에서 제외된다. */
    @Transactional
    public void deactivate(Long id) {
        ApiSpec spec = requireActiveSpec(id);
        spec.setStatus(SpecStatus.INACTIVE);
        specRepository.save(spec);
        log.info("Spec deactivated: specId={}", id);
    }

    /**
     * 스펙 활성화 (INACTIVE → ACTIVE 복귀). 관리자만 수행.
     * INACTIVE가 아닌 경우(ACTIVE/STALE)는 heartbeat가 관리하는 상태이므로 변경하지 않는다.
     */
    @Transactional
    public void activate(Long id) {
        ApiSpec spec = requireActiveSpec(id);
        if (spec.getStatus() == SpecStatus.INACTIVE) {
            spec.setStatus(SpecStatus.ACTIVE);
            specRepository.save(spec);
            log.info("Spec activated: specId={}", id);
        }
    }

    /** 스펙 소프트 삭제 (DELETED_AT = now). 참조 레시피는 유효성 검증에서 경고된다. */
    @Transactional
    public void softDelete(Long id) {
        ApiSpec spec = requireActiveSpec(id);
        spec.setDeletedAt(LocalDateTime.now());
        specRepository.save(spec);
        log.info("Spec soft-deleted: specId={}", id);
    }

    /** 미삭제 스펙 조회 후 없으면 404 */
    private ApiSpec requireActiveSpec(Long id) {
        return specRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.specNotFound(id));
    }

    /** 목록 행 매핑 (ACTIVE 엔드포인트 수 집계 포함) */
    private SpecSummaryResponse toSummary(ApiSpec spec) {
        long apiCount = endpointRepository.countByApiSpecIdAndStatus(
                spec.getId(), EndpointStatus.ACTIVE);
        return new SpecSummaryResponse(
                spec.getId(),
                spec.getName(),
                spec.getBaseUrl(),
                StatusView.of(spec.getStatus()),
                apiCount,
                spec.getLastHeartbeatAt());
    }

    /** 상세 매핑 */
    private SpecDetailResponse toDetail(ApiSpec spec,
                                        List<ApiEndpoint> endpoints,
                                        List<AuthProfile> profiles) {
        SpecDetailResponse.ServiceInfo serviceInfo = new SpecDetailResponse.ServiceInfo(
                spec.getServiceDescription(),
                spec.getServiceDomain(),
                parseCapabilities(spec.getServiceCapabilities()),
                spec.getServiceNotes(),
                spec.isAdminEdited());

        List<SpecDetailResponse.EndpointItem> endpointItems = endpoints.stream()
                .map(e -> new SpecDetailResponse.EndpointItem(
                        e.getId(),
                        e.getHttpMethod(),
                        e.getPath(),
                        e.getSummary(),
                        StatusView.of(e.getStatus()),
                        e.isExcluded(),
                        e.isConfirmRequired()))
                .toList();

        List<SpecDetailResponse.AuthProfileItem> profileItems = profiles.stream()
                .map(p -> new SpecDetailResponse.AuthProfileItem(p.getName(), p.getLoginPageUrl()))
                .toList();

        SpecDetailResponse.Diagnostics diagnostics = new SpecDetailResponse.Diagnostics(
                spec.getClientLang(),
                spec.getClientVersion(),
                spec.getSchemaVersion());

        return new SpecDetailResponse(
                spec.getId(),
                spec.getName(),
                spec.getBaseUrl(),
                StatusView.of(spec.getStatus()),
                spec.getLastHeartbeatAt(),
                serviceInfo,
                endpointItems,
                profileItems,
                diagnostics);
    }

    /**
     * capabilities JSON 문자열을 List<String>으로 파싱한다.
     * null/빈 값이거나 파싱 실패 시 빈 리스트를 반환한다(조회는 실패시키지 않음).
     */
    private List<String> parseCapabilities(String capabilitiesJson) {
        if (capabilitiesJson == null || capabilitiesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(capabilitiesJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse service capabilities JSON: {}", e.getMessage());
            return List.of();
        }
    }
}
