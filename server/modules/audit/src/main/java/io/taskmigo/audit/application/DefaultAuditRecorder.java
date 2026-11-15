package io.taskmigo.audit.application;

import io.taskmigo.audit.application.append.AuditAppendService;
import io.taskmigo.audit.application.record.AuditRecorder;
import io.taskmigo.audit.context.AuditContexts;
import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.audit.model.AuditOperation;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/// Centralizes actor lookup and synchronous audit append for application mutations.
public final class DefaultAuditRecorder implements AuditRecorder {

    private final AuditAppendService append;
    private final Clock clock;

    public DefaultAuditRecorder(AuditAppendService append, Clock clock) {
        this.append = Objects.requireNonNull(append);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void record(
        String entityType,
        String entityId,
        AuditOperation operation,
        Supplier<List<AuditChange>> changes
    ) {
        Objects.requireNonNull(changes);
        if (entityId.isBlank() || entityId.length() > 255) {
            throw new IllegalArgumentException("Audit entity ID must be 1 to 255 characters");
        }
        var context = AuditContexts.require();
        this.append.append(
            new AuditEvent(
                UUID.randomUUID(),
                entityType,
                entityId,
                context.actor(),
                this.clock.instant(),
                changes.get(),
                operation,
                context.executor().application(),
                context.causation().persistedValue()
            )
        );
    }
}
