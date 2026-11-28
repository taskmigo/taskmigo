package io.taskmigo.ticket.field.application;

import io.taskmigo.foundation.DomainFailureType;
import io.taskmigo.ticket.field.application.api.TicketFieldException;
import io.taskmigo.ticket.field.application.api.TicketFieldStatus;
import io.taskmigo.ticket.field.application.api.TicketFieldType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/// Stores Ticket-wide field definitions independently of template versions.
public interface TicketFieldRegistry {
    @SuppressWarnings("NullableProblems")
    Optional<Entry> findByKey(String key);

    @SuppressWarnings("NullableProblems")
    Optional<Entry> findById(UUID id);

    List<Entry> findAll();

    /// Locks an identity for template attachment or lifecycle mutation until transaction completion.
    @SuppressWarnings("NullableProblems")
    Optional<Entry> lockById(UUID id);

    /// Inserts a new identity; duplicate IDs and keys must be rejected.
    @SuppressWarnings("NullableProblems")
    void insert(Entry entry);

    @SuppressWarnings("NullableProblems")
    void update(Entry entry);

    /// The canonical key and data type shared by every template using this field.
    record Entry(UUID id, String key, String label, TicketFieldType dataType, TicketFieldStatus status) {
        private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

        private static final Set<String> RESERVED_KEYS = Set.of(
            "id",
            "ticketTypeId",
            "templateVersionId",
            "schemaInstanceId",
            "title",
            "status",
            "createdBy",
            "createdAt",
            "updatedAt",
            "version",
            "customFields",
            "const",
            "if",
            "else",
            "return",
            "in",
            "true",
            "false",
            "null",
            "all",
            "any",
            "none",
            "len",
            "contains"
        );

        public Entry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(key);
            Objects.requireNonNull(label);
            Objects.requireNonNull(dataType);
            Objects.requireNonNull(status);
            if (key.length() > 200 || !KEY_PATTERN.matcher(key).matches() || RESERVED_KEYS.contains(key)) {
                throw new TicketFieldException(
                    DomainFailureType.INVALID_INPUT,
                    "Invalid or reserved Ticket field key: " + key
                );
            }
            if (label.isBlank() || label.length() > 200) {
                throw new TicketFieldException(
                    DomainFailureType.INVALID_INPUT,
                    "Ticket field label must contain 1 to 200 characters"
                );
            }
        }
    }
}
