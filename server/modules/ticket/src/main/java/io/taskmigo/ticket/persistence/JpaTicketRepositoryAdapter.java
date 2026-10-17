package io.taskmigo.ticket.persistence;

import io.taskmigo.ticket.application.TicketRepository;
import io.taskmigo.ticket.domain.Ticket;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaTicketRepositoryAdapter implements TicketRepository {

    private final JpaTicketRepository repository;

    JpaTicketRepositoryAdapter(JpaTicketRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public void save(Ticket ticket) {
        Optional<TicketEntity> existing = this.repository.findById(ticket.id());
        if (existing.isEmpty()) {
            this.repository.save(new TicketEntity(ticket));
            return;
        }
        TicketEntity entity = existing.orElseThrow();
        entity.update(ticket);
        this.repository.save(entity);
    }

    @Override
    public Optional<Ticket> findById(UUID id) {
        return this.repository.findById(id).map(TicketEntity::toDomain);
    }
}
