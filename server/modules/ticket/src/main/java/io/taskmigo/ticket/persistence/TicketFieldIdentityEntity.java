package io.taskmigo.ticket.persistence;

import io.taskmigo.ticket.field.application.TicketFieldRegistry;
import io.taskmigo.ticket.field.application.api.TicketFieldStatus;
import io.taskmigo.ticket.field.application.api.TicketFieldType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "ticket_field_registry")
@SuppressWarnings("NotNullFieldNotInitialized")
class TicketFieldIdentityEntity {

    @Id
    private UUID id;

    @Column(name = "field_key", nullable = false, unique = true, updatable = false, length = 200)
    private String key;

    @Column(nullable = false, length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, updatable = false, length = 16)
    private TicketFieldType dataType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketFieldStatus status;

    protected TicketFieldIdentityEntity() {}

    TicketFieldIdentityEntity(TicketFieldRegistry.Entry entry) {
        this.id = entry.id();
        this.key = entry.key();
        this.label = entry.label();
        this.dataType = entry.dataType();
        this.status = entry.status();
    }

    void update(TicketFieldRegistry.Entry entry) {
        if (!this.id.equals(entry.id()) || !this.key.equals(entry.key()) || this.dataType != entry.dataType()) {
            throw new IllegalArgumentException("Ticket field identity and data type cannot change");
        }
        this.label = entry.label();
        this.status = entry.status();
    }

    TicketFieldRegistry.Entry toEntry() {
        return new TicketFieldRegistry.Entry(this.id, this.key, this.label, this.dataType, this.status);
    }
}
