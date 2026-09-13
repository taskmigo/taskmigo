# Authorization

The `authorization` module owns Taskmigo's transport-neutral authorization semantics and public contracts. It evaluates request-scoped policies, derives persistence-neutral object predicates, validates authorization Statements, and exposes the ports required to resolve effective authorization state.

The authoritative behavior is defined by the [Authorization specification](https://github.com/taskmigo/specification/tree/next/specification/002.%20Authorization).

## Overview

The intended authorization flow is:

```text
Spring Security / transport adapter
        |
        v
RequestAuthorization
        |
        +--> RequestAuthorizationResult.granted()
        |
        v
AuthorizationContext
        |
        v
ObjectAuthorization + ObjectAuthorizationSchema<Q>
        |
        v
ObjectAuthorizationPredicate<Q>
        |
        v
resource-owned persistence binder
        |
        v
database filtering before pagination
```

Important invariants:

- Request authorization is default-deny.
- A matching `DENY` overrides matching `ALLOW` Statements.
- Request and object authorization for one operation reuse the same `AuthorizationContext`.
- The database remains authoritative for effective Statement state on every operation.
- Object authorization stays persistence-neutral until the resource-owning module binds the predicate to its database query.
- Authorization failures fail closed.

## Add the module

From another server module:

```kotlin
dependencies {
    implementation(project(":modules:authorization"))
}
```

The module is a Spring Modulith application module. Its public named interfaces are:

| Named interface | Package | Purpose |
| --- | --- | --- |
| `request` | `io.taskmigo.authorization.request` | Typed request authorization and operation context |
| `object` | `io.taskmigo.authorization.object` | Object schemas and persistence-neutral predicates |
| `role` | `io.taskmigo.authorization.role` | Authorization-owned role contracts |
| `statement` | `io.taskmigo.authorization.statement` | Statement model and policy validation |
| `spi` | `io.taskmigo.authorization.spi` | Persistence-facing authorization ports |

Consumers should depend on these public contracts instead of implementation classes in the module.

## Request authorization

Inject `RequestAuthorization`, build typed principal/request inputs, and authorize the operation:

```java
import io.taskmigo.authorization.request.AuthorizationPrincipal;
import io.taskmigo.authorization.request.AuthorizationRequest;
import io.taskmigo.authorization.request.RequestAuthorization;
import io.taskmigo.authorization.request.RequestAuthorizationResult;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
final class ProjectAuthorization {

    private final RequestAuthorization authorization;

    ProjectAuthorization(RequestAuthorization authorization) {
        this.authorization = authorization;
    }

    RequestAuthorizationResult authorize(UUID userId, String username, UUID projectId) {
        AuthorizationPrincipal principal = new AuthorizationPrincipal(userId, username);
        AuthorizationRequest request = new AuthorizationRequest(
            "GET",
            "/api/v0/projects/" + projectId,
            Map.of("projectId", projectId.toString())
        );

        return this.authorization.authorize(principal, request);
    }
}
```

`AuthorizationRequest.path()` must be the request path without a query string. `pathVariables` contains values already available at the transport boundary; Request Authorization must not load business resources to construct policy inputs.

Use `RequestAuthorizationResult.granted()` as the request decision. The accompanying `AuthorizationContext` is the opaque handle for the exact authorization state used to make that decision.

```java
RequestAuthorizationResult result = authorization.authorize(principal, request);

if (!result.granted()) {
    // Reject the operation.
    return;
}

AuthorizationContext context = result.context();
```

Do not inspect, persist, cache, or reuse an `AuthorizationContext` for an unrelated later request. It is operation-scoped and intentionally hides the internal authorization snapshot.

### Web integration

The `authorization` module does not own Spring Security or MVC adaptation. `apps/web` is responsible for:

- adapting the authenticated HTTP request into `AuthorizationPrincipal` and `AuthorizationRequest`;
- invoking `RequestAuthorization` through the Spring Security authorization boundary;
- retaining the granted `AuthorizationContext` for the current HTTP request;
- exposing that same context to handlers that need Object Authorization.

A controller may therefore receive the current context and reuse it without resolving effective Statements again:

```java
@GetMapping("/projects")
ResponseEntity<?> list(AuthorizationContext context) {
    // Use the same context for Object Authorization.
    // ...
}
```

## Object authorization

Object Authorization turns object-scoped Statements into an opaque logical predicate that the resource-owning module can translate to its persistence model.

### 1. Define the resource schema

An `ObjectAuthorizationSchema<Q>` allow-lists the API-visible object paths that policies may reference. It is deliberately independent of JPA entities and database column names.

```java
import io.taskmigo.authorization.object.ObjectAuthorizationField;
import io.taskmigo.authorization.object.ObjectAuthorizationPath;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

@Configuration(proxyBeanMethods = false)
class ProjectAuthorizationSchemaConfiguration {

    @Bean
    ObjectAuthorizationSchema<ProjectInfo> projectObjectAuthorizationSchema() {
        List<ObjectAuthorizationField> fields = List.of(
            new ObjectAuthorizationField(
                ObjectAuthorizationPath.parse("id"),
                ResolvableType.forClass(UUID.class),
                false
            ),
            new ObjectAuthorizationField(
                ObjectAuthorizationPath.parse("name"),
                ResolvableType.forClass(String.class),
                false
            ),
            new ObjectAuthorizationField(
                ObjectAuthorizationPath.parse("owner.id"),
                ResolvableType.forClass(UUID.class),
                false
            )
        );

        return new ObjectAuthorizationSchema<>() {
            @Override
            public Class<ProjectInfo> objectType() {
                return ProjectInfo.class;
            }

            @Override
            public Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath path) {
                return fields.stream().filter(field -> field.path().equals(path)).findFirst();
            }

            @Override
            public Collection<ObjectAuthorizationField> fields() {
                return fields;
            }
        };
    }
}
```

The three-argument `ObjectAuthorizationField` constructor enables the standard scalar operators (`EQ`, `NE`, `GT`, `GE`, `LT`, `LE`, and `IN`). Use the four-argument constructor when a field must expose a smaller operator set.

The schema identity includes the object type, paths, types, nullability, and supported operators. Changing those properties changes the compatibility identity used by authorization artifacts.

### 2. Register the API route

Register each governed resource route with the schema registry:

```java
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.ObjectAuthorizationSchemaRegistration;
import io.taskmigo.authorization.object.ObjectAuthorizationSchemaRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AuthorizationRouteConfiguration {

    @Bean
    ObjectAuthorizationSchemaRegistry objectAuthorizationSchemaRegistry(
        ObjectAuthorizationSchema<ProjectInfo> projects
    ) {
        return ObjectAuthorizationSchemaRegistry.of(
            List.of(
                new ObjectAuthorizationSchemaRegistration(
                    "GET",
                    "/api/v0/projects",
                    projects
                )
            )
        );
    }
}
```

Route registration is used when validating an Object Statement: every registered schema that can be governed by the Statement target must accept the referenced object paths and operators.

### 3. Derive the predicate

Reuse the `AuthorizationContext` from Request Authorization:

```java
import io.taskmigo.authorization.object.ObjectAuthorization;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.request.AuthorizationContext;

ObjectAuthorizationPredicate<ProjectInfo> authorizationPredicate =
    objectAuthorization.authorize(context, projectSchema);
```

`ObjectAuthorizationPredicate<Q>` is intentionally opaque. Consumers may inspect only the constant identities:

```java
if (authorizationPredicate.isAlwaysFalse()) {
    // No object is visible.
}

if (authorizationPredicate.isAlwaysTrue()) {
    // Authorization itself does not further restrict this query.
}
```

Do not depend on the predicate's internal semantic representation.

### 4. Bind it in the resource-owning module

The module that owns persistence for `Q` must own the trusted mapping from authorization paths to database expressions. For JPA, that typically means a resource-specific binder that converts `ObjectAuthorizationPredicate<Q>` into a Spring Data predicate/specification for the resource entity.

Conceptually:

```java
ObjectAuthorizationPredicate<ProjectInfo> authorizationPredicate =
    objectAuthorization.authorize(context, projectSchema);

return projectRepository.findAll(
    queryBinder.bind(userFilter),
    authorizationBinder.bind(authorizationPredicate),
    pageable
);
```

The exact binder API belongs to the resource-owning module, not to core Authorization.

Object Authorization must be applied in the database before pagination. Do not load unrestricted rows and filter them in JVM memory.

## Statements

A Statement contains:

```yaml
name: read-own-projects
description: Allow users to see projects they own
effect: allow
scope: object
target:
  api:
    method: GET
    path: /api/v0/projects
policy: |
  return object.owner.id == principal.id;
```

The canonical fields are:

- `name`
- optional `description`
- `effect`: `ALLOW` or `DENY`
- `scope`: `REQUEST` or `OBJECT`
- `target.api.method`: exact HTTP method or `*`
- `target.api.path`: full-match regular expression
- non-blank Language `PROGRAM` policy

Policy roots are:

| Root | Request scope | Object scope |
| --- | --- | --- |
| `principal` | Available | Available |
| `request` | Available | Available |
| `object` | Not available | Symbolic, schema-defined |

Minimum principal/request paths are `principal.id`, `principal.username`, `request.method`, `request.path`, and `request.pathVariables`.

Authorization does not expose repositories, entities, an application context, arbitrary host objects, or a privileged business-resource-loading function to policies.

### Validate before persistence

Use `StatementPolicyValidator` when accepting or changing a Statement definition:

```java
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementDefinition;
import io.taskmigo.authorization.statement.StatementPolicyValidator;

StatementDefinition definition = validator.validate(
    "read-projects",
    "Allow project reads",
    Effect.ALLOW,
    Scope.REQUEST,
    "GET",
    "/api/v0/projects.*",
    "return request.method == \"GET\";"
);
```

The validator normalizes and validates the Statement before persistence. Request policies are compiled against the Request Authorization environment. Object policies are also checked for queryability against every applicable registered `ObjectAuthorizationSchema`.

A policy result of `true` applies the Statement's effect; `false` means that Statement does not match.

Request decision semantics are:

```text
DENY if any target-matching DENY Statement evaluates true
ELSE ALLOW if any target-matching ALLOW Statement evaluates true
ELSE DENY
```

Object visibility composes equivalent logical semantics:

```text
ANY(ALLOW predicates) AND NOT ANY(DENY predicates)
```

Runtime evaluation, partial evaluation, queryability, and persistence-translation failures must not grant access.

## Effective Statement persistence SPI

The authorization module does not own User/Group/Role persistence. A persistence-owning module supplies `EffectiveStatementResolver`:

```java
import io.taskmigo.authorization.spi.EffectiveStatement;
import io.taskmigo.authorization.spi.EffectiveStatementResolver;
import java.util.List;
import java.util.UUID;

final class DatabaseEffectiveStatementResolver implements EffectiveStatementResolver {

    @Override
    public List<EffectiveStatement> resolve(UUID userId) {
        // Resolve the user's direct and inherited effective Statements
        // from authoritative persistence state for this operation.
        // Deduplicate before returning.
        throw new UnsupportedOperationException("example");
    }
}
```

Implementations must preserve these properties:

- resolve from authoritative persisted state for every authorization operation;
- include direct and inherited User/Group/Role/Statement semantics;
- return each effective Statement once;
- use bounded database round trips and avoid N+1 behavior;
- do not make correctness depend on a cross-request authorization-state cache.

Derived compiled Language artifacts may be reused only when their Statement/language/schema/profile identity is compatible; they must never replace the required database resolution.

## Role contracts

The `role` named interface contains authorization-owned role contracts that higher-level resource modules may consume without coupling to role persistence.

`RoleAccess` exposes:

```java
void requireRoles(Collection<UUID> roleIds);
List<RoleInfo> effectiveRoles(Collection<UUID> roleIds);
```

Use `requireRoles` when another module needs to validate referenced role IDs. Use `effectiveRoles` when it needs the supplied roles plus inherited descendant roles in deterministic order.

Persistence and CRUD for roles remain outside this module's core authorization semantics.

## Module boundaries

Keep the following ownership rules when integrating the module:

- `authorization` owns policy semantics, request decisions, object schemas/predicates, and Authorization-specific Language integration.
- `language` owns parsing, typing, evaluation, and partial evaluation of the policy language.
- resource-owning modules own their `ObjectAuthorizationSchema<Q>` and persistence binder.
- `apps/web` owns Spring Security, MVC, HTTP DTOs, and request-context adaptation.
- persistence-owning modules implement authorization SPIs such as `EffectiveStatementResolver`.

The `authorization` module must not depend on `identity`, `query`, `apps/web`, or a resource-specific persistence model for core authorization behavior.

## Common mistakes

Avoid these integration patterns:

- constructing Language root maps in callers instead of using typed `AuthorizationPrincipal` and `AuthorizationRequest`;
- re-resolving effective Statements for Object Authorization after Request Authorization already produced a context;
- caching or reusing `AuthorizationContext` across unrelated operations;
- exposing JPA entities or repository APIs through `ObjectAuthorizationSchema`;
- depending on the internal representation of `ObjectAuthorizationPredicate`;
- applying Object Authorization after pagination or by unrestricted in-memory row filtering;
- allowing a persistence-binding failure to fall back to an unrestricted query;
- loading business resources during Request Authorization to make a policy evaluable;
- treating compiled-policy caching as the source of truth for authorization state.

## Verification

From `server/`:

```bash
./gradlew --no-daemon :modules:authorization:test
```

For the repository-level checks described by `CONTRIBUTING.md`:

```bash
# repository root
npm run format:check

# server/
./gradlew --no-daemon build
```
