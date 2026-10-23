# Internal Server Module Boundaries Implementation Plan

> **For agentic implementation:** Execute this plan with Superpowers `executing-plans`; keep implementation complete before starting CI repair.

**Goal:** Implement issue #294 by introducing a self-enforcing `server/modules/internal/` category without changing runtime behavior.

**Architecture:** Keep Java package names and Spring Modulith logical module names stable while relocating implementation-only Gradle projects. Restrict non-internal Gradle/API exposure using project-path discovery and ABI inspection so future `:modules:internal:*` projects are protected automatically.

**Spec:** [Issue #294](https://github.com/taskmigo/taskmigo/issues/294)

---

## Task 1: Relocate implementation-only modules and close current API leaks

**Files:**

- Move `server/modules/eav` to `server/modules/internal/eav`.
- Move `server/modules/jpa-query` to `server/modules/internal/jpa-query`.
- Move `server/modules/language` to `server/modules/internal/language`.
- Update `server/settings.gradle.kts`, affected module/benchmark Gradle files, Docker/Qodana references, and the two Language-leaking Java API surfaces named in #294.

**Steps:**

1. Change project paths and all in-repository dependency references atomically.
2. Keep Ticket -> EAV, Query/Authorization/Identity -> Language/JPA Query dependencies as `implementation`; allow EAV -> JPA Query `api` because both projects are internal.
3. Make the Language-injecting `FilterByCompiler` constructor package-private.
4. Move `StatementExecutionArtifact` out of the exported `authorization :: statement` named interface into request application state.
5. Preserve Docker build inputs, Qodana generation/exclusions, benchmark dependencies, and database runtime composition.

## Task 2: Enforce the category automatically

**Files:**

- Modify `server/build.gradle.kts`.

**Steps:**

1. Derive internal projects from the `:modules:internal:` project-path prefix.
2. Fail verification when any `:apps:*` project directly depends on an internal project.
3. Fail verification when any non-internal `:modules:*` project exports an internal project through `api` or `compileOnlyApi`.
4. Derive packages owned by internal projects from `src/main/java` and fail when application production sources import them.
5. Derive public module packages from Spring Modulith `@ApplicationModule`, `@NamedInterface`, and `application.api` source packages; run `jdeps --api-only` against non-internal module JARs and fail if their supported ABI references an internal package.
6. Wire the aggregate boundary verification into root `check`.

## Verification and CI repair

1. Inspect the complete branch diff and search for retired Gradle paths before opening the PR.
2. Open the PR against `next` and let required CI execute the full server/Modulith verification.
3. Per the user instruction, only after implementation is complete, diagnose CI failures from current-head logs/artifacts and repair the owning implementation commit rather than appending a generic pipeline-fix commit.

**Ruling:** Local execution is unavailable because the provided runtime cannot resolve GitHub for a repository clone. The user explicitly requested pipeline work only after coding is complete, so test/verification execution is deferred to the post-implementation CI phase instead of using CI as a pre-implementation TDD runner.
