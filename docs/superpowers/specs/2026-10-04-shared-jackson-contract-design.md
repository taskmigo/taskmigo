# Shared Jackson Contract Design

## Context

Taskmigo treats UTC as a non-configurable server runtime invariant. The server currently has several independent Jackson construction paths:

- Spring Boot creates the Web `JsonMapper`.
- `OAuthPersistenceConfiguration` builds a dedicated `JsonMapper` with Spring Security modules and a restricted polymorphic type validator.
- `JpaAuditLogStore` builds a dedicated plain `JsonMapper`.
- `MigrationResourceLoader` builds a dedicated `YAMLMapper`.

A Web-only `spring.jackson.time-zone=UTC` property configures only Spring Boot-managed mappers and can be overridden through external configuration. It therefore does not establish a Taskmigo-wide Jackson invariant.

Spring Boot 4.1 uses Jackson 3 as its preferred/default Jackson implementation and exposes `JsonMapperBuilderCustomizer` for builder customization. Jackson 3 mapper configuration is builder-based.

## Goals

1. Establish one reusable Taskmigo Jackson policy for JSON and YAML mapper builders.
2. Make UTC a hard Jackson invariant rather than an operator-selectable Spring property.
3. Apply the same policy to Spring Boot-managed and manually-created mappers.
4. Preserve purpose-specific mapper configuration, especially OAuth security modules and YAML support.
5. Keep `foundation:core` free of Jackson and Spring dependencies.
6. Avoid introducing a shared singleton `JsonMapper` that couples unrelated serialization use cases.

## Non-goals

- Replacing purpose-specific OAuth Jackson modules or polymorphic type validation.
- Converting YAML parsing to JSON.
- Moving business DTOs, serializers, or persistence models into Foundation.
- Supporting an operator-configurable server serialization timezone.
- Introducing Jackson 2 APIs. Spring Boot 4.1/Jackson 3 APIs are the target.

## Module Boundary

Add:

`server/modules/foundation/jackson`

with Gradle path:

`:modules:foundation:jackson`

The module owns Taskmigo-wide Jackson defaults and Spring Boot integration for those defaults.

It may depend on:

- Jackson 3 databind.
- Spring Boot autoconfigure APIs required to contribute a `JsonMapperBuilderCustomizer`.

It must not depend on bounded-context modules such as Identity, Audit, Authorization, Query, Web, Worker, or Migration.

`foundation:core` remains framework- and serialization-library-neutral.

## Shared Builder Policy

The module exposes a small public utility, `TaskmigoJackson`, that applies Taskmigo's common settings to an existing Jackson 3 `MapperBuilder`.

Conceptually:

```java
TaskmigoJackson.configure(JsonMapper.builder())
TaskmigoJackson.configure(YAMLMapper.builder())
```

The method configures the supplied builder and returns that builder for normal chaining.

The initial shared invariant is:

- Jackson default timezone is UTC.

The API is intentionally builder-oriented. Callers remain responsible for format-specific or security-specific configuration before building the mapper.

Jackson 3 already defaults its mapper timezone to UTC, but Taskmigo must set UTC explicitly so the application contract is independent of library defaults and remains stable when other customization is introduced.

## Spring Boot Integration

The module contributes an auto-configuration that provides a `JsonMapperBuilderCustomizer`.

The customizer:

- Applies the same `TaskmigoJackson` builder policy.
- Runs after Spring Boot's own Jackson property customizer.
- Re-applies UTC last so external values such as `SPRING_JACKSON_TIME_ZONE=Asia/Ho_Chi_Minh` cannot change Taskmigo's effective JSON timezone.

The module must use Jackson 3 / Spring Boot 4.1 APIs. It must not use deprecated Jackson 2 customization APIs.

Because the shared customizer is authoritative, remove the Web-only:

```yaml
spring:
  jackson:
    time-zone: UTC
```

That property is not a security or correctness boundary because Spring configuration is externally overrideable.

