package com.testforge.controller.execution;

import com.testforge.dto.common.CursorPage;
import com.testforge.dto.execution.ActionPickerRespondRequest;
import com.testforge.dto.execution.ExecutionResponse;
import com.testforge.dto.execution.ExecutionStartRequest;
import com.testforge.dto.execution.ExecutionStepView;
import com.testforge.dto.execution.ExecutionSummaryView;
import com.testforge.dto.execution.PlanStartRequest;
import com.testforge.dto.execution.StepReportRequest;
import com.testforge.entity.execution.enums.ExecutionStatus;
import com.testforge.security.CurrentUser;
import com.testforge.service.execution.ExecutionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 레시피 실행 시작/종료/조회 API. 실제 스텝 실행은 FE 브라우저가 수행하고, 이 API는 실행의
 * 서버측 기록(스냅샷/상태/히스토리)과 대화방 상태(executing↔idle) 전이를 담당한다.
 *
 * <p>히스토리 목록은 커서 기반 무한 스크롤로 제공한다(부하 방지). 이어서 실행/플랜 실행은 다음 조각.
 *
 * <p>인증: 모든 엔드포인트는 세션 인증 필수(SecurityConfig). userId는 세션에서 도출한다(auth.md).
 */
@RestController
@RequestMapping("/api/v1")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * 실행 시작 (단일 레시피). 대화방을 executing으로 전이하고 스냅샷/레코드를 생성한다.
     * 이미 처리 중이면 409 CONVERSATION_BUSY, 레시피/대화방 없으면 404.
     */
    @PostMapping("/conversations/{conversationId}/executions")
    public ResponseEntity<ExecutionResponse> start(@PathVariable Long conversationId,
                                                   @RequestBody ExecutionStartRequest request) {
        // userId는 세션에서 도출 (클라이언트 값 무시)
        Long requesterId = CurrentUser.id();
        ExecutionStartRequest secured = new ExecutionStartRequest(
                requesterId, request.recipeId(), request.mode(),
                request.messageId(), request.initialContext());
        ExecutionResponse response = executionService.start(conversationId, requesterId, secured);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 플랜 실행 시작 (레시피 여러 개 순차 실행). plan 카드의 [실행]이 트리거한다. recipeIds 순서가
     * 실행 순서이며, 첫 레시피만 RUNNING으로 시작하고 각 레시피 완료 시 자동으로 다음 레시피로 전이한다.
     * recipeIds가 1개면 단일 실행과 동일하게 수렴한다(TYPE 표시만 SINGLE).
     * 이미 처리 중이면 409, 레시피/대화방 없으면 404, recipeIds가 비면 400.
     *
     * <p>실행 완료 보고(complete)용 외부 엔드포인트는 없다. 마지막 레시피의 마지막 스텝을
     * reportStep(SUCCESS/SKIPPED)으로 보고하면 서버가 자동으로 실행을 완료한다(완료 진입점 단일화).
     */
    @PostMapping("/conversations/{conversationId}/plan-executions")
    public ResponseEntity<ExecutionResponse> startPlan(@PathVariable Long conversationId,
                                                       @RequestBody PlanStartRequest request) {
        // userId는 세션에서 도출 (클라이언트 값 무시)
        Long requesterId = CurrentUser.id();
        PlanStartRequest secured = new PlanStartRequest(
                requesterId, request.recipeIds(), request.mode(),
                request.messageId(), request.initialContext(), request.recipeInputs());
        ExecutionResponse response = executionService.startPlan(conversationId, requesterId, secured);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 실행 히스토리 목록 (커서 기반 무한 스크롤). userId는 세션에서 도출(본인 것만).
     * 필터는 모두 옵션이며 "빈 선택 = 전체"다(execution.md 히스토리 조회 API 계약):
     * <ul>
     *   <li>{@code status}: 상태 다중 필터(SUCCESS/FAILED/STOPPED/CANCELLED 등). 빈=전체.</li>
     *   <li>{@code apiSpecId}: 서비스 다중 필터(실행 참조 API_SPEC_ID). 빈=전체, 지정 시 NULL 실행 제외.</li>
     *   <li>{@code keyword}: 레시피명(title) 부분일치.</li>
     *   <li>{@code from}/{@code to}: 날짜 범위(YYYY-MM-DD). 서버가 from 00:00:00 ~ to 23:59:59.999(당일 포함)로 확장.</li>
     * </ul>
     * cursor는 이전 응답의 nextCursor를 그대로 전달(없으면 첫 페이지). size 기본 20, 최대 50.
     * 응답은 경량 요약 목록 + nextCursor/hasNext.
     */
    @GetMapping("/executions")
    public CursorPage<ExecutionSummaryView> history(@RequestParam(required = false) List<ExecutionStatus> status,
                                                    @RequestParam(required = false) List<Long> apiSpecId,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                    @RequestParam(required = false) String cursor,
                                                    @RequestParam(required = false) Integer size) {
        // 본인 히스토리만 조회 (userId는 세션에서 도출)
        return executionService.history(CurrentUser.id(), status, apiSpecId, keyword, from, to, cursor, size);
    }

    /** 실행 상세 조회 (진행 상태 블록/히스토리 상세용). 없으면 404. */
    @GetMapping("/executions/{executionId}")
    public ExecutionResponse detail(@PathVariable Long executionId) {
        return executionService.detail(executionId, CurrentUser.id());
    }

    /**
     * 특정 대화방의 실행 목록 (패널에서 현재 대화방 실행 보기, 커서 기반). 대화방 없거나 삭제 시 404.
     * cursor/size 규칙은 히스토리 목록과 동일.
     */
    @GetMapping("/conversations/{conversationId}/executions")
    public CursorPage<ExecutionSummaryView> historyByConversation(
            @PathVariable Long conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return executionService.historyByConversation(conversationId, CurrentUser.id(), cursor, size);
    }

    /**
     * 스텝 결과 보고. FE가 한 스텝을 실행한 뒤 결과를 보고하면 EXECUTION_STEP을 갱신하고 context를
     * 누적하며 진행 블록(PROGRESS 메시지)을 message_update로 갱신한다. 스텝이 실행에 속하지 않거나
     * 종료된 실행이면 400, 실행/스텝 없으면 404.
     */
    @PostMapping("/executions/{executionId}/steps/{stepId}")
    public ExecutionStepView reportStep(@PathVariable Long executionId,
                                        @PathVariable Long stepId,
                                        @RequestBody StepReportRequest request) {
        return executionService.reportStep(executionId, stepId, CurrentUser.id(), request);
    }

    /**
     * 액션 피커 입력 응답 (action-picker.md). 사용자가 액션 피커에서 값을 제출하면 실행 context의
     * userInput에 병합한 뒤 대화방을 WAITING_INPUT → EXECUTING으로 전환하고 실행을 재개한다.
     * 실행 없으면 404, 입력 대기 상태가 아니거나 필수값이 여전히 비면 400(WAITING_INPUT 유지).
     */
    @PostMapping("/action-picker/respond")
    public ExecutionResponse respondActionPicker(@RequestBody ActionPickerRespondRequest request) {
        return executionService.respondActionPicker(CurrentUser.id(), request);
    }
}
