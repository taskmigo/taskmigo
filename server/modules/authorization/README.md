# API ACL POC

This module proves API-only ACL with two independent policy kinds: `acl/request` and `acl/response`.

System policies are built into `AclPolicyRegistry` and are not exposed by the management API. Custom policies can be managed at `/api/v0/organizations/{organizationId}/acl-policies/{name}` during the POC.

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

## POC limitation

Custom policy definitions are stored as compiled ASTs in the application process. Persistence, revision-based cache invalidation, and multi-instance synchronization are intentionally deferred; application resource rows are never cached or filtered in memory for ACL.
