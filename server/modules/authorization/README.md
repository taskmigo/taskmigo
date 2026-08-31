# API ACL POC

This module proves API-only ACL with two independent policy kinds: `acl/request` and `acl/response`.

System policies are immutable guardrails persisted in PostgreSQL with `origin=SYSTEM`. Their desired state is declared as YAML under the bootstrap application's `system/acl` resources and reconciled during installation bootstrap. Users cannot update, delete, disable, or override them through the policy management API.

Custom policies are organization-scoped and persisted with `origin=CUSTOM` as validated source DSL in `acl_policies`. There is intentionally no ACL cache in this POC: every authenticated API request loads a fresh organization policy snapshot from the database. The request ACL and response ACL reuse that same request-scoped snapshot. This keeps multiple application instances consistent without Redis, Kafka, or process-local invalidation.

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

## System policy bootstrap

System policy files use the same restricted DSL plus `metadata.name` for stable identity. For example:

```yaml
kind: acl/request
metadata:
  name: Chỉ có project manager mới có thể tạo project
spec:
  target:
    methods: [POST]
    path: /api/v0/organizations/*/projects
  rules:
    project-manager:
      effect: allow
      when:
        eq: [principal.role, project-manager]
```

Bootstrap validates the complete YAML set before reconciling it. New definitions are inserted, changed definitions are updated, and stale system policies are deleted. An invalid system policy fails bootstrap instead of starting the installation with incomplete security guardrails.

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

`acl_policies` stores both global system policies and organization-scoped custom policies. System rows have no organization id; custom rows must have one. Runtime reads both origins from PostgreSQL, so the database is the single runtime source of truth.

The runtime flow is:

```text
Bootstrap YAML -> validate -> reconcile SYSTEM policies -> PostgreSQL
                                                        |
HTTP request -> resolve organization and principal -----+
  -> SELECT system ACL plus organization custom ACL policies
  -> validate/compile definitions into a request-scoped policy snapshot
  -> evaluate request ACL
  -> reuse the same snapshot for response planning
  -> translate object predicates to JPA Criteria
  -> PostgreSQL filters business rows
  -> apply response field selection
```

Because PostgreSQL is the runtime source of truth and no policy cache exists, a policy committed by bootstrap or one application instance is visible to subsequent requests handled by every instance without distributed cache invalidation.
