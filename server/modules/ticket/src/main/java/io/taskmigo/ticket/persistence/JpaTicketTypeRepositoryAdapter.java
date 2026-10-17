package io.taskmigo.ticket.persistence;

import io.taskmigo.ticket.type.application.TicketTypeRepository;
import io.taskmigo.ticket.type.domain.TicketType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaTicketTypeRepositoryAdapter implements TicketTypeRepository {

    private final JpaTicketTypeRepository repository;

    JpaTicketTypeRepositoryAdapter(JpaTicketTypeRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(TicketType ticketType) {
        Optional<TicketTypeEntity> existing = this.repository.findById(ticketType.id());
        if (existing.isEmpty()) {
            this.repository.save(new TicketTypeEntity(ticketType));
            return;
        }
        TicketTypeEntity entity = existing.orElseThrow();
        entity.update(ticketType);
        this.repository.save(entity);
    }

    @Override
    public Optional<TicketType> findById(UUID id) {
        return this.repository.findById(id).map(TicketTypeEntity::toDomain);
    }

    @Override
    public boolean existsByKey(String key) {
        return this.repository.existsByKey(key);
    }

    @Override
    public List<TicketType> findAll() {
        return this.repository.findAll().stream().map(TicketTypeEntity::toDomain).toList();
    }
}
