package com.testforge.repository.conversation;

import com.testforge.entity.conversation.Conversation;
import com.testforge.entity.conversation.enums.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
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

    /**
     * 진행 중 상태로 남은 미삭제 대화방 조회. 서버 기동 시 인메모리 락이 사라진 상태에서
     * {@code AI_RESPONDING}/{@code EXECUTING}로 굳어버린 대화방을 IDLE로 정리하는 데 사용한다
     * (messaging.md 종결 보장 / 서버 기동 복구).
     */
    List<Conversation> findByStatusInAndDeletedAtIsNull(Collection<ConversationStatus> statuses);
}
