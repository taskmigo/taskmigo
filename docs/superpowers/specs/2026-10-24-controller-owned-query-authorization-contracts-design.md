# Controller-Owned Query and Authorization Contracts

## Intent

Redesign query and Object Authorization routing so HTTP controllers declare their contracts explicitly, while non-HTTP callers such as workers can authorize the same operation for an explicit user without depending on Spring MVC.

## Goals

- Web DX: an endpoint declares `@QueryContract(...)` and/or `@AuthorizationContract(...)`; Object Authorization is injected as an `ObjectAuthorizationPredicate` argument.
- Worker DX: a worker can authorize `principal + request + authorization schema + runtime field context` through one framework-free application API call.
- Query and Authorization contracts are separate types even when they reuse the same field primitives.
- Query metadata remains sufficient for `x-filter-by` / `x-sort-by` OpenAPI generation.
- Authorization contract selection is controlled by the controller, not inferred from OpenAPI operation IDs, HTTP method names, or feature use cases.
- Both client query and Object Authorization stay database-pushed before pagination.

## Non-goals

- Preserve `@QueryOperation` compatibility.
- Preserve operation-string schema lookup.
- Move HTTP/security state into feature services.
- Serialize authorization predicates into jobs.

## Contract model

`@QueryContract` references a typed `QuerySchemaView` contract class. `@AuthorizationContract` references a typed `ObjectAuthorizationSchema<?>` contract class. Concrete persistence schemas implement those public contract interfaces; Web resolves the exact bean by contract type.

`operationId` remains documentation/client-generation identity only.

## Web usage

```java
@GetMapping("/tickets")
@QueryContract(TicketQuery.List.class)
@AuthorizationContract(TicketAuthorization.List.class)
ResponseEntity<?> list(
    ...,
    ObjectAuthorizationPredicate<TicketInfo> authorization
) {
    return ... tickets.list(..., query.toRequest(), authorization);
}
```

The predicate argument is server-owned and never exposed as an OpenAPI request parameter.

## Worker usage

```java
ObjectAuthorizationPredicate<TicketInfo> authorization = objectAuthorization.authorize(
    userX,
    request,
    ticketAuthorization,
    ticketFields.forTicket(ticketId)
);

tickets.update(ticketId, patch, authorization);
```

The worker re-authorizes at execution time. Jobs carry actor identity, not a previously compiled predicate.

## Ownership

- Controller: route, OpenAPI identity, query contract declaration, authorization contract declaration.
- Query feature: raw query compilation and runtime query semantics.
- Authorization module: request authorization, policy evaluation, object predicate creation.
- Persistence: binds query and authorization constraints into one database query.

## Fail-closed rules

- Missing or ambiguous declared contract bean fails immediately.
- Protected predicate parameters require `@AuthorizationContract`.
- Predicate/schema identity mismatch fails during binding.
- Policy field references outside the selected Authorization contract fail validation.
- Missing request authorization context or denied request authorization never yields an object predicate.
