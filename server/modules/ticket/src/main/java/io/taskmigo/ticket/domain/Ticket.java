package io.taskmigo.ticket.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/// Represents the stable Ticket aggregate independently of versioned custom-field storage.
public record Ticket(
    UUID id,
    UUID ticketTypeId,
    UUID schemaInstanceId,
    String title,
    String status,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public Ticket {
        Objects.requireNonNull(id);
        Objects.requireNonNull(ticketTypeId);
        Objects.requireNonNull(schemaInstanceId);
        title = requireNonBlank(title, "Ticket title");
        status = requireNonBlank(status, "Ticket status");
        Objects.requireNonNull(createdBy);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (version < 0) {
            throw new IllegalArgumentException("Ticket version must not be negative");
        }
    }

    /// Creates a Ticket with immutable creator provenance and an initial optimistic-lock version.
    public static Ticket create(
        UUID id,
        UUID ticketTypeId,
        UUID schemaInstanceId,
        String title,
        String status,
        UUID createdBy,
        Instant now
    ) {
        return new Ticket(id, ticketTypeId, schemaInstanceId, title, status, createdBy, now, now, 0);
    }

    /// Applies common-field changes while preserving creator and historical schema-instance identity.
    public Ticket updateCommonFields(String title, String status, Instant now) {
        return new Ticket(
            this.id,
            this.ticketTypeId,
            this.schemaInstanceId,
            title,
            status,
            this.createdBy,
            this.createdAt,
            now,
            this.version
        );
    }

    private static String requireNonBlank(String value, String label) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
