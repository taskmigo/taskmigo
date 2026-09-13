# Authorization

Use this module to make authorization decisions for HTTP requests and to restrict which objects a user may see.

## Add the module

```kotlin
dependencies {
  implementation(project(":modules:authorization"))
}
```

## Request authorization

Inject `RequestAuthorization`, then pass the authenticated user and current request.

```java
import io.taskmigo.authorization.request.AuthorizationPrincipal;
import io.taskmigo.authorization.request.AuthorizationRequest;
import io.taskmigo.authorization.request.RequestAuthorization;
import io.taskmigo.authorization.request.RequestAuthorizationResult;
import java.util.Map;
import java.util.UUID;

final class ProjectAuthorization {

  private final RequestAuthorization authorization;

  ProjectAuthorization(RequestAuthorization authorization) {
    this.authorization = authorization;
  }

  RequestAuthorizationResult authorize(UUID userId, String username, UUID projectId) {
    return this.authorization.authorize(
      new AuthorizationPrincipal(userId, username),
      new AuthorizationRequest(
        "GET",
        "/api/v0/projects/" + projectId,
        Map.of("projectId", projectId.toString())
      )
    );
  }
}
```

Check the result before continuing:

```java
RequestAuthorizationResult result = authorization.authorize(principal, request);

if (!result.granted()) {
  // Reject the request.
  return;
}
```

The returned `AuthorizationContext` must be reused for Object Authorization in the same request:

```java
AuthorizationContext context = result.context();
```

Do not cache or reuse that context across unrelated requests.

## Object authorization

Use `ObjectAuthorization` when a list/query must return only objects visible to the current user.

Inject:

```java
private final ObjectAuthorization objectAuthorization;
private final ObjectAuthorizationSchema<ProjectInfo> projectSchema;
```

Then authorize the object query with the same `AuthorizationContext` produced by Request Authorization:

```java
ObjectAuthorizationPredicate<ProjectInfo> authorizationPredicate = objectAuthorization.authorize(
  context,
  projectSchema
);
```

Pass that predicate to the persistence layer together with the normal query filters:

```java
return projects.list(
  pagination,
  filter.predicate(),
  authorizationPredicate
);
```

The resource-owning module is responsible for translating `ObjectAuthorizationPredicate<Q>` into its database query.

Object Authorization must be applied before pagination.

## Adding Object Authorization to a new resource

A resource that supports Object Authorization needs an `ObjectAuthorizationSchema<Q>` describing the fields that policies may reference.

Example:

```java
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
    )
  );

  return new ObjectAuthorizationSchema<>() {
    @Override
    public Class<ProjectInfo> objectType() {
      return ProjectInfo.class;
    }

    @Override
    public Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath path) {
      return fields.stream().filter((field) -> field.path().equals(path)).findFirst();
    }

    @Override
    public Collection<ObjectAuthorizationField> fields() {
      return fields;
    }
  };
}
```

Register the API route that uses the schema:

```java
@Bean
ObjectAuthorizationSchemaRegistry objectAuthorizationSchemaRegistry(
  ObjectAuthorizationSchema<ProjectInfo> projects
) {
  return ObjectAuthorizationSchemaRegistry.of(
    List.of(new ObjectAuthorizationSchemaRegistration("GET", "/api/v0/projects", projects))
  );
}
```

## Statements

Authorization rules are stored as Statements.

Example Request Statement:

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

Example Object Statement:

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

Available policy roots are:

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

For the full authorization semantics and constraints, see the [Authorization specification](https://github.com/taskmigo/specification/tree/next/specification/002.%20Authorization).
