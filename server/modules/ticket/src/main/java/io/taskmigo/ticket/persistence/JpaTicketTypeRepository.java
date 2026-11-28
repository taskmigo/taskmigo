package io.taskmigo.ticket.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface JpaTicketTypeRepository extends JpaRepository<TicketTypeEntity, UUID> {
    boolean existsByKey(String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TicketTypeEntity> findFirstById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<TicketTypeEntity> findOneById(UUID id);
}
