package io.taskmigo.ticket.persistence;

import io.taskmigo.foundation.DomainFailureType;
import io.taskmigo.ticket.field.application.TicketFieldRegistry;
import io.taskmigo.ticket.field.application.api.TicketFieldException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;

@Repository
class JpaTicketFieldRegistryAdapter implements TicketFieldRegistry {

    private final JpaTicketFieldIdentityRepository repository;
    private final EntityManager entityManager;

    JpaTicketFieldRegistryAdapter(JpaTicketFieldIdentityRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Entry> findByKey(String key) {
        return this.repository.findByKey(key).map(TicketFieldIdentityEntity::toEntry);
    }

    @Override
    public Optional<Entry> findById(UUID id) {
        return this.repository.findById(id).map(TicketFieldIdentityEntity::toEntry);
    }

    @Override
    public List<Entry> findAll() {
        return this.repository.findAll().stream().map(TicketFieldIdentityEntity::toEntry).toList();
    }

    @Override
    public Optional<Entry> lockById(UUID id) {
        return this.repository.findFirstById(id).map(TicketFieldIdentityEntity::toEntry);
    }

    @Override
    public void insert(Entry entry) {
        try {
            this.entityManager.persist(new TicketFieldIdentityEntity(entry));
            this.entityManager.flush();
        } catch (ConstraintViolationException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw new TicketFieldException(
                    DomainFailureType.CONFLICT,
                    "Ticket field key already exists: " + entry.key(),
                    exception
                );
            }
            throw exception;
        }
    }

    @Override
    public void update(Entry entry) {
        TicketFieldIdentityEntity entity = this.repository
            .findById(entry.id())
            .orElseThrow(() -> new IllegalArgumentException("Unknown Ticket field: " + entry.id()));
        entity.update(entry);
    }
}
