package io.taskmigo.audit.context;

import java.util.Objects;

/// Identifies the trusted origin of an operation across synchronous and asynchronous execution.
public record AuditCause(Kind kind, String reference) {
    public enum Kind {
        HTTP_REQUEST,
        ASYNC_JOB,
        SCHEDULED_JOB,
        INSTALLATION,
        AUTOMATION,
        TEST,
    }

    public AuditCause {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(reference);
        if (reference.isBlank() || reference.length() > 200) {
            throw new IllegalArgumentException("Audit causation reference must be 1 to 200 characters");
        }
    }

    public static AuditCause request(String requestId) {
        return new AuditCause(Kind.HTTP_REQUEST, requestId);
    }

    public static AuditCause job(String jobId) {
        return new AuditCause(Kind.ASYNC_JOB, jobId);
    }

    public static AuditCause scheduled(String jobId) {
        return new AuditCause(Kind.SCHEDULED_JOB, jobId);
    }

    public static AuditCause installation(String runId) {
        return new AuditCause(Kind.INSTALLATION, runId);
    }

    public static AuditCause test(String reference) {
        return new AuditCause(Kind.TEST, reference);
    }

    /// Provides the stable representation persisted in the audit log.
    public String persistedValue() {
        return this.kind.name() + ":" + this.reference;
    }
}
