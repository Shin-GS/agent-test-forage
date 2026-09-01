package com.testforge.repository.conversation;

import com.testforge.entity.conversation.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** ID로 미삭제 대화방 조회 (상세/수정/삭제 시 삭제 대화방 배제용) */
    Optional<Conversation> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 사용자별 미삭제 대화방 목록. lastMessageAt DESC.
     * 대화방은 첫 메시지와 함께 생성되므로 lastMessageAt은 항상 not null이다(빈 대화방 없음).
     */
    List<Conversation> findByUserIdAndDeletedAtIsNullOrderByLastMessageAtDesc(Long userId);
}
