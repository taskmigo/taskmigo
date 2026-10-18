package io.taskmigo.eav.schema.application;

import java.util.function.Supplier;

/// Runs EAV application work inside caller-independent read or write transaction boundaries.
public interface EavTransactionRunner {
    @SuppressWarnings("NullableProblems")
    <T> T read(Supplier<T> work);

    @SuppressWarnings("NullableProblems")
    <T> T write(Supplier<T> work);

    @SuppressWarnings("NullableProblems")
    void write(Runnable work);
}
