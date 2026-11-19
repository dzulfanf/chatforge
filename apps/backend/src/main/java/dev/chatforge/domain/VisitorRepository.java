package dev.chatforge.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitorRepository extends JpaRepository<Visitor, UUID> {
}
