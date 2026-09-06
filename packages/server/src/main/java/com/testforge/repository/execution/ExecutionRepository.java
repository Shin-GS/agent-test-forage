package com.testforge.repository.execution;

import com.testforge.entity.execution.Execution;
import com.testforge.entity.execution.enums.ExecutionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    /** 특정 대화방에서 지정 상태인 실행 목록 (중지/취소 시 RUNNING 실행 종료 대상 조회) */
    List<Execution> findByConversationIdAndStatus(Long conversationId, ExecutionStatus status);

    /**
     * 사용자 실행 히스토리의 커서 페이지 (최신순 무한 스크롤). 정렬/커서는 <b>{@code ID DESC}</b> 단독이다.
     * ID는 auto-increment PK라 생성순(= 최신순)이며 유일하므로, 시각 정밀도(마이크로초) 손실로 인한
     * 커서 경계 누락/중복 위험이 없다(startedAt+epochMilli 커서의 함정 회피). 상태/서비스/키워드/기간은 옵션 필터.
     * 커서(id)가 null이면 첫 페이지, 있으면 {@code id < cursorId}로 이어 조회한다. size 제한은
     * {@link Pageable}로 전달한다(hasNext 판정을 위해 서비스에서 size+1 요청).
     *
     * <p><b>다중 필터(IN)</b>: {@code statuses}/{@code apiSpecIds}는 "빈 선택 = 전체"라, 서비스에서 빈/미전달을
     * {@code null}로 넘겨 해당 조건을 무시한다(리스트가 null이면 필터 미적용). apiSpecId 필터가 <b>지정된</b>
     * 경우에만 {@code API_SPEC_ID IN}으로 거르며, 이때 {@code API_SPEC_ID}가 NULL인 실행(플랜 등)은 제외된다.
     * 미지정(전체)이면 NULL 실행도 포함된다(execution.md apiSpecId NULL 규칙).
     *
     * <p><b>기간</b>: {@code fromAt}/{@code toAt}는 서비스에서 날짜 경계를 확장(from 00:00:00, to 23:59:59.999)해
     * 넘긴 {@code STARTED_AT} 범위 경계다. from만/to만도 허용(각각 null이면 그 방향 무제한).
     *
     * <p>키워드는 {@code title} 부분일치(대소문자 무시). LIKE 와일드카드({@code % _ \})는 서비스에서
     * 이스케이프해 전달하며, 여기서 {@code ESCAPE '\'}로 리터럴 매칭한다.
     */
    @Query("""
            SELECT e FROM Execution e
            WHERE e.userId = :userId
              AND (:statuses IS NULL OR e.status IN :statuses)
              AND (:apiSpecIds IS NULL OR (e.apiSpecId IS NOT NULL AND e.apiSpecId IN :apiSpecIds))
              AND (:keyword IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')
              AND (:fromAt IS NULL OR e.startedAt >= :fromAt)
              AND (:toAt IS NULL OR e.startedAt <= :toAt)
              AND (:cursorId IS NULL OR e.id < :cursorId)
            ORDER BY e.id DESC
            """)
    List<Execution> findHistoryByCursor(@Param("userId") Long userId,
                                        @Param("statuses") List<ExecutionStatus> statuses,
                                        @Param("apiSpecIds") List<Long> apiSpecIds,
                                        @Param("keyword") String keyword,
                                        @Param("fromAt") LocalDateTime fromAt,
                                        @Param("toAt") LocalDateTime toAt,
                                        @Param("cursorId") Long cursorId,
                                        Pageable pageable);

    /**
     * 특정 대화방의 실행 목록 커서 페이지 (패널에서 현재 대화방 실행 보기). 정렬/커서 규칙은
     * {@link #findHistoryByCursor}와 동일한 {@code ID DESC} 단독이다.
     */
    @Query("""
            SELECT e FROM Execution e
            WHERE e.conversationId = :conversationId
              AND (:cursorId IS NULL OR e.id < :cursorId)
            ORDER BY e.id DESC
            """)
    List<Execution> findByConversationIdByCursor(@Param("conversationId") Long conversationId,
                                                 @Param("cursorId") Long cursorId,
                                                 Pageable pageable);

    /**
     * 특정 대화방의 전체 실행 목록 (id 오름차순). 대화 진입/새로고침 시 실행 진행 블록 복원 전용
     * (db/execution.md 새로고침 복원). 커서 페이징(요약 목록)과 달리 그 대화의 실행을 <b>모두</b>
     * 촉발 메시지 순서대로 재구성해야 하므로 상태 무관(RUNNING/종료 포함) 전체를 오름차순으로 돌려준다.
     */
    List<Execution> findByConversationIdOrderByIdAsc(Long conversationId);
}
