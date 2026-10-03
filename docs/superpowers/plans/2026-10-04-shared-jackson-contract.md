# Shared Jackson Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reusable `foundation:jackson` module that hard-applies Taskmigo's UTC Jackson policy to Spring Boot-managed and manually-built JSON/YAML mappers without replacing purpose-specific mapper configuration.

**Architecture:** `foundation:jackson` owns one builder-level policy through `TaskmigoJackson.configure(...)` and one Spring Boot auto-configuration that applies the same policy after Boot's own Jackson property customization. Web, OAuth persistence, Audit persistence, and Migration YAML opt in to the module while keeping their existing mapper lifecycle and specialized modules.

**Tech Stack:** Java 26, Spring Boot 4.1.1, Jackson 3, Gradle Kotlin DSL, JUnit 5, AssertJ, ArchUnit.

**Spec:** `docs/superpowers/specs/2026-10-04-shared-jackson-contract-design.md`

## Global Constraints

- UTC is a non-configurable Taskmigo server invariant.
- Use Jackson 3 APIs only; do not introduce deprecated Jackson 2 customization APIs.
- Keep `foundation:core` free of Jackson and Spring dependencies.
- Do not expose a shared mutable `ObjectMapper` or `JsonMapper` singleton.
- Preserve OAuth Spring Security Jackson modules and the existing restricted polymorphic type validator.
- Preserve Migration YAML format and existing serialized JSON structures.
- `foundation:spring` must not gain a mandatory dependency on Jackson.
- Production code must not introduce raw/native SQL.
- Server verification runs sequentially; CI-repair changes rewrite the owning implementation commit rather than adding repair-only commits.

## Review Focus

- External `spring.jackson.time-zone` is deliberately set to a non-UTC zone: the Spring-managed Jackson 3 mapper must still use UTC. Covered in Task 2.
- JVM default timezone is non-UTC when a manual mapper is built: the shared builder policy must still explicitly configure UTC. Covered in Task 1.
- OAuth principal persistence round-trips through the migrated mapper: `UserSessionPrincipal` type/identity and restricted polymorphic configuration must remain intact. Covered in Task 3.
- Audit JSON persistence migrates to the shared policy without changing its stored field structure or privacy scrubbing behavior. Covered in Task 4.
- Migration YAML resources still load credentials/placeholders and browser-auth-disabled resources unchanged after the YAML mapper migration. Covered in Task 5.

---

### Task 1: Create the shared Jackson builder policy

**Files:**

- Modify: `server/settings.gradle.kts`
- Create: `server/modules/foundation/jackson/build.gradle.kts`
- Create: `server/modules/foundation/jackson/src/main/java/io/taskmigo/foundation/jackson/TaskmigoJackson.java`
- Create: `server/modules/foundation/jackson/src/main/java/io/taskmigo/foundation/jackson/package-info.java`
- Create: `server/modules/foundation/jackson/src/test/java/io/taskmigo/foundation/jackson/TaskmigoJacksonTest.java`

**Interfaces:**

- Consumes: Jackson 3 `MapperBuilder<M, B>`.
- Produces: `public static <M extends ObjectMapper, B extends MapperBuilder<M, B>> B configure(B builder)`.

- [ ] **Step 1: Scaffold the Gradle module without production Java code**

Add `:modules:foundation:jackson` to `settings.gradle.kts`.

Create `build.gradle.kts` with `java-library`, Spring Boot BOM alignment, `api(libs.jackson.databind)`, and only the JUnit/AssertJ dependencies needed for the first test. This configuration-only scaffold is allowed before the RED test; do not create `TaskmigoJackson` yet.

- [ ] **Step 2: Write the failing builder-policy test**

Add `TaskmigoJacksonTest.shouldConfigureUtcWhenJvmDefaultTimezoneIsNonUtc()`.

The test:

- saves the original JVM default timezone,
- changes it to `Asia/Ho_Chi_Minh`,
- calls `TaskmigoJackson.configure(JsonMapper.builder()).build()`,
- asserts `mapper.serializationConfig().getTimeZone().toZoneId().normalized()` is `ZoneOffset.UTC`,
- restores global JVM state in `finally`.

- [ ] **Step 3: Run the focused test and verify RED**

Run:

```bash
cd server
./gradlew --no-daemon :modules:foundation:jackson:test --tests '*TaskmigoJacksonTest'
```

Expected: FAIL at test compilation because `TaskmigoJackson` does not exist.

