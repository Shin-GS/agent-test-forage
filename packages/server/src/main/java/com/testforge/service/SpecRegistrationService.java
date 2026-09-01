package com.testforge.service;

import com.testforge.dto.RegisterRequest;
import com.testforge.dto.RegisterResponse;
import com.testforge.entity.ApiEndpoint;
import com.testforge.entity.ApiSpec;
import com.testforge.entity.ApiSpecDocument;
import com.testforge.entity.AuthProfile;
import com.testforge.entity.EndpointStatus;
import com.testforge.entity.SpecStatus;
import com.testforge.parser.NormalizedSpec;
import com.testforge.parser.SpecRegistrationParser;
import com.testforge.parser.SpecRegistrationParserResolver;
import com.testforge.repository.ApiEndpointRepository;
import com.testforge.repository.ApiSpecDocumentRepository;
import com.testforge.repository.ApiSpecRepository;
import com.testforge.repository.AuthProfileRepository;
import com.testforge.utils.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 스펙 등록을 적용한다: 스펙 upsert, 원본 문서 재작성, 엔드포인트 분해/upsert(PK 유지),
 * 서비스 메타 관리자 우선 병합, 인증 프로필 교체를 수행한다.
 *
 * <p>도메인은 {@link NormalizedSpec}에만 의존하며, 원본 계약은 이 서비스 실행 전에
 * 버전별 파서가 정규화한다.
 */
