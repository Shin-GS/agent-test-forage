package com.testforge.repository.conversation;

import com.testforge.entity.conversation.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 대화방 메시지 목록 (SEQ 오름차순) */
    List<Message> findByConversationIdOrderBySeqAsc(Long conversationId);

    /**
     * 해당 대화방의 현재 최대 SEQ. 메시지가 없으면 null.
     * 다음 SEQ 발번(max+1)에 사용한다.
     */
    @Query("SELECT MAX(m.seq) FROM Message m WHERE m.conversationId = :conversationId")
    Long findMaxSeq(@Param("conversationId") Long conversationId);
}