- [ ] **Step 4: Implement the minimal builder policy**

Implement:

```java
public static <M extends ObjectMapper, B extends MapperBuilder<M, B>> B configure(B builder)
```

The method must require a non-null builder and return `builder.defaultTimeZone(TimeZone.getTimeZone("UTC"))`.

Do not add Spring integration yet.

- [ ] **Step 5: Run Task 1 tests and verify GREEN**

Run:

```bash
cd server
./gradlew --no-daemon :modules:foundation:jackson:test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

```bash
git add server/settings.gradle.kts server/modules/foundation/jackson
git commit -m "Add shared Jackson builder policy"
```

### Task 2: Hard-apply the policy to Spring Boot-managed Jackson

**Files:**

- Modify: `server/modules/foundation/jackson/build.gradle.kts`
- Create: `server/modules/foundation/jackson/src/main/java/io/taskmigo/foundation/jackson/TaskmigoJacksonAutoConfiguration.java`
- Create: `server/modules/foundation/jackson/src/main/java/io/taskmigo/foundation/jackson/TaskmigoJsonMapperBuilderCustomizer.java`
- Create: `server/modules/foundation/jackson/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `server/modules/foundation/jackson/src/test/java/io/taskmigo/foundation/jackson/TaskmigoJacksonAutoConfigurationTest.java`

**Interfaces:**

- Consumes: `TaskmigoJackson.configure(B builder)` from Task 1.
- Produces: auto-registered `JsonMapperBuilderCustomizer` that applies Taskmigo policy after Boot's order-0 customizer.

- [ ] **Step 1: Write the failing override-resistance test**

Use `ApplicationContextRunner` with Spring Boot `JacksonAutoConfiguration` and `TaskmigoJacksonAutoConfiguration`.

Set:

```text
spring.jackson.time-zone=Asia/Ho_Chi_Minh
```

Assert:

- exactly one Jackson 3 `JsonMapper` exists,
- its serialization and deserialization configuration timezone normalizes to `ZoneOffset.UTC`.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd server
./gradlew --no-daemon :modules:foundation:jackson:test --tests '*TaskmigoJacksonAutoConfigurationTest'
```

Expected: FAIL because Taskmigo's auto-configuration/customizer does not exist and Boot applies the non-UTC property.

- [ ] **Step 3: Implement Spring Boot integration**

Add compile-only dependencies on `org.springframework.boot:spring-boot-autoconfigure` and `org.springframework.boot:spring-boot-jackson`; add `spring-boot-starter-jackson` and `spring-boot-starter-test` only to the test configuration. This keeps the shared builder utility usable by manual-mapper consumers without making Spring/Jackson auto-configuration a transitive runtime requirement.

`TaskmigoJacksonAutoConfiguration` is an `@AutoConfiguration`, guarded with a classpath condition for Jackson 3 Spring Boot customization APIs, that exposes one `JsonMapperBuilderCustomizer` implementation.

`TaskmigoJsonMapperBuilderCustomizer`:

- implements `JsonMapperBuilderCustomizer` and `Ordered`,
- delegates `customize(JsonMapper.Builder builder)` to `TaskmigoJackson.configure(builder)`,
- returns `Ordered.LOWEST_PRECEDENCE` so Taskmigo policy is reapplied after Boot's property customizer.

Register only the auto-configuration through:

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

Do not make `foundation:spring` depend on this module.

- [ ] **Step 4: Run Task 2 tests and verify GREEN**

Run:

```bash
cd server
./gradlew --no-daemon :modules:foundation:jackson:test
```

Expected: PASS for both shared-policy and Spring override tests.

- [ ] **Step 5: Commit Task 2**

```bash
git add server/modules/foundation/jackson
git commit -m "Enforce Jackson UTC in Spring Boot"
```

### Task 3: Adopt the shared policy in Web and OAuth persistence

**Files:**

- Modify: `server/apps/web/build.gradle.kts`
- Modify: `server/apps/web/src/main/resources/application.yaml`
- Modify: `server/apps/web/src/main/java/io/taskmigo/web/composition/auth/OAuthPersistenceConfiguration.java`
- Modify/Test: `server/apps/web/src/test/java/io/taskmigo/web/composition/auth/SessionPrincipalSerializationTest.java`
- Test: existing Web integration suite.

**Interfaces:**

- Consumes: `TaskmigoJackson.configure(B builder)` and Task 2's auto-configuration.
- Produces: Web HTTP mapper and OAuth persistence mapper governed by the same UTC policy while OAuth keeps its security modules.

- [ ] **Step 1: Add a failing Web/OAuth policy assertion**

Extend `SessionPrincipalSerializationTest` with a test that deliberately sets JVM default timezone to `Asia/Ho_Chi_Minh`, obtains `OAuthPersistenceConfiguration.authorizationMapper()`, and asserts the mapper timezone normalizes to UTC.

Restore JVM state in `finally`.

Keep the existing principal round-trip test unchanged; it is the security regression gate.

- [ ] **Step 2: Run the focused Web tests and verify RED**

Run:

```bash
cd server
./gradlew --no-daemon :apps:web:test --tests '*SessionPrincipalSerializationTest'
```

Expected: the new mapper-policy assertion fails against the current policy-free `JsonMapper.builder()`.

- [ ] **Step 3: Migrate Web and OAuth**

Add:

```kotlin
implementation(project(":modules:foundation:jackson"))
```

to Web.

Remove the Web-only:

```yaml
spring:
  jackson:
    time-zone: UTC
