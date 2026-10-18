package io.taskmigo.ticket.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaTicketTypeRepository extends JpaRepository<TicketTypeEntity, UUID> {
    boolean existsByKey(String key);
}
