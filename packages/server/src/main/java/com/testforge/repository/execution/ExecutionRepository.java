package com.testforge.repository.execution;

import com.testforge.entity.execution.Execution;
import com.testforge.entity.execution.enums.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    /** 사용자 실행 히스토리 (최신순). 대화 무관 — 히스토리 독립(history.md) */
    List<Execution> findByUserIdOrderByStartedAtDesc(Long userId);

    /** 특정 대화방의 실행 목록 (대화방 내 실행 표시용) */
    List<Execution> findByConversationId(Long conversationId);

    /** 특정 대화방에서 지정 상태인 실행 목록 (중지/취소 시 RUNNING 실행 종료 대상 조회) */
    List<Execution> findByConversationIdAndStatus(Long conversationId, ExecutionStatus status);
}
