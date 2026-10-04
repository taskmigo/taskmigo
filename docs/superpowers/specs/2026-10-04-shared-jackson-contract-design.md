# UTC Runtime and Jackson Contract Design

## Context

Taskmigo treats UTC as a non-configurable server invariant. Issue #238 showed that the previous implementation did not make that invariant explicit across process bootstrap, Jackson configuration, Hibernate/JDBC behavior, and PostgreSQL sessions.

The first implementation attempted to self-correct a non-UTC JVM by calling `TimeZone.setDefault(...)` and hard-applied Jackson's mapper timezone through `MapperBuilder.defaultTimeZone(TimeZone)`. Those APIs require the legacy `java.util.TimeZone` type.

The desired contract is stricter and simpler:

- Taskmigo production code must not mutate the JVM default timezone.
- A server started with a non-UTC effective JVM timezone must fail fast.
- Application boundaries that can be configured independently must declare UTC explicitly.
- Absolute application timestamps remain `Instant`.
- Taskmigo production code must not require a Qodana exception for legacy date/time APIs.

## Goals

1. Make a non-UTC JVM configuration a startup error instead of silently correcting it.
2. Keep Taskmigo production code on `java.time` APIs and remove `java.util.TimeZone` usage introduced by this feature.
3. Keep Spring Boot-managed Jackson explicitly configured as UTC and reject any effective non-UTC external override.
4. Keep manually-created OAuth, Audit, and Migration mappers free of application-global timezone mutation or legacy timezone configuration.
5. Make PostgreSQL application sessions explicitly request UTC for every new connection, rather than relying only on the JVM default inherited when the connection is created.
6. Preserve `Instant` + PostgreSQL `timestamptz` as the absolute timestamp model.
7. Preserve OAuth security modules, Audit JSON structure, Migration YAML behavior, and existing persisted data.

## Non-goals

- Dynamically changing the server timezone while the process is running.
- Supporting an operator-selectable server timezone.
- Masking a bad deployment by silently rewriting its timezone.
- Introducing Spring Cloud refresh or runtime configuration rebinding.
- Replacing purpose-specific OAuth serialization modules or polymorphic validation.
- Changing API or persisted timestamp schemas.

## Runtime Invariant

### Deployment defaults

Taskmigo's server container continues to declare UTC operational defaults:

```text
TZ=UTC
-Duser.timezone=UTC
```

These are deployment defaults, not the correctness boundary by themselves.

### Startup validation

`TaskmigoRuntime` becomes a validator rather than a mutator.

Production code checks:

```java
ZoneId.systemDefault().normalized().equals(ZoneOffset.UTC)
```

If the effective JVM timezone is not UTC, startup fails with an error that includes the actual timezone.

The validator must not call:

- `TimeZone.setDefault(...)`
- `TimeZone.getDefault()`
- `System.setProperty("user.timezone", ...)`

The existing highest-precedence `ApplicationStartingEvent` listener remains the enforcement point so an invalid runtime fails before the Spring Environment or ApplicationContext is created.

The validator may expose a package-private pure helper that accepts a `ZoneId` so non-UTC behavior can be tested without mutating global JVM state.

## Runtime Mutation After Startup

Spring Boot configuration and operating-system environment variables are not continuously rebound by core Spring Boot. Taskmigo therefore validates the effective configuration at startup.

The JVM default timezone is different: third-party code could still mutate that global state through legacy JDK APIs after startup. Taskmigo does not attempt to continuously rewrite or police global JVM state.

Instead, Taskmigo minimizes the impact of any later external mutation by making important boundaries independent of the JVM default:

1. Domain/application absolute timestamps use `Instant`.
2. Worker clocks use `Clock.systemUTC()`.
3. Hibernate JDBC timezone remains explicitly UTC.
4. PostgreSQL connections explicitly request a UTC session timezone.
5. Spring-managed Jackson explicitly receives UTC from Boot configuration and a non-UTC external override is rejected at startup.
6. Production code keeps Qodana's legacy date/time inspection enabled with no feature-specific exclusions.

Dynamic runtime code that intentionally mutates the JVM default remains unsupported. The design prevents normal Taskmigo code and operator configuration from silently creating mixed-timezone behavior.

## Jackson Contract

### Why the builder-level TimeZone policy is removed

Jackson 3 currently defaults mapper timezone to UTC, but `MapperBuilder.defaultTimeZone(...)` still requires `java.util.TimeZone`.

Taskmigo's absolute application timestamps are `Instant`, whose meaning does not depend on a mapper's local timezone. Therefore Taskmigo does not need to force a legacy mapper timezone onto every manually-created mapper.

The shared `TaskmigoJackson.configure(...)` builder utility is removed.

Manual mappers continue to own only their purpose-specific configuration:

- OAuth keeps Spring Security modules and the restricted `UserSessionPrincipal` polymorphic validator.
- Audit keeps its dedicated JSON mapper.
- Migration keeps its dedicated YAML mapper.

Their existing behavior tests remain compatibility gates.

### Spring Boot-managed Jackson

Web restores an explicit application default:

```yaml
spring:
  jackson:
    time-zone: UTC
```

External Spring configuration can override application YAML, so the property alone is not the invariant.

`:modules:foundation:jackson` remains as the shared Spring Boot Jackson policy module, but its responsibility changes from mutating a mapper builder to validating effective configuration.

