package com.testforge.repository.conversation;

import com.testforge.entity.conversation.MessagePart;
import com.testforge.entity.conversation.enums.PartType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessagePartRepository extends JpaRepository<MessagePart, Long> {

    /** 한 턴의 파트 목록 (ID 오름차순 = 생성순 = 표시순). 턴 렌더/스냅샷 구성용 */
    List<MessagePart> findByMessageIdOrderByIdAsc(Long messageId);

    /** 여러 턴의 파트를 한 번에 로드 (커서 페이지 렌더 시 N+1 방지). ID 오름차순 */
    List<MessagePart> findByMessageIdInOrderByIdAsc(List<Long> messageIds);

    /**
     * 실행(executionId)의 특정 타입 파트 중 <b>가장 최근(id DESC)</b> 1개.
     * 진행(PROGRESS) 파트를 executionId로 역조회할 때 사용한다(TRIGGER_PART_ID에 의존하지 않음 —
     * TRIGGER_PART_ID는 촉발 카드 파트를 가리키는 값으로 유지, db/execution.md). 재개로 한 실행에
     * PROGRESS 파트가 여러 개면 최신 것을 갱신/기준으로 삼는다.
     */
    Optional<MessagePart> findTopByExecutionIdAndTypeOrderByIdDesc(Long executionId, PartType type);
}
