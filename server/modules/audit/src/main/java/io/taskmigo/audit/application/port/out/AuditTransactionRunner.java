package io.taskmigo.audit.application.port.out;

import java.util.function.Supplier;

/// Executes audit application transaction scopes without exposing Spring transactions inward.
public interface AuditTransactionRunner {

    @SuppressWarnings("NullableProblems")
    <T> T read(Supplier<T> work);

    @SuppressWarnings("NullableProblems")
    void write(Runnable work);
}