## Consumer Migration

### Web HTTP JSON

`apps:web` depends on `foundation:jackson`.

Spring Boot continues to own the Web `JsonMapper` lifecycle. Taskmigo does not define a replacement mapper bean.

The shared `JsonMapperBuilderCustomizer` hard-applies the common Taskmigo policy.

### OAuth persistence mapper

`OAuthPersistenceConfiguration.authorizationMapper()` keeps its dedicated mapper and existing Spring Security modules / type validator.

Its builder must pass through `TaskmigoJackson.configure(...)` before `build()`.

The shared policy must not weaken or broaden the existing allowed polymorphic subtypes.

### Audit persistence mapper

`JpaAuditLogStore` keeps a dedicated mapper, but its builder must pass through `TaskmigoJackson.configure(...)`.

`modules:audit` therefore takes a dependency on `foundation:jackson` rather than constructing a policy-free mapper.

### Migration YAML mapper

`MigrationResourceLoader` keeps a dedicated `YAMLMapper`.

Its builder must pass through `TaskmigoJackson.configure(...)`, proving that the common policy is format-agnostic at the mapper-builder level.

`apps:migration` depends on `foundation:jackson`.

## Runtime Interaction with UTC Bootstrap

The Jackson module complements, but does not replace, the process-wide UTC invariant implemented by `TaskmigoRuntime` and enforced from the Spring Boot `ApplicationStartingEvent`.

The layers are intentionally redundant:

1. Container defaults to UTC.
2. Spring Boot starting listener hard-enforces JVM UTC.
3. Jackson builders hard-apply UTC.
4. Hibernate JDBC conversion hard-applies UTC.
5. PostgreSQL connections inherit the effective JVM UTC and are verified by integration tests.

A future regression in one layer should not silently change another layer's timezone contract.

## Testing

### Shared builder policy

A `foundation:jackson` test deliberately changes the JVM default timezone to a non-UTC zone, applies `TaskmigoJackson` to a mapper builder, and verifies the resulting mapper's configured timezone is UTC.

The test must restore global JVM state in `finally`.

### Spring override resistance

An auto-configuration test starts a minimal Spring Boot context with:

`spring.jackson.time-zone=Asia/Ho_Chi_Minh`

and verifies the auto-configured Jackson 3 `JsonMapper` still uses UTC.

This proves external Spring configuration cannot override the Taskmigo invariant.

### Existing consumers

Existing OAuth, Audit, Migration, and Web tests remain green after migrating their builders. No behavior-specific security modules or YAML semantics may regress.

### Server regression suite

The existing runtime UTC tests from issue #238 remain separate:

- process/JVM invariant,
- PostgreSQL session UTC,
- exact retained `Instant` preservation.

Jackson tests must not replace those boundary-specific tests.

## Dependency and Architecture Rules

- `foundation:core` must continue to reject dependencies on Jackson.
- `foundation:jackson` must not depend on higher-level Taskmigo capabilities.
- Consumers opt in by depending on `foundation:jackson`; `foundation:spring` does not gain a mandatory dependency on Jackson.
- Do not expose a global mutable `ObjectMapper` or `JsonMapper` singleton.
- Do not add Jackson 2 APIs.

## Compatibility and Risk

No API or persisted-data schema changes are intended.

Potential serialization compatibility risk is limited to mapper settings that become shared in the future. For this change, UTC is the only common setting, so existing JSON/YAML structural representation must remain unchanged.

OAuth mapper security configuration is explicitly preserved and is a review-critical area.

## Success Criteria

- One `foundation:jackson` module owns the shared Jackson builder policy.
- Spring Boot-managed Web JSON cannot be changed away from UTC by external Jackson timezone configuration.
- All manually-created production JSON/YAML mappers apply the same common policy.
- OAuth-specific security modules and validator remain unchanged.
- `foundation:core` remains Jackson-free.
- All server CI checks pass.
