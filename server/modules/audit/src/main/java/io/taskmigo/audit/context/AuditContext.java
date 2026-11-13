package io.taskmigo.audit.context;

import io.taskmigo.audit.model.AuditActor;
import java.util.Objects;
import java.util.UUID;

/// Immutable attribution attached to a trusted execution boundary and durable job metadata.
///
/// Actor: who initiated the operation. Executor: which component processes it.
/// Cause: which event, request or job triggered it. This is not an authorization grant.
public record AuditContext(AuditActor actor, AuditExecutor executor, AuditCause causation) {
    public AuditContext {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(causation);
    }

    /// Retains an authenticated User as actor even when the request later executes on Worker.
    public static AuditContext user(UUID userId, String username, AuditExecutor executor, AuditCause causation) {
        return new AuditContext(new AuditActor(userId, username), executor, causation);
    }

    /// Retains the responsible actor while updating the processing application and cause.
    public AuditContext forExecution(AuditExecutor nextExecutor, AuditCause nextCause) {
        return new AuditContext(this.actor, nextExecutor, nextCause);
    }

    /// Declares a trusted logical System actor, independent of any persisted User.
    public static AuditContext system(String operation, AuditExecutor executor, AuditCause causation) {
        return new AuditContext(AuditActor.system(operation), executor, causation);
    }
}
