# API ACL POC

This module proves API-only ACL with two independent policy kinds: `acl/request` and `acl/response`.

System policies are built into `AclPolicyRegistry`, are always evaluated as mandatory guardrails, and are not exposed by the management API. Users therefore cannot update, delete, disable, or override them. Custom policies can be managed at `/api/v0/organizations/{organizationId}/acl-policies/{name}` during the POC.

Custom policies are persisted in PostgreSQL as validated source DSL in `acl_policies`. There is intentionally no ACL cache in this POC: every authenticated API request loads a fresh organization policy snapshot from the database. The request ACL and response ACL reuse that same request-scoped snapshot. This keeps multiple application instances consistent without Redis, Kafka, or process-local invalidation.

## Request policy

```json
{
  "kind": "acl/request",
  "spec": {
    "target": {
      "methods": ["PATCH"],
      "path": "/api/v0/projects/**"
    },
    "rules": {
      "deny-example": {
        "effect": "deny",
        "when": {
          "eq": ["principal.type", "blocked"]
        }
      }
    }
  }
}
```

Request ACL executes after authentication and before the controller.

## Response policy

```json
{
  "kind": "acl/response",
  "spec": {
    "target": {
      "methods": ["GET"],
      "path": "/api/v0/projects"
    },
    "rules": {
      "member": {
        "effect": "allow",
        "when": {
          "relation": {
            "name": "projectMember",
            "principal": "principal.id",
            "object": "object.id"
          }
        },
        "fields": {
          "allow": ["id", "name"]
        }
      }
    }
  }
}
```

The response rule is partially evaluated before repository access. `projectMember` is translated by the project module to a JPA Criteria `EXISTS` predicate, so rows are filtered by the database. The field allow-list is applied only after authorized rows are returned.

## Persistence and multi-instance behavior

`acl_policies` stores only custom organization policies. System policies remain versioned with application code and have no mutable database representation.

The runtime flow is:

```text
HTTP request
  -> resolve organization and principal
  -> SELECT organization custom ACL policies
  -> validate/compile definitions into a request-scoped policy snapshot
  -> evaluate request ACL
  -> reuse the same snapshot for response planning
  -> translate object predicates to JPA Criteria
  -> PostgreSQL filters business rows
  -> apply response field selection
```

Because PostgreSQL is the source of truth and no policy cache exists, a policy committed by one instance is visible to subsequent requests handled by every instance without any distributed cache or invalidation mechanism.

## POC limitation

The POC recompiles custom policy definitions when loading the request snapshot. If profiling later shows policy loading or compilation to be a bottleneck, caching can be added behind the same registry contract. Application resource rows are never cached or filtered in memory for ACL.
