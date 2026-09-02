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
     * 사용자별 미삭제 대화방 목록. lastMessageAt DESC. 대화방은 첫 메시지와 함께 생성되므로
     * lastMessageAt은 항상 not null이다(빈 대화방 없음).
     *
     * <p><b>상한 200건.</b> 대화방 목록은 사용자가 직접 만드는 개수라 보통 10~20개, 많아도 수십 개다.
     * 무한 스크롤 UX가 아니라 사이드바 전체 목록이므로 커서/페이징 대신 전체 반환하되, 비정상 폭증
     * (스크립트 대량 생성 등)에 대비해 최근 200건으로 상한만 둔다. 200을 넘기는 상황이 실제로 생기면
     * 그때 오래된 대화 정리 UX나 페이징을 도입한다(YAGNI).
     */
    List<Conversation> findTop200ByUserIdAndDeletedAtIsNullOrderByLastMessageAtDesc(Long userId);

    /**
     * 진행 중 상태로 남은 미삭제 대화방 조회. 서버 기동 시 인메모리 락이 사라진 상태에서
     * {@code AI_RESPONDING}/{@code EXECUTING}로 굳어버린 대화방을 IDLE로 정리하는 데 사용한다
     * (messaging.md 종결 보장 / 서버 기동 복구).
     */
    List<Conversation> findByStatusInAndDeletedAtIsNull(Collection<ConversationStatus> statuses);
}
