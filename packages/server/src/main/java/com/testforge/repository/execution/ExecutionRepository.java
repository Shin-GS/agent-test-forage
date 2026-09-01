package com.testforge.repository.execution;

import com.testforge.entity.execution.Execution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    /** 사용자 실행 히스토리 (최신순). 대화 무관 — 히스토리 독립(history.md) */
    List<Execution> findByUserIdOrderByStartedAtDesc(Long userId);

    /** 특정 대화방의 실행 목록 (대화방 내 실행 표시용) */
    List<Execution> findByConversationId(Long conversationId);
}