@Service
public class SpecRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(SpecRegistrationService.class);

    private final SpecRegistrationParserResolver parserResolver;
    private final ApiSpecRepository specRepository;
    private final ApiSpecDocumentRepository documentRepository;
    private final ApiEndpointRepository endpointRepository;
    private final AuthProfileRepository authProfileRepository;

    public SpecRegistrationService(SpecRegistrationParserResolver parserResolver,
                                   ApiSpecRepository specRepository,
                                   ApiSpecDocumentRepository documentRepository,
                                   ApiEndpointRepository endpointRepository,
                                   AuthProfileRepository authProfileRepository) {
        this.parserResolver = parserResolver;
        this.specRepository = specRepository;
        this.documentRepository = documentRepository;
        this.endpointRepository = endpointRepository;
        this.authProfileRepository = authProfileRepository;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        SpecRegistrationParser parser = parserResolver.resolve(request.schemaVersion());
        NormalizedSpec normalized = parser.parse(request);

        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(normalized.baseUrl())
                .orElseGet(() -> new ApiSpec(normalized.baseUrl()));

        applyIdentityAndDiagnostics(spec, normalized);
        mergeServiceMeta(spec, normalized);

        // 등록/heartbeat는 스펙을 살아있게 유지한다. INACTIVE는 유지(관리자만 재활성화),
        // 그 외에는 ACTIVE로 복귀한다.
        if (spec.getStatus() != SpecStatus.INACTIVE) {
            spec.setStatus(SpecStatus.ACTIVE);
        }
        spec.setLastHeartbeatAt(LocalDateTime.now());

        ApiSpec saved = specRepository.save(spec);

        upsertDocument(saved.getId(), normalized.specJson());
        upsertEndpoints(saved.getId(), normalized.endpoints());
        replaceAuthProfiles(saved.getId(), normalized.authProfiles());

        log.info("Spec registered: baseUrl={}, specId={}, endpoints={}",
                saved.getBaseUrl(), saved.getId(), normalized.endpoints().size());

        return new RegisterResponse(saved.getId(), saved.getStatus().name());
    }

    /** 식별/진단 필드(name, specHash, jira, client, schemaVersion) 반영 */
    private void applyIdentityAndDiagnostics(ApiSpec spec, NormalizedSpec normalized) {
        spec.setName(normalized.name());
        spec.setSpecHash(normalized.specHash());
        spec.setJiraProjectKey(normalized.jiraProjectKey());
        spec.setClientLang(normalized.clientLang());
        spec.setClientVersion(normalized.clientVersion());
        spec.setSchemaVersion(normalized.schemaVersion());
    }

    /**
     * 관리자 우선 병합. 최초 등록 시 yml 값으로 채우고 yml 메타 해시를 저장한다.
     * 재등록 시에는 관리자가 메타를 수정하지 않은 경우에만 덮어쓰며, 관리자 수정 상태에서
     * yml 메타가 바뀌면 새 해시만 기록하고 관리자 값은 유지한다(변경 감지만).
     */
    private void mergeServiceMeta(ApiSpec spec, NormalizedSpec normalized) {
        NormalizedSpec.ServiceMeta meta = normalized.serviceMeta();
        String capabilitiesJson = writeCapabilities(meta.capabilities());
        String newYmlHash = HashUtil.sha256(String.join("\u0000",
                nullToEmpty(meta.description()),
                nullToEmpty(meta.domain()),
                nullToEmpty(capabilitiesJson),
                nullToEmpty(meta.notes())));

        boolean firstRegistration = spec.getId() == null;
        if (firstRegistration) {
            spec.setServiceDescription(meta.description());
            spec.setServiceDomain(meta.domain());
            spec.setServiceCapabilities(capabilitiesJson);
            spec.setServiceNotes(meta.notes());
            spec.setAdminEdited(false);
            spec.setYmlMetaHash(newYmlHash);
            return;
        }

        if (spec.isAdminEdited()) {
            // 관리자 수정은 덮어쓰지 않고, 변경 감지를 위해 yml drift만 기록한다.
            spec.setYmlMetaHash(newYmlHash);
            return;
        }

        spec.setServiceDescription(meta.description());
        spec.setServiceDomain(meta.domain());
        spec.setServiceCapabilities(capabilitiesJson);
        spec.setServiceNotes(meta.notes());
        spec.setYmlMetaHash(newYmlHash);
    }

    /** 원본 문서 upsert (스펙당 1개) */
    private void upsertDocument(Long specId, String specJson) {
        ApiSpecDocument document = documentRepository.findByApiSpecId(specId)
                .orElseGet(() -> new ApiSpecDocument(specId, specJson));
        document.setSpecJson(specJson);
        documentRepository.save(document);
    }

    /**
     * (specId, method, path) 키 기준 엔드포인트 upsert. 기존 행은 PK를 유지하고
     * (레시피 참조 보존), 새 스펙에서 사라진 엔드포인트는 삭제하지 않고 DEPRECATED로 표시한다.
     */
    private void upsertEndpoints(Long specId, List<NormalizedSpec.EndpointData> endpoints) {
        List<ApiEndpoint> existing = endpointRepository.findByApiSpecId(specId);
        Map<String, ApiEndpoint> existingByKey = new HashMap<>();
        for (ApiEndpoint e : existing) {
            existingByKey.put(key(e.getHttpMethod(), e.getPath()), e);
        }

        List<ApiEndpoint> toSave = new ArrayList<>();
        for (NormalizedSpec.EndpointData data : endpoints) {
            String k = key(data.httpMethod(), data.path());
            ApiEndpoint endpoint = existingByKey.remove(k);
            if (endpoint == null) {
                endpoint = new ApiEndpoint(specId, data.httpMethod(), data.path());
            }
            endpoint.setOperationJson(data.operationJson());
            endpoint.setSummary(data.summary());
            endpoint.setExcluded(data.excluded());
            endpoint.setConfirmRequired(data.confirmRequired());
            endpoint.setConfirmMessage(data.confirmMessage());
            endpoint.setStatus(EndpointStatus.ACTIVE);
            toSave.add(endpoint);
        }

        // 맵에 남은 항목은 새 스펙에서 사라진 것 → DEPRECATED로 표시.
        for (ApiEndpoint stale : existingByKey.values()) {
            if (stale.getStatus() != EndpointStatus.DEPRECATED) {
                stale.setStatus(EndpointStatus.DEPRECATED);
                toSave.add(stale);
            }
        }

        endpointRepository.saveAll(toSave);
    }

    /** 인증 프로필 전체 교체 (레시피가 PK 참조 안 하므로 단순 삭제 후 재삽입) */
    private void replaceAuthProfiles(Long specId, List<NormalizedSpec.AuthProfileData> profiles) {
        authProfileRepository.deleteByApiSpecId(specId);
        authProfileRepository.flush();
        List<AuthProfile> toSave = new ArrayList<>();
        for (NormalizedSpec.AuthProfileData p : profiles) {
            toSave.add(new AuthProfile(specId, p.name(), p.loginPageUrl()));
        }
        authProfileRepository.saveAll(toSave);
    }

    /** 기능 키워드를 JSON 배열 문자열로 직렬화 (매퍼 의존 없이 처리) */
    private String writeCapabilities(List<String> capabilities) {
        List<String> safe = capabilities != null ? capabilities : List.of();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < safe.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(safe.get(i))).append("\"");
        }
        return sb.append("]").toString();
    }

    /** JSON 문자열 이스케이프 (역슬래시/따옴표) */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 엔드포인트 upsert 비교용 키 (method + path) */
    private String key(String method, String path) {
        return method + " " + path;
    }

    /** null을 빈 문자열로 치환 (해시 계산 안정화용) */
    private String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
