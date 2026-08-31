# API ACL POC

Taskmigo authorization has two independent layers:

1. **ACL policies** are mandatory API guardrails.
2. **Statements and Roles** describe reusable business capabilities assigned to principals.

System resources are reconciled by the bootstrap application and persisted in PostgreSQL. Runtime applications read PostgreSQL as the source of truth.

## ACL policies

System policies are immutable guardrails with `origin=SYSTEM`. Custom policies are organization-scoped with `origin=CUSTOM`. Roles do not override system policies.

Custom policies keep the canonical ACL DSL used by the management API:

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

System policy YAML under `system/acl` uses the same canonical policy DSL plus `metadata.name` as its stable identity. Bootstrap validates the complete desired set before reconciliation.

## Statements

Statement keys are **data**, not Java constants. Runtime code does not know built-in Statement names. Adding, renaming, or removing a built-in Statement is a YAML/bootstrap concern as long as Roles reference valid keys.

Built-in Statements use a compact bootstrap-only authoring DSL under `system/access/statements`:

```yaml
key: project.create
name: Create projects
description: Allows creating a Project inside the principal Organization.
request:
  methods: POST
  path: /api/v0/organizations/*/projects
  when:
    eq: [request.organizationId, principal.organizationId]
```

The compact fields are:

- `key`: stable Statement key.
- `name`: optional display name; defaults to `key`.
- `description`: optional display description.
- exactly one of `request` or `response`.
- `methods`: one method or a list of methods.
- `path`: API target glob.
- `when`: restricted ACL expression.
- `effect`: optional `allow` or `deny`; defaults to `allow`.
- `fields`: response-only field allow-list; accepts one field or a list.

For example, a response Statement can be written as:

```yaml
key: project.summary.read
response:
  methods: GET
  path: /api/v0/projects
  when:
    relation:
      name: projectMember
      principal: principal.id
      object: object.id
  fields: [id, name]
```

Bootstrap normalizes this compact representation into the canonical `acl/statement` AST before validation and persistence. The ACL compiler therefore has one runtime representation while YAML authors avoid `kind -> metadata -> spec -> target` nesting.

## Roles

A Role is only a named collection of Statement keys. Built-in Roles use compact YAML under `system/access/roles`:

```yaml
key: project-manager
name: Project Manager
statements: [project.create]
```

`name` and `description` are display metadata. `statements` is the only authorization content. The bootstrap reconciler resolves every referenced key to a persisted Statement ID and fails bootstrap if any reference is unknown.

Custom Roles use the same persisted Role/Statement model and may reference system Statements or Statements owned by the same Organization.

## Evaluation

Request evaluation order is:

```text
authentication
  -> mandatory system/custom ACL policy evaluation
  -> resolve principal Role assignments
  -> resolve effective Statement keys
  -> evaluate matching request Statements
  -> controller
```

Response Statements are converted to the existing ACL response plan. Object predicates remain DB-translatable; for example, project membership uses a JPA Criteria `EXISTS` predicate rather than loading rows and filtering them in memory. Field selection is applied only after authorized rows are returned.

The runtime request keeps one authorization snapshot containing the policy snapshot, visible Statement catalog, and effective Statement keys. There is intentionally no process-local ACL cache in this POC, so committed changes are visible across application instances without distributed cache invalidation.

## Persistence and bootstrap

The relevant tables are:

- `acl_policies`: system and organization custom ACL policies.
- `acl_statements`: system and organization Statements.
- `roles`: system and organization Roles.
- `role_statements`: Role-to-Statement references.
- `user_roles`: organization-level user Role assignments.
- `project_member_roles`: Project membership Role assignments.

Bootstrap reconciliation preserves database IDs for unchanged system resources, updates changed resources, and removes stale bootstrap-owned resources. Invalid YAML or a Role referencing an unknown Statement fails bootstrap instead of starting with an incomplete authorization model.
