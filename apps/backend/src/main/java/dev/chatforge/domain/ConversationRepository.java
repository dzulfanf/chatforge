package dev.chatforge.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByVisitorId(UUID visitorId);

    Optional<Conversation> findFirstByVisitorIdAndStatus(UUID visitorId, ConversationStatus status);

    Page<Conversation> findByStatusOrderByUpdatedAtDesc(ConversationStatus status, Pageable pageable);
}
