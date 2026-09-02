package com.testforge.repository.conversation;

import com.testforge.entity.conversation.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 대화방 메시지 목록 (SEQ 오름차순). 내부 처리용(AI 컨텍스트 조립 등)이며 UI 조회는 커서 페이징을 쓴다 */
    List<Message> findByConversationIdOrderBySeqAsc(Long conversationId);

    /**
     * 대화방 메시지의 커서 페이지 (채팅 무한 스크롤: 최신부터 위로 과거 로딩). 정렬은 {@code SEQ DESC}.
     * seq는 대화방 내 단조증가·유일이라 커서로 안정적이다(정밀도 문제 없음). {@code cursorSeq}가 null이면
     * 첫 페이지(최신), 있으면 그보다 과거({@code seq < cursorSeq})를 이어 조회한다. size 제한은 Pageable로
     * 전달한다(hasNext 판정을 위해 서비스에서 size+1 요청).
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
              AND (:cursorSeq IS NULL OR m.seq < :cursorSeq)
            ORDER BY m.seq DESC
            """)
    List<Message> findByConversationIdBySeqCursor(@Param("conversationId") Long conversationId,
                                                  @Param("cursorSeq") Long cursorSeq,
                                                  org.springframework.data.domain.Pageable pageable);

    /**
     * 해당 대화방의 현재 최대 SEQ. 메시지가 없으면 null.
     * 다음 SEQ 발번(max+1)에 사용한다.
     */
    @Query("SELECT MAX(m.seq) FROM Message m WHERE m.conversationId = :conversationId")
    Long findMaxSeq(@Param("conversationId") Long conversationId);
}
