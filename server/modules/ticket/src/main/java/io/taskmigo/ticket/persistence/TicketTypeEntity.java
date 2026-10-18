package io.taskmigo.ticket.persistence;

import io.taskmigo.ticket.type.domain.TicketType;
import io.taskmigo.ticket.type.domain.TicketTypeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_types")
@SuppressWarnings("NotNullFieldNotInitialized")
class TicketTypeEntity {

    @Id
    private UUID id;

    @Column(name = "type_key", nullable = false, unique = true, length = 200)
    private String key;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketTypeStatus status;

    @Column(name = "schema_definition_id", nullable = false, unique = true)
    private UUID schemaDefinitionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TicketTypeEntity() {}

    TicketTypeEntity(TicketType type) {
        this.id = type.id();
        this.key = type.key();
        this.name = type.name();
        this.status = type.status();
        this.schemaDefinitionId = type.schemaDefinitionId();
        this.createdAt = type.createdAt();
        this.updatedAt = type.updatedAt();
    }

    void update(TicketType type) {
        if (
            !this.id.equals(type.id()) ||
            !this.key.equals(type.key()) ||
            !this.schemaDefinitionId.equals(type.schemaDefinitionId())
        ) {
            throw new IllegalArgumentException("Ticket Type stable identity cannot change");
        }
        this.name = type.name();
        this.status = type.status();
        this.updatedAt = type.updatedAt();
    }

    TicketType toDomain() {
        return new TicketType(
            this.id,
            this.key,
            this.name,
            this.status,
            this.schemaDefinitionId,
            this.createdAt,
            this.updatedAt
        );
    }
}
