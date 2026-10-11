# Simplify Identity User Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the Identity/User capability from mandatory Hexagonal package vocabulary to a feature-first Onion structure without changing business behavior or weakening architecture enforcement.

**Architecture:** Keep `user/domain` as the inner ring. Publish externally callable User use cases from `user/application/api`; keep context-private use cases under `user/application/internal`; place application implementations and application-owned dependency abstractions directly under `user/application`. Move JPA code to `user/persistence`, Audit integration to `user/integration/audit`, and Spring wiring to `user/configuration`. Keep legacy Hexagonal rules for capabilities not yet migrated.

**Tech Stack:** Java 26, Gradle 9.7.1, Spring Modulith, ArchUnit, Spring Data JPA, JUnit 5.

**Spec:** GitHub issue #252.

## Global Constraints

- Preserve observable business, HTTP/API, security, authorization, transaction, and persistence/query behavior.
- `server/apps/web`, `server/apps/worker`, and `server/apps/migration` remain the executable application roots.
- No internal compatibility aliases; Taskmigo is v0.
- Application/domain code must remain independent from Spring Data/JPA concrete infrastructure.
- Cross-context APIs remain deliberately published through Spring Modulith named interfaces.
- Do not force untouched capabilities into the new layout in this PR.

## Review Focus

- Published `user` and `user-use-cases` contracts must not accidentally expose application implementations or repository abstractions.
- User application classes must not import moved persistence/integration/configuration implementations after package changes.
- JPA/Spring Data usage must be confined to `user.persistence` for this capability.
- Existing web/worker/migration consumers must compile against the renamed published application package.
- User application tests must remain framework-light after package moves.

---

### Task 1: Add the migration architecture gate

**Files:**

- Create: `server/modules/identity/src/test/java/io/taskmigo/identity/IdentityUserOnionArchitectureTest.java`.
- Modify architecture checks as needed after the package migration.

**Interfaces:**

- Produces: a regression gate rejecting retired Identity/User Hexagonal-only package paths.

- [x] Write the failing package-vocabulary architecture test.
- [x] Run CI and verify it fails because current User classes still reside in retired paths.
- [ ] Keep the test through the final refactor as a permanent regression guard.

### Task 2: Simplify Identity/User application packages

**Files:**

- Move `user/application/port/in/api/**` -> `user/application/api/**`.
- Move `user/application/port/in/internal/**` -> `user/application/internal/**`.
- Move `user/application/port/out/*.java` -> `user/application/*.java`.
- Move `user/application/service/*.java` -> `user/application/*.java`.
- Update all imports and package declarations across server/tests.

**Interfaces:**

- Produces: named interface `user-use-cases` from `user.application.api`.

- [ ] Move files and imports without behavior edits.
- [ ] Update Spring Modulith named-interface declarations and consumers.
- [ ] Keep application/domain independent from concrete outward implementations.

### Task 3: Move outward implementations to responsibility-oriented packages

**Files:**

- Move `user/adapter/out/persistence/**` -> `user/persistence/**`.
- Move `user/adapter/out/audit/**` -> `user/integration/audit/**`.
- Move `user/composition/**` -> `user/configuration/**`.
- Update package declarations/imports and architecture rules.

**Interfaces:**

- Consumes: application-owned abstractions from Task 2.
- Produces: outward implementations whose paths name responsibility rather than Hexagonal direction.

- [ ] Confine User JPA/Spring Data usage to `user.persistence..`.
- [ ] Keep Audit integration inward-dependent on application contracts.
- [ ] Keep Spring wiring in `user.configuration`.

### Task 4: Final regression verification

**Files:**

- Update package docs affected by the move.

**Interfaces:**

- Produces: reviewable vertical slice for #252 with no compatibility duplicate paths.

- [ ] Run required server architecture/unit tests and formatting/static analysis checks.
- [ ] Compare branch against `next` and confirm changes are structural only.
- [ ] Verify no old Identity/User Hexagonal-only paths remain.
- [ ] Open a PR linked to #252; later capabilities remain separate slices.
