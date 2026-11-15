package io.taskmigo.audit.context;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/// Binds immutable audit provenance within a lexical execution scope.
///
/// For durable jobs the immutable context must be carried through trusted job metadata.
public final class AuditContexts {

    private static final ScopedValue<AuditContext> CURRENT = ScopedValue.newInstance();

    private AuditContexts() {}

    /// Requires an explicitly declared responsible actor; never invents a SYSTEM fallback.
    public static AuditContext require() {
        return CURRENT.orElseThrow(() ->
            new IllegalStateException("Audited operation requires an explicit AuditContext")
        );
    }

    public static boolean isBound() {
        return CURRENT.isBound();
    }

    /// Executes value-returning work under an immutable, lexically scoped actor binding.
    public static <T> T call(AuditContext context, Supplier<T> action) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(action);
        return ScopedValue.where(CURRENT, context).call(action::get);
    }

    /// Captures a producer actor for a Callable submitted to an unstructured executor.
    public static <T> Callable<T> captureCallable(Callable<T> task) {
        Objects.requireNonNull(task);
        AuditContext captured = require();
        return () -> callChecked(captured, task);
    }

    /// Captures the responsible actor while declaring the receiving executor and job cause.
    public static <T> Callable<T> captureCallable(AuditExecutor executor, AuditCause causation, Callable<T> task) {
        Objects.requireNonNull(task);
        AuditContext captured = require().forExecution(executor, causation);
        return () -> callChecked(captured, task);
    }

    /// Captures a producer actor for a Runnable submitted to an unstructured executor.
    public static Runnable captureRunnable(Runnable task) {
        Objects.requireNonNull(task);
        AuditContext captured = require();
        return () -> run(captured, task);
    }

    /// Captures a Runnable while declaring the receiving executor and job cause.
    public static Runnable captureRunnable(AuditExecutor executor, AuditCause causation, Runnable task) {
        Objects.requireNonNull(task);
        AuditContext captured = require().forExecution(executor, causation);
        return () -> run(captured, task);
    }

    /// Executes checked work under an immutable, lexically scoped actor binding.
    public static <T> T callChecked(AuditContext context, Callable<T> task) throws Exception {
        Objects.requireNonNull(context);
        Objects.requireNonNull(task);
        return ScopedValue.where(CURRENT, context).call(task::call);
    }

    /// Executes void work under an immutable, lexically scoped actor binding.
    public static void run(AuditContext context, Runnable action) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(action);
        ScopedValue.where(CURRENT, context).run(action);
    }
}