```

from `application.yaml`.

Change `authorizationMapper()` so the existing `JsonMapper.Builder`, including the current `SecurityJacksonModules` and validator setup, passes through `TaskmigoJackson.configure(...)` before `build()`.

Do not change the validator's allowed subtype configuration.

- [ ] **Step 4: Run focused Web tests and verify GREEN**

Run sequentially:

```bash
cd server
./gradlew --no-daemon :apps:web:test --tests '*SessionPrincipalSerializationTest'
./gradlew --no-daemon :apps:web:test --tests '*DatabaseUtcIntegrationTest'
```

Expected: PASS; OAuth principal round-trip remains intact and database UTC regression remains independent.

- [ ] **Step 5: Commit Task 3**

```bash
git add server/apps/web
git commit -m "Apply shared Jackson policy to Web"
```

### Task 4: Adopt the shared policy in Audit persistence

**Files:**

- Modify: `server/modules/audit/build.gradle.kts`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/adapter/out/persistence/JpaAuditLogStore.java`
- Modify/Test: `server/modules/audit/src/test/java/io/taskmigo/audit/adapter/out/persistence/JpaAuditLogStoreTest.java`

**Interfaces:**

- Consumes: `TaskmigoJackson.configure(B builder)`.
- Produces: Audit's dedicated `JsonMapper` with Taskmigo policy and unchanged audit JSON structure.

- [ ] **Step 1: Add a failing Audit mapper-policy regression**

Extract the existing static mapper initialization to a package-private static `JsonMapper auditMapper()` factory and have the `JSON` constant call that factory. The factory is production construction code, not a test-only accessor, and gives the package test a direct policy boundary to verify.

The new test changes JVM timezone to `Asia/Ho_Chi_Minh`, creates the mapper, and asserts UTC; restore JVM state in `finally`.

Keep `shouldScrubHistoricalUserPii()` unchanged as the JSON compatibility regression.

- [ ] **Step 2: Run focused Audit tests and verify RED**

Run:

```bash
cd server
./gradlew --no-daemon :modules:audit:test --tests '*JpaAuditLogStoreTest'
```

Expected: new timezone assertion fails before Audit adopts the shared policy.

- [ ] **Step 3: Migrate Audit**

Replace the direct Jackson databind dependency with:

```kotlin
implementation(project(":modules:foundation:jackson"))
```

when the transitive Jackson API from that module is sufficient for Audit compilation.

Build the dedicated Audit mapper through `TaskmigoJackson.configure(JsonMapper.builder()).build()`.

Do not inject or share a global mapper bean.

- [ ] **Step 4: Run Audit tests and verify GREEN**

Run:

```bash
cd server
./gradlew --no-daemon :modules:audit:test
```

Expected: PASS, including unchanged privacy scrub JSON assertions.

- [ ] **Step 5: Commit Task 4**

```bash
git add server/modules/audit
git commit -m "Apply shared Jackson policy to Audit"
```

### Task 5: Adopt the shared policy in Migration YAML

**Files:**

- Modify: `server/apps/migration/build.gradle.kts`
- Modify: `server/apps/migration/src/main/java/io/taskmigo/migration/bootstrap/MigrationResourceLoader.java`
- Modify/Test: `server/apps/migration/src/test/java/io/taskmigo/migration/MigrationResourceLoaderTest.java`

**Interfaces:**

- Consumes: `TaskmigoJackson.configure(B builder)`.
- Produces: Migration's dedicated `YAMLMapper` with the same common UTC policy and unchanged YAML behavior.

