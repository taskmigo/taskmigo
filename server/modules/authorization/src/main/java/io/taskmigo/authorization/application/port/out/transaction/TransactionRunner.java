package io.taskmigo.authorization.application.port.out.transaction;

import java.util.function.Supplier;

/// Executes application-owned transaction scopes without exposing framework transaction APIs to application services.
public interface TransactionRunner {

    /// Executes read-only application work in the current transaction or a new read-only transaction.
    ///
    /// @param work application work to execute
    /// @return the non-null result returned by the work
    <T> T read(Supplier<T> work);

    /// Executes mutating application work in the current transaction or a new transaction.
    ///
    /// @param work application work to execute
    /// @return the non-null result returned by the work
    <T> T write(Supplier<T> work);

    /// Executes mutating application work in the current transaction or a new transaction.
    ///
    /// @param work application work to execute
    void write(Runnable work);
}
