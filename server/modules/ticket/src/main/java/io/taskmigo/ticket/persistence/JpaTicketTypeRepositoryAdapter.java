package io.taskmigo.ticket.persistence;

import io.taskmigo.foundation.DomainFailureType;
import io.taskmigo.ticket.type.application.TicketTypeRepository;
import io.taskmigo.ticket.type.application.api.TicketTypeException;
import io.taskmigo.ticket.type.domain.TicketType;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;

@Repository
class JpaTicketTypeRepositoryAdapter implements TicketTypeRepository {

    private final JpaTicketTypeRepository repository;

    private final EntityManager entityManager;

    JpaTicketTypeRepositoryAdapter(JpaTicketTypeRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public void save(TicketType ticketType) {
        Optional<TicketTypeEntity> existing = this.repository.findById(ticketType.id());
        if (existing.isEmpty()) {
            try {
                this.entityManager.persist(new TicketTypeEntity(ticketType));
                this.entityManager.flush();
            } catch (ConstraintViolationException exception) {
                if (
                    "23505".equals(exception.getSQLState()) &&
                    "uk_ticket_types_key".equals(exception.getConstraintName())
                ) {
                    throw new TicketTypeException(
                        DomainFailureType.CONFLICT,
                        "Ticket Type key already exists: " + ticketType.key(),
                        exception
                    );
                }
                throw exception;
            }
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
    public Optional<TicketType> lockById(UUID id) {
        return this.repository.findFirstById(id).map(TicketTypeEntity::toDomain);
    }

    @Override
    public Optional<TicketType> lockForCreation(UUID id) {
        return this.repository.findOneById(id).map(TicketTypeEntity::toDomain);
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