- [ ] **Step 1: Add a failing YAML mapper-policy test**

Expose a package-private static `YAMLMapper migrationYamlMapper()` factory on `MigrationResourceLoader` and use it for the instance field.

Add a test that changes JVM timezone to `Asia/Ho_Chi_Minh`, calls the factory, verifies its configured timezone normalizes to UTC, and restores JVM state.

Keep existing credential-loading and browser-auth-disabled tests unchanged as YAML compatibility regressions.

- [ ] **Step 2: Run focused Migration tests and verify RED**

Run:

```bash
cd server
./gradlew --no-daemon :apps:migration:test --tests '*MigrationResourceLoaderTest'
```

Expected: new mapper-policy assertion fails before the shared policy is applied.

- [ ] **Step 3: Migrate Migration YAML**

Add:

```kotlin
implementation(project(":modules:foundation:jackson"))
```

to Migration while retaining `libs.jackson.dataformat.yaml` because YAML format support remains app-specific.

Build `YAMLMapper` through `TaskmigoJackson.configure(YAMLMapper.builder()).build()`.

- [ ] **Step 4: Run Migration tests and verify GREEN**

Run:

```bash
cd server
./gradlew --no-daemon :apps:migration:test --tests '*MigrationResourceLoaderTest'
```

Expected: PASS for timezone policy and existing resource behavior.

- [ ] **Step 5: Commit Task 5**

```bash
git add server/apps/migration
git commit -m "Apply shared Jackson policy to Migration"
```

### Task 6: Protect module boundaries and verify the complete server

**Files:**

- Create: `server/modules/foundation/jackson/src/test/java/io/taskmigo/foundation/jackson/FoundationJacksonPackageArchitectureTest.java`
- Verify: `server/modules/foundation/core/src/test/java/io/taskmigo/foundation/FoundationPackageArchitectureTest.java`
- Modify: PR #240 description/checklist only after current-head verification.

**Interfaces:**

- Consumes: completed `foundation:jackson` and consumer migrations.
- Produces: architecture regression coverage and final verified PR state.

- [ ] **Step 1: Write the architecture rule**

Add `FoundationJacksonPackageArchitectureTest` using ArchUnit.

Import production classes from `io.taskmigo.foundation.jackson` and assert they do not depend on:

- `io.taskmigo.authorization..`
- `io.taskmigo.audit..`
- `io.taskmigo.database..`
- `io.taskmigo.identity..`
- `io.taskmigo.query..`
- `io.taskmigo.web..`
- `io.taskmigo.worker..`
- `io.taskmigo.migration..`

The existing `FoundationPackageArchitectureTest` must continue proving `foundation:core` has no Jackson dependency.

- [ ] **Step 2: Run architecture and module tests**

Run sequentially:

```bash
cd server
./gradlew --no-daemon :modules:foundation:core:test
./gradlew --no-daemon :modules:foundation:jackson:test
```

Expected: PASS.

- [ ] **Step 3: Run repository formatting**

From repository root:

```bash
npm run format:fix
```

Expected: no uncommitted formatting changes remain after committing the formatter-owned edits into their owning commits.

- [ ] **Step 4: Run full server verification sequentially**

Run:

```bash
cd server
./gradlew --no-daemon build
```

Expected: PASS with zero test/checkstyle/NullAway/Spotless failures.

Then run any repository-required OpenAPI verification command from the Server workflow if it is not already covered by `build`.

- [ ] **Step 5: Adversarial whole-branch review**

Review the complete diff against the spec, concentrating on:

- any production `JsonMapper.builder().build()` / `YAMLMapper.builder().build()` path that still bypasses `TaskmigoJackson`,
- accidental Jackson dependency in `foundation:core`,
- OAuth validator or Security Jackson module changes,
- Spring customizer ordering that could allow an external timezone override,
- serialized JSON/YAML compatibility.

Any Critical/Important finding receives one RED→GREEN fix pass in the owning commit.

- [ ] **Step 6: Update PR #240**

Update RCA/Changes with immutable final commit links for the Jackson policy and Spring auto-configuration.

Keep:

```text
## Verification

Covered by automated CI.
```

Leave the pipeline checklist unchecked until all required checks for the final head report success.

- [ ] **Step 7: Final CI confirmation**

Re-anchor PR #240's current head and inspect all required workflow/check results once.

Expected: all required checks are completed successfully before checking the pipeline box.
