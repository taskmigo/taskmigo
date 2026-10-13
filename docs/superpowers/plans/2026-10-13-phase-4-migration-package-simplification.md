# Phase 4 Migration Package Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify the Migration executable to responsibility-oriented packages while preserving installation, provisioning, OAuth reconciliation, SERIALIZABLE retry, startup, and audit behavior.

**Architecture:** Treat Migration as an executable runtime boundary, not as a reusable business bounded context. Replace the generic `application.*` and `infrastructure.*` buckets with direct runtime responsibilities: `installation`, `bootstrap`, `oauth`, and `configuration`; keep existing constructors, Spring beans, algorithms, and transaction behavior unchanged.

**Tech Stack:** Java 26, Spring Boot, Spring Authorization Server JDBC, Spring Modulith, ArchUnit, Gradle.

**Spec:** `server/ARCHITECTURE.md` (tracking issue #252, Phase 4 executable apps)

## Global Constraints

- Keep exactly `server/apps/web`, `server/apps/worker`, and `server/apps/migration` as executable app roots.
- Do not change product behavior, public APIs, authorization semantics, transaction semantics, database behavior, or persistence/query performance.
- Preserve Migration installation, provider-owned provisioning, OAuth-client reconciliation, SERIALIZABLE/retry, startup, and synchronous/post-commit observable behavior.
- Do not keep compatibility aliases for retired internal packages; Taskmigo is v0.
- Keep `package-info.java` documentation and `@NullMarked` coverage for Java packages.
- Reusable modules must not depend back on Migration.

## Review Focus

- Concurrent OAuth reconciliation must still converge through the existing SERIALIZABLE transaction/retry behavior.
- Existing managed OAuth secret hashes must remain stable when input is unchanged and rotate only when the configured secret changes.
- An existing unmanaged OAuth client with the same client id must still be rejected rather than adopted.
- Managed resource absence/deletion and hierarchy-closure behavior must remain unchanged.
- Startup/resource loading must still load the same YAML plan and invoke one-shot installation with the same Spring wiring.

---

### Task 1: Lock the final Migration package contract with a RED architecture test

**Files:**
- Modify: `server/apps/migration/src/test/java/io/taskmigo/migration/MigrationPackageArchitectureTest.java`

**Interfaces:**
- Consumes: current Migration production packages at the Phase 4 integration-branch baseline.
- Produces: an ArchUnit regression gate rejecting `io.taskmigo.migration.application..` and `io.taskmigo.migration.infrastructure..` as retired generic architecture buckets.

- [ ] **Step 1: Add the failing retired-package architecture test**

Add `shouldRejectRetiredArchitectureVocabularyWhenMigrationPackagesAreChecked()` with `@DisplayName`, the required `Verifies/Given/Expect` Javadoc block, and Arrange/Act/Assert comments. Import only production Migration classes and assert that no production class resides in `io.taskmigo.migration.application..` or `io.taskmigo.migration.infrastructure..`.

- [ ] **Step 2: Run the focused architecture test and verify RED**

Run: `./gradlew :apps:migration:test --tests io.taskmigo.migration.MigrationPackageArchitectureTest`

Expected: FAIL only because current production classes still reside under the two retired roots.

- [ ] **Step 3: Commit the RED checkpoint**

Commit the architecture-test-only change so remote CI can independently prove the gate detects the current structure before production files move.

### Task 2: Move Migration production code to direct runtime responsibilities

**Files:**
- Move/modify: `server/apps/migration/src/main/java/io/taskmigo/migration/application/model/InstallationPlan.java` → `server/apps/migration/src/main/java/io/taskmigo/migration/installation/InstallationPlan.java`
- Move/modify: `server/apps/migration/src/main/java/io/taskmigo/migration/application/model/ManagedOAuthClient.java` → `server/apps/migration/src/main/java/io/taskmigo/migration/oauth/ManagedOAuthClient.java`
- Move/modify: `server/apps/migration/src/main/java/io/taskmigo/migration/application/service/MigrationInstaller.java` → `server/apps/migration/src/main/java/io/taskmigo/migration/installation/MigrationInstaller.java`
- Move/modify: `server/apps/migration/src/main/java/io/taskmigo/migration/application/service/ManagedResourceReconciler.java` → `server/apps/migration/src/main/java/io/taskmigo/migration/installation/ManagedResourceReconciler.java`
- Move/modify: `server/apps/migration/src/main/java/io/taskmigo/migration/application/service/InternalClientReconciler.java` → `server/apps/migration/src/main/java/io/taskmigo/migration/installation/InternalClientReconciler.java`
- Move/modify: `server/apps/migration/src/main/java/io/taskmigo/migration/infrastructure/oauth/InternalClientMetadata.java` → `server/apps/migration/src/main/java/io/taskmigo/migration/oauth/InternalClientMetadata.java`
- Move/modify: `server/apps/migration/src/main/java/io/taskmigo/migration/infrastructure/oauth/ManagedClientRepository.java` → `server/apps/migration/src/main/java/io/taskmigo/migration/oauth/ManagedClientRepository.java`
- Move/modify: `server/apps/migration/src/main/java/io/taskmigo/migration/infrastructure/config/AuthorizationObjectSchemaConfiguration.java` → `server/apps/migration/src/main/java/io/taskmigo/migration/configuration/AuthorizationObjectSchemaConfiguration.java`
- Move/modify: `server/apps/migration/src/main/java/io/taskmigo/migration/infrastructure/config/MigrationConfiguration.java` → `server/apps/migration/src/main/java/io/taskmigo/migration/configuration/MigrationConfiguration.java`
- Replace package docs for the retired subpackages with package docs for `installation`, `oauth`, and `configuration`.
- Modify imports in `server/apps/migration/src/main/java/io/taskmigo/migration/bootstrap/MigrationResourceLoader.java`, `MigrationRunner.java`, tests, and the root Migration docs as needed.
- Modify: `server/apps/migration/src/test/java/io/taskmigo/migration/MigrationPackageArchitectureTest.java`
- Modify: `server/apps/migration/src/test/java/io/taskmigo/migration/MigrationIntegrationTest.java`

**Interfaces:**
- Consumes: provider-owned Authorization/Identity provisioning APIs, Spring Authorization Server JDBC repository, Spring transaction manager, PasswordEncoder, YAML resource loader.
- Produces: the same one-shot Migration runtime behavior with internal package names `installation`, `bootstrap`, `oauth`, and `configuration`.

- [ ] **Step 1: Relocate files without changing algorithms or constructor wiring**

Change only package declarations/imports/package docs needed for the moves. Keep `MigrationInstaller.install`, the three-attempt retry loop, `ISOLATION_SERIALIZABLE`, reconciliation ordering, OAuth ownership checks, and secret handling byte-for-byte equivalent in behavior.

- [ ] **Step 2: Retarget architecture enforcement to final packages**

Keep the RED retired-root rule. Update the provider-owned provisioning rule to inspect `io.taskmigo.migration.installation` instead of `io.taskmigo.migration.application.service`, preserving the same forbidden private/runtime package set.

- [ ] **Step 3: Update tests only for package/import moves**

Update `MigrationIntegrationTest` and `MigrationResourceLoaderTest` imports to the new internal packages. Do not weaken assertions or remove concurrency, OAuth ownership, credential, deletion, audit, or schema coverage.

- [ ] **Step 4: Run focused GREEN verification**

Run: `./gradlew :apps:migration:test`

Expected: PASS, including the retired-package architecture gate and all Migration integration/resource tests.

- [ ] **Step 5: Run repository-required server verification**

Run: `./gradlew --no-daemon --console=plain --continue build`

Expected: PASS.

### Task 3: Final review and PR handoff

**Files:**
- Review the complete child-branch diff against `server/ARCHITECTURE.md` and #252.
- Update the child PR body using `.github/pull_request_template.md`.

**Interfaces:**
- Consumes: final child-branch diff and exact CI SHA.
- Produces: a focused PR targeting `refactor/252-onion-identity-user` with no behavior change and traceable Phase 4 status.

- [ ] **Step 1: Self-review the whole branch**

Confirm there are no remaining production classes under `io.taskmigo.migration.application..` or `io.taskmigo.migration.infrastructure..`, no compatibility aliases, and no unrelated changes.

- [ ] **Step 2: Open/update the child PR**

Use a sentence-style title, `N/A` specification justification for this internal refactor, exact behavior-preservation risks, and `Covered by automated CI.` for verification. Do not add `Closes #252` to the child PR.

- [ ] **Step 3: Verify all required GitHub checks on the exact final SHA**

Required: Formatting, Server, Qodana, Kubernetes Integration, Authorization performance (plus Markdown if triggered).

Expected: all required checks PASS before marking the child PR ready for review.
