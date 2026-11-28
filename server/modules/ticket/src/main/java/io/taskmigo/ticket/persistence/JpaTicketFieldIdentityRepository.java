package io.taskmigo.ticket.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface JpaTicketFieldIdentityRepository extends JpaRepository<TicketFieldIdentityEntity, UUID> {
    Optional<TicketFieldIdentityEntity> findByKey(String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TicketFieldIdentityEntity> findFirstById(UUID id);
}
