package io.taskmigo.audit;

/// Publishes an immutable audit event inside the caller's current business transaction.
@FunctionalInterface
public interface AuditPublisher {
    void publish(AuditEvent event);
}
