# Audit attribution — Developer guide

Taskmigo records **the actor responsible for an operation**, not automatically the application executing it. Use a single recorder for all auditable mutations, whether called from Web, Worker, Migration, or a future CLI.

## Core contract

- **Actor**: the authenticated User who initiated work, or a logical SYSTEM identity explicitly selected at a trusted boundary. System actors are stable UUID-derived logical principals, **not User rows**. They do not have credentials or authorization grants.
- **Executor**: typed `AuditExecutor` (`WEB`, `WORKER`, `MIGRATION`, `CLI`, `AUTOMATION`, `TEST`), with a named application.
- **Cause**: typed `AuditCause` (`HTTP_REQUEST`, `ASYNC_JOB`, `SCHEDULED_JOB`, `INSTALLATION`, `AUTOMATION`, `TEST`) and a reference identifier. Never put credentials or PII in this reference.
- **Operation**: explicit `AuditOperation.CREATE`, `UPDATE` or `DELETE`. Do not infer it from differences.

The mutation is responsible for whether it actually changed state; call `AuditRecorder.record` **once** for a changed aggregate and **never** for `UNCHANGED`. The recorder resolves actor provenance, evaluates a lazy diff, and synchronously persists within the mutation transaction. Missing context fails an enabled recorder, causing the transaction to roll back. An app with audit explicitly disabled has an injected no-op recorder.

### Migration or independent scheduled system operation

```java
AuditContexts.run(
  AuditContext.system("taskmigo-migration", AuditExecutor.migration("migration"), AuditCause.installation("startup")),
  () -> migrationInstaller.reconcile(plan)
);
```

Use a separate name per independent system _operation_ (for example `retention-purge`) rather than inferring a system actor from all Worker executions.

### User-initiated work

Create the context **after authentication** at the trusted entry point, using the authenticated User identity and the applicable executor/cause. Do not accept a client-supplied `SYSTEM` principal.

```java
AuditContext context = AuditContext.user(
  authenticatedUserId,
  authenticatedUsername,
  AuditExecutor.web("web"),
  AuditCause.request(requestId)
);

return AuditContexts.call(context, () -> userUseCase.execute(command));
```

The record itself does not grant permission. Run the relevant authorization check at execution time.

The Web User API builds this scope from authenticated JWT claims and the servlet request ID. Runtime User CREATE, direct grant updates, retention delete and tombstone use the same `AuditRecorder` as managed provisioning. The User application methods no longer accept or propagate a separate audit actor argument.

### Recording a mutation from a use case

```java
if (mutation.changed()) {
  auditRecorder.record("role", roleId, AuditOperation.UPDATE, () ->
    AuditChanges.builder()
      .changed("displayName", oldName, newName)
      .sensitiveChanged("secret", oldSecret, newSecret)
      .build()
  );
}
```

The recorder accepts real `UUID` or `String` resource IDs without inventing a substitute. Sensitive values must never reach audit storage. The diff supplier executes only when audit is enabled and a valid actor is available.

### In-process asynchronous tasks

Taskmigo targets Java 26. The in-thread context implementation uses `ScopedValue` instead of a mutable `ThreadLocal`; lexical scopes automatically restore the previous actor. A general executor still does **not** inherit the submitting thread's context. Capture at submission time:

```java
var task = AuditContexts.captureCallable(() -> ticketService.execute(command));

Future<Result> future = executor.submit(task);
```

Use `captureRunnable` for void tasks. Both restore previous thread context even after exceptions. These helpers are **only** for in-process execution.

For a task whose executor changes from Web to Worker in the same process, provide explicit receiving provenance while keeping the User as actor:

```java
var task = AuditContexts.captureCallable(AuditExecutor.worker("worker"), AuditCause.job(jobId), () ->
  ticketService.execute(command)
);

Future<Result> future = executor.submit(task);
```

### Durable background jobs

Persist attribution in trusted job metadata alongside the command:

```java
String metadata = AuditContextCodec.encode(AuditContexts.require());
// Persist metadata with the command under a trusted producer.
```

On the receiving side, after validating job metadata provenance and integrity:

```java
AuditContext original = AuditContextCodec.decodeTrusted(trustedMetadata);

AuditContext workerContext = new AuditContext(original.actor(), AuditExecutor.worker("worker"), AuditCause.job(jobId));

AuditContexts.run(workerContext, () -> ticketService.execute(command));
```

The codec is versioned and preserves typed actor identity. **It is not a signature or authentication protocol**. Never decode untrusted API input and treat it as authorization to become a SYSTEM actor. Validate queue access, retain original initiating identity, and check current authorization independently.

### Common mistakes

- Do not use `AuditContexts.isBound()` in business services to skip recording. An absent actor is a bug, not an audit opt-out.
- Do not use the Worker app name as the actor for every Worker mutation.
- Do not propagate a Java `ThreadLocal` across durable queues.
- Do not record passwords, OAuth secrets or token values, including in causation IDs.
- Do not append an audit event after the transaction has committed. Audit append must participate in the same transaction and failure must abort the business mutation.
- Do not store an invented UUID when the domain entity's real identifier is a string.
