# Server agent instructions

These instructions apply to `server/` and all of its descendants. Repository-wide instructions in the root `AGENTS.md` and `CONTRIBUTING.md` also apply.

## Start here

Before changing server code:

- Read the repository-level `AGENTS.md` and `CONTRIBUTING.md`, then check for more specific instructions from the repository root to the file being changed.
- Inspect `.agents/skills/` and use the skill that matches the task. Read the complete `SKILL.md` before applying it.
- Use `spring-boot-testing` when writing or reviewing Spring Boot tests.
- Use `java-javadoc` when adding or changing Javadoc, package documentation, or public Java APIs.

## Specification first

The [Taskmigo specification repository](https://github.com/taskmigo/specification) is the authoritative source for product and system behavior.

- Identify the affected feature specification before changing behavior.
- Start with that feature's `README.md`, then follow its table of contents and read order.
- Preserve requirement IDs and the meaning of normative terms such as `SHALL`, `SHOULD`, and `MAY`.
- If the implementation and specification disagree, report the conflict instead of silently choosing one.

## Server map

- `apps/web` contains HTTP adapters, OAuth endpoints, REST APIs, and OpenAPI configuration.
- `apps/bootstrap` runs database migration and initial data setup.
- `apps/worker` contains background processing.
- `modules/` contains reusable domain and application modules.
- `benchmarks/` contains performance benchmarks and is not a replacement for functional tests.

Put reusable feature behavior in its owning module and HTTP-specific behavior in `apps/web`. Before editing, identify which application or module owns the behavior.

## Quick verification

Install repository tooling once from the repository root:

```bash
npm ci
```

Run formatting from the repository root:

```bash
npm run format:check
```

Run the server build from `server/`:

```bash
./gradlew --no-daemon build
```

Spring integration tests use Testcontainers and require Docker. When running the application locally, start the PostgreSQL service from the repository root with `docker compose up -d postgres`. Run test and verification commands sequentially rather than starting them in parallel.

## HTTP module ownership

- `apps/web` owns the HTTP adapters, OAuth endpoints, shared API transport infrastructure, and OpenAPI configuration.
- Feature modules own SDK/domain/application services. They must not depend on `apps/web` or expose REST controllers.
- Keep reusable feature behavior in its feature module and keep HTTP-specific DTOs, controllers, response envelopes,
  pagination bindings, versioning, and exception translation in `apps/web`.
- Adding `implementation(project(":modules:<feature>"))` to `apps/web` must be sufficient to make the feature available;
  do not add feature-specific `@Import`, controller registration, or component-scan wiring.

## Persistence queriesre

- Do not use `org.springframework.data.jpa.repository.Query` by default. Prefer Spring Data derived queries, specifications, the persistence API supplied by the owning library, or another repository abstraction when those alternatives keep the solution readable, maintainable, and ergonomic.
- Do not optimize for avoiding `@Query` at the expense of developer experience. If the alternative introduces excessive boilerplate, awkward repository APIs, harder-to-understand code, or disproportionate implementation complexity, treat that DX regression as a legitimate reason to consider `@Query`.
- Balance the complete solution rather than applying the rule mechanically. Compare readability, maintainability, type safety, testability, implementation complexity, and developer experience, and choose the simplest solution with acceptable long-term trade-offs.
- If `@Query` appears preferable after that trade-off analysis, do not add or keep it based on agent judgment alone. Explain the alternatives considered, the DX or solution-quality cost of avoiding `@Query`, and ask a maintainer for explicit confirmation before using it.
- Treat maintainer confirmation as specific to the proposed use case. Do not infer a general exception for other repositories or queries.
- Native queries and other raw-SQL mechanisms remain prohibited by the repository contribution rules even when a maintainer approves use of `@Query`.
