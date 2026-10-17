package io.taskmigo.ticket.application;

import java.util.function.Supplier;

/// Runs Ticket application work inside the transaction semantics supplied by the outer runtime.
public interface TicketTransactionRunner {
    <T> T read(Supplier<T> work);

    <T> T write(Supplier<T> work);

    void write(Runnable work);
}
