package com.testforge.repository.conversation;

import com.testforge.entity.conversation.MessagePart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessagePartRepository extends JpaRepository<MessagePart, Long> {

    /** 한 턴의 파트 목록 (ID 오름차순 = 생성순 = 표시순). 턴 렌더/스냅샷 구성용 */
    List<MessagePart> findByMessageIdOrderByIdAsc(Long messageId);

    /** 여러 턴의 파트를 한 번에 로드 (커서 페이지 렌더 시 N+1 방지). ID 오름차순 */
    List<MessagePart> findByMessageIdInOrderByIdAsc(List<Long> messageIds);
}
