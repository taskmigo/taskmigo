package io.taskmigo.ticket.type.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/// Represents a stable Ticket Type identity that owns one generic schema definition.
public record TicketType(
    UUID id,
    String key,
    String name,
    TicketTypeStatus status,
    UUID schemaDefinitionId,
    Instant createdAt,
    Instant updatedAt
) {
    public TicketType {
        Objects.requireNonNull(id);
        key = requireNonBlank(key, "Ticket Type key");
        name = requireNonBlank(name, "Ticket Type name");
        Objects.requireNonNull(status);
        Objects.requireNonNull(schemaDefinitionId);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    /// Creates a Ticket Type that is immediately eligible for new Ticket creation.
    public static TicketType active(UUID id, String key, String name, UUID schemaDefinitionId, Instant now) {
        return new TicketType(id, key, name, TicketTypeStatus.ACTIVE, schemaDefinitionId, now, now);
    }

    /// Returns a metadata-only revision while preserving stable key and schema ownership.
    public TicketType update(String name, TicketTypeStatus status, Instant now) {
        return new TicketType(this.id, this.key, name, status, this.schemaDefinitionId, this.createdAt, now);
    }

    /// Fails when this type is not eligible for creating new Tickets.
    public void requireCreationEligible() {
        if (this.status != TicketTypeStatus.ACTIVE) {
            throw new IllegalStateException("Retired Ticket Types cannot create new Tickets");
        }
    }

    private static String requireNonBlank(String value, String label) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
