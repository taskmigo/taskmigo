package io.taskmigo.audit.application.port.out;

import java.util.function.Supplier;

/// Executes audit application work inside read or write transactions.
public interface AuditTransactionRunner {
    <T> T read(Supplier<T> work);

    void write(Runnable work);
}
