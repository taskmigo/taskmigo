# Authorization

Use this module to decide whether a user may perform a request and to restrict which objects that user may see.

## Add the module

```kotlin
dependencies {
  implementation(project(":modules:authorization"))
}
```

## Authorize a request

Inject `RequestAuthorization`, then pass the authenticated user and current request.

```java
import io.taskmigo.authorization.request.AuthorizationPrincipal;
import io.taskmigo.authorization.request.AuthorizationRequest;
import io.taskmigo.authorization.request.RequestAuthorization;
import io.taskmigo.authorization.request.RequestAuthorizationResult;
import java.util.Map;

RequestAuthorizationResult result = authorization.authorize(
  new AuthorizationPrincipal(userId, username),
  new AuthorizationRequest(
    "GET",
    "/api/v0/projects/" + projectId,
    Map.of("projectId", projectId.toString())
  )
);
```

Check the result before continuing:

```java
if (!result.granted()) {
  // Reject the request.
  return;
}
```

The result also contains an `AuthorizationContext` for the current operation:

```java
AuthorizationContext context = result.context();
```

Reuse this context when Object Authorization is needed later in the same request. Do not cache or reuse it across unrelated requests.

## Authorize objects

Use `ObjectAuthorization` when a list or query must return only objects visible to the current user.

Inject `ObjectAuthorization` and the schema for the resource:

```java
private final ObjectAuthorization objectAuthorization;
private final ObjectAuthorizationSchema<ProjectInfo> projectSchema;
```

Then authorize the object query with the same `AuthorizationContext` returned by Request Authorization:

```java
ObjectAuthorizationPredicate<ProjectInfo> authorizationPredicate = objectAuthorization.authorize(
  context,
  projectSchema
);
```

Pass the predicate to the resource service together with the normal query filters:

```java
return projects.list(
  pagination,
  filter.predicate(),
  authorizationPredicate
);
```

Object Authorization should be applied in the database before pagination.

## Statements

Authorization rules are defined as Statements.

A Request Statement controls whether the request itself is allowed:

```yaml
name: read-projects
effect: allow
scope: request
target:
  api:
    method: GET
    path: /api/v0/projects.*
policy: |
  return request.method == "GET";
```

An Object Statement controls which objects are visible:

```yaml
name: read-own-projects
effect: allow
scope: object
target:
  api:
    method: GET
    path: /api/v0/projects
policy: |
  return object.ownerId == principal.id;
```

Policies can use these roots:

| Root        | Request Statement | Object Statement |
| ----------- | ----------------- | ---------------- |
| `principal` | Yes               | Yes              |
| `request`   | Yes               | Yes              |
| `object`    | No                | Yes              |

When creating or updating a Statement, validate it before persistence:

```java
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

## Typical flow

```text
Authenticated user + HTTP request
        |
        v
RequestAuthorization
        |
        +--> denied -> reject request
        |
        v
AuthorizationContext
        |
        v
ObjectAuthorization (when querying resources)
        |
        v
ObjectAuthorizationPredicate
        |
        v
Database query
```

For advanced behavior and constraints, see the [Authorization specification](https://github.com/taskmigo/specification/tree/next/specification/002.%20Authorization).
