package com.testforge.repository.conversation;

import com.testforge.entity.conversation.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 대화방 턴 목록 (ID 오름차순 = 시간순). 내부 처리용(AI 컨텍스트 조립 등)이며 UI 조회는 커서 페이징을 쓴다 */
    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);

    /**
     * 대화방 턴의 커서 페이지 (채팅 무한 스크롤: 최신부터 위로 과거 로딩). 정렬은 {@code ID DESC}.
     * ID는 대화방 내 auto-increment·유일이라 커서로 안정적이다(시각 정밀도 문제 없음). {@code cursorId}가
     * null이면 첫 페이지(최신), 있으면 그보다 과거({@code id < cursorId})를 이어 조회한다. size 제한은
     * Pageable로 전달한다(hasNext 판정을 위해 서비스에서 size+1 요청).
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
              AND (:cursorId IS NULL OR m.id < :cursorId)
            ORDER BY m.id DESC
            """)
    List<Message> findByConversationIdByCursor(@Param("conversationId") Long conversationId,
                                               @Param("cursorId") Long cursorId,
                                               org.springframework.data.domain.Pageable pageable);
}
