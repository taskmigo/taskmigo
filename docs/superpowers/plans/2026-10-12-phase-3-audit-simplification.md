# Phase 3 Audit Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify the Audit module from mandatory Hexagonal package vocabulary to a compact Onion structure without changing audit behavior, published capability boundaries, persistence behavior, or specialized transaction semantics.

**Architecture:** Keep Audit small and cohesive rather than inventing fake DDD layers. Public append/privacy/query capabilities remain separately published, application-owned storage and transaction abstractions live in `audit.application`, JPA moves to `audit.persistence`, Spring transaction execution moves to `audit.transaction`, and bean wiring moves to `audit.configuration`.

**Tech Stack:** Java 26, Gradle 9.7.1, Spring Modulith, ArchUnit, Spring Data JPA, JUnit 5, Mockito, AssertJ.

**Spec:** GitHub issue #252 and `server/ARCHITECTURE.md`.

## Global Constraints

- Preserve synchronous audit append behavior and failure propagation.
- Preserve privacy scrubbing and query behavior.
- Preserve `PROPAGATION_MANDATORY` for audit writes and read-only transaction handling for audit reads.
- Preserve separate published append, privacy, and query capability boundaries; rename them only to remove Hexagonal direction vocabulary.
- Do not add a fake `domain` layer to Audit.
- Do not perform Phase 4 Web package restructuring or reopen completed Identity structure.
- No compatibility aliases; obsolete Audit Hexagonal packages must disappear in the same slice.
- Pipeline/workflow configuration is out of scope.

## Review Focus

- Caller-owned mutation transactions must still roll back when synchronous audit persistence fails.
- Audit writes must still require an existing transaction rather than silently opening an independent write transaction.
- Identity must retain access only to Audit append/privacy contracts and published event/model contracts.
- Web must retain access only to Audit query/model contracts.
- JPA/Spring types must not leak into Audit application contracts after the move.

---

### Task 1: Establish the Audit Onion regression gate

**Files:**

- Create: `server/modules/audit/src/test/java/io/taskmigo/audit/AuditOnionArchitectureTest.java`

**Interfaces:**

- Consumes: `io.taskmigo.architecture.OnionArchitectureRules`.
- Produces: regression coverage for Audit inward dependencies and retired Hexagonal package vocabulary.

- [ ] **Step 1:** Add architecture tests classifying `..application..`, `..persistence..`, and `..configuration..` / `..transaction..` as the intended Audit Onion responsibilities.
- [ ] **Step 2:** Add a regression rule rejecting `..application.port..`, `..application.service..`, `..adapter..`, `..composition..`, and unjustified `..infrastructure..` production packages.
- [ ] **Step 3:** Run `./gradlew :modules:audit:test --tests 'io.taskmigo.audit.AuditOnionArchitectureTest'` and confirm the retired-package rule fails against the legacy layout.

### Task 2: Move Audit to responsibility-oriented Onion packages

**Files:**

- Move: `application/port/in/append/**` -> `application/append/**`.
- Move: `application/port/in/privacy/**` -> `application/privacy/**`.
- Move: `application/port/in/query/**` -> `application/query/**`.
- Move: `application/port/out/{AuditLogStore,AuditTransactionRunner}.java` -> `application/`.
- Move: `application/service/DefaultAudit*Service.java` -> `application/`.
- Move: `adapter/out/persistence/**` -> `persistence/**`.
- Move: `adapter/out/transaction/SpringAuditTransactionRunner.java` -> `transaction/`.
- Move: `composition/AuditApplicationConfiguration.java` -> `configuration/`.
- Move matching Audit tests to the responsibility they exercise.

**Interfaces:**

- Produces: `application.append.AuditAppendService`, `application.privacy.AuditPrivacyService`, `application.query.AuditQueryService`.
- Keeps private/application-owned: `AuditLogStore`, `AuditTransactionRunner`, and `DefaultAudit*Service` under `audit.application`.

- [ ] **Step 1:** Rename published Modulith interfaces from `append-input`, `privacy-input`, and `query-input` to `append`, `privacy`, and `query` without broadening package exposure.
- [ ] **Step 2:** Move application-owned abstractions/services without changing method signatures or service logic.
- [ ] **Step 3:** Move JPA, transaction, and configuration implementations outward without changing persistence or transaction behavior.
- [ ] **Step 4:** Move `DefaultAuditAppendServiceTest` to `audit.application` and `JpaAuditLogStoreTest` to `audit.persistence`, changing only package/import references.

### Task 3: Update direct consumers and verify the slice

**Files:**

- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/package-info.java`.
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/integration/audit/DefaultUserAuditAppender.java`.
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/integration/audit/DefaultUserAuditScrubber.java`.
- Modify: `server/apps/web/src/main/java/io/taskmigo/web/package-info.java`.
- Modify: Audit imports in Web controller/integration tests as required.

**Interfaces:**

- Consumes: final Audit append/privacy/query contracts from Task 2.
- Produces: no new runtime contract.

- [ ] **Step 1:** Update Identity to `audit :: append` / `audit :: privacy` and the moved Java contracts.
- [ ] **Step 2:** Update Web to `audit :: query` and the moved query contract; do not restructure Web packages.
- [ ] **Step 3:** Sweep the server for retired Audit package imports and named-interface strings; no stale consumer may remain.
- [ ] **Step 4:** Run `./gradlew :modules:audit:test :modules:identity:compileJava :modules:identity:compileTestJava :apps:web:compileJava :apps:web:compileTestJava`.
- [ ] **Step 5:** Run the existing Web audit transaction integration test to confirm caller-owned transaction behavior is unchanged.
- [ ] **Step 6:** Run repository formatting and the required server/CI verification before marking the slice complete.
