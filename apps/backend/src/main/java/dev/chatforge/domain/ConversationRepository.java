package dev.chatforge.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByVisitorId(UUID visitorId);

    Page<Conversation> findByStatusOrderByUpdatedAtDesc(ConversationStatus status, Pageable pageable);
}
