# Internal server modules

Projects below this directory are reusable implementation details, not application-facing contracts.

- Executable projects under `apps/` must not depend directly on `:modules:internal:*`.
- Production modules outside this directory must not export internal projects through Gradle `api` dependencies.
- Public application APIs and Spring Modulith named interfaces must not expose types owned by internal projects.
- Internal projects may depend on one another when the dependency is part of their implementation.

These rules are enforced by the root Gradle verification tasks and are derived from project paths so new projects under `modules/internal/` are covered automatically.