A listener registered for `ApplicationEnvironmentPreparedEvent` reads the effective:

```text
spring.jackson.time-zone
```

Behavior:

- property absent: accepted; Jackson 3's default remains UTC,
- property present and UTC-equivalent: accepted,
- property present and non-UTC: fail startup with a clear error.

UTC equivalence is determined with `ZoneId`/`ZoneOffset`, not `java.util.TimeZone`.

This validation runs before the ApplicationContext creates the Boot-managed `JsonMapper`.

The previous `JsonMapperBuilderCustomizer`, `TaskmigoJacksonAutoConfiguration`, and builder-level timezone enforcement are removed.

## PostgreSQL and Hibernate

Hibernate retains:

```yaml
hibernate:
  jdbc:
    time_zone: UTC
```

That setting protects JDBC temporal conversion but does not by itself define the PostgreSQL session timezone.

Each datasource connection must also request a PostgreSQL UTC session through the JDBC driver's connection properties, using PostgreSQL's startup `options` property rather than production SQL statements.

Conceptually:

```yaml
spring:
  datasource:
    hikari:
      data-source-properties:
        options: "-c TimeZone=UTC"
```

This preserves the repository rule against raw/native production SQL and ensures connections created after startup do not derive their PostgreSQL session timezone from a later-mutated JVM default.

The existing integration assertion:

```sql
show time zone
```

remains a test-only boundary check and must continue to resolve to UTC.

## Module Boundaries

### foundation:core

`foundation:core` owns the framework-neutral JVM runtime validator.

It remains free of Spring and Jackson dependencies.

### foundation:spring

`foundation:spring` owns the Spring Boot starting listener that invokes the runtime validator.

It does not gain a Jackson dependency.

### foundation:jackson

`foundation:jackson` owns only the Spring Boot Jackson configuration invariant.

It may depend on Spring Boot APIs required to observe `ApplicationEnvironmentPreparedEvent`.

It must not depend on bounded-context modules such as Authorization, Audit, Database, Identity, Query, Web, Worker, or Migration.

It no longer exposes a mapper-builder utility and does not depend on Jackson databind merely to set a timezone.

### Consumers

- Web depends on `foundation:jackson` because it owns the Spring-managed HTTP JSON mapper.
- Audit no longer depends on `foundation:jackson` only for timezone configuration.
- Migration no longer depends on `foundation:jackson` only for YAML timezone configuration.
- OAuth's dedicated mapper no longer invokes a shared builder timezone policy.

## Testing

### JVM runtime validator

Tests cover the pure validation behavior without mutating JVM global state:

- `ZoneOffset.UTC` is accepted.
- `Asia/Ho_Chi_Minh` is rejected with a clear startup-invariant message.

A Spring listener test verifies the normal UTC bootstrap path remains valid.

### Jackson configuration validation

A Spring Boot test starts a minimal application with:

```text
spring.jackson.time-zone=Asia/Ho_Chi_Minh
```

and expects startup to fail before the ApplicationContext is created.

A corresponding UTC configuration starts successfully.

The tests must not call legacy `TimeZone` APIs.

### PostgreSQL boundary

Integration coverage verifies:

1. the datasource connection property explicitly contains the UTC PostgreSQL startup option, and
2. an application-owned PostgreSQL session reports an effective UTC timezone.

### Consumer compatibility

Existing tests remain green:

- OAuth principal JSON round-trip and subtype restrictions,
- Audit privacy scrub JSON structure,
- Migration YAML credentials/placeholders and browser-auth-disabled behavior,
- exact retained `Instant` preservation.

### Static quality gates

Qodana's `UseOfObsoleteDateTimeApi` inspection remains enabled with no Taskmigo UTC feature exclusions.

No production class added or changed by this feature may use `java.util.TimeZone`.

## Failure Semantics

A deployment such as:

```text
-Duser.timezone=Asia/Ho_Chi_Minh
```

must fail during the earliest Spring Boot startup event.

An effective configuration such as:

```text
SPRING_JACKSON_TIME_ZONE=Asia/Ho_Chi_Minh
```

must fail during environment preparation before mapper creation.

Taskmigo does not silently rewrite either value.

## Compatibility and Risk

No external API or database schema changes are intended.

The main operational change is deliberate fail-fast behavior: a deployment that previously started with a non-UTC JVM or non-UTC Spring Jackson timezone will now refuse to start.

The PostgreSQL JDBC startup option changes only the timezone of application sessions; persisted `timestamptz` instants remain unchanged.

Removing builder-level timezone configuration is safe for Taskmigo's absolute temporal model because application timestamps are `Instant`, while existing consumer serialization tests protect purpose-specific structures.

## Success Criteria

- Taskmigo production code introduced by this feature contains no `java.util.TimeZone` usage.
- A non-UTC effective JVM timezone fails startup instead of being rewritten.
- A non-UTC effective `spring.jackson.time-zone` fails startup.
- Web declares UTC explicitly for Spring Boot-managed Jackson.
- OAuth, Audit, and Migration retain their existing serialization behavior without a shared legacy timezone builder policy.
- PostgreSQL connections explicitly request UTC independently of the JVM default.
- Hibernate JDBC conversion remains UTC.
- No feature-specific Qodana exclusion is required for legacy date/time APIs.
- Existing runtime, serialization, persistence, and integration tests pass.
- All required CI checks pass.
