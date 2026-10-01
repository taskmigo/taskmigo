package io.taskmigo.audit.application.port.out;

import java.util.function.Supplier;

/// Executes audit query transactions and joins caller-owned mutation transactions for synchronous appends.
public interface AuditTransactionRunner {

    <T> T read(Supplier<T> work);

    /// Executes write work only when the caller already owns an active transaction.
    ///
    /// @param work audit persistence work
    void write(Runnable work);
}
