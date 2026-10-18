package io.taskmigo.ticket.persistence;

import io.taskmigo.ticket.domain.Ticket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "tickets")
@SuppressWarnings("NotNullFieldNotInitialized")
class TicketEntity {

    @Id
    private UUID id;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "schema_instance_id", nullable = false, unique = true)
    private UUID schemaInstanceId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 100)
    private String status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Nullable
    @Column(nullable = false)
    private Long version;

    protected TicketEntity() {}

    TicketEntity(Ticket ticket) {
        this.id = ticket.id();
        this.ticketTypeId = ticket.ticketTypeId();
        this.schemaInstanceId = ticket.schemaInstanceId();
        this.title = ticket.title();
        this.status = ticket.status();
        this.createdBy = ticket.createdBy();
        this.createdAt = ticket.createdAt();
        this.updatedAt = ticket.updatedAt();
        this.version = null;
    }

    void update(Ticket ticket) {
        if (
            !this.id.equals(ticket.id()) ||
            !this.ticketTypeId.equals(ticket.ticketTypeId()) ||
            !this.schemaInstanceId.equals(ticket.schemaInstanceId()) ||
            !this.createdBy.equals(ticket.createdBy()) ||
            !this.createdAt.equals(ticket.createdAt())
        ) {
            throw new IllegalArgumentException("Ticket structural identity cannot change");
        }
        this.updateCommonFields(ticket.title(), ticket.status(), ticket.updatedAt());
    }

    void updateCommonFields(String title, String status, Instant updatedAt) {
        this.title = Objects.requireNonNull(title);
        this.status = Objects.requireNonNull(status);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    Ticket toDomain() {
        long persistedVersion = this.version == null ? 0 : this.version;
        return new Ticket(
            this.id,
            this.ticketTypeId,
            this.schemaInstanceId,
            this.title,
            this.status,
            this.createdBy,
            this.createdAt,
            this.updatedAt,
            persistedVersion
        );
    }
}
