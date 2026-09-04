# Software Requirements Specification (IEEE 830)

## JavaScript Authorization Policies and Shared Query Filtering

**Status:** Draft for implementation  
**Baseline branch:** `next`  
**Baseline commit:** `b7570226eb6f7258ed6a3e75a7f8dcab4ae93392`  
**Related:** #37, #54  
**Compatibility:** v0; breaking changes are allowed.

> Requirements are based on the implementation on `next`. Prior discussion and experimental implementations are not baseline behavior.

---

# 1. Purpose and Scope

Replace the current `conditions[]` + restricted SpEL-derived authorization model with JavaScript-authored policies while preserving DB-side Object Authorization.

This SRS covers:

- Statement schema/API migration;
- JavaScript policy compilation;
- Request Authorization;
- request-time resource resolution;
- Object Authorization partial evaluation;
- shared Filter AST / Filter Schema;
- DB-first effective Statement resolution;
- future filtering extension points;
- implementation phases and verification.

## Project-specific terms

| Term          | Meaning                                                                |
| ------------- | ---------------------------------------------------------------------- |
| Policy IR     | Taskmigo-owned compiled representation of JavaScript policy semantics. |
| Filter AST    | Taskmigo-owned representation of database predicates.                  |
| Filter Schema | Resource-specific mapping of filter fields to persistence.             |

References:

- Baseline: https://github.com/taskmigo/taskmigo/tree/b7570226eb6f7258ed6a3e75a7f8dcab4ae93392
- #37
- #54
- RFC 7644 §3.4.2.2 for the future `filterBy` grammar

---

# 2. Current System Baseline (`next`)

Current Statement model:

```text
effect
target.type: REQUEST | OBJECT
target.api.method
target.api.path
conditions: List<String>
```

Persistence uses `statements.target_type` and `statement_conditions`.

`AuthorizationCompiler` parses restricted SpEL and produces a Taskmigo expression tree. `RequestAuthorizationService` evaluates that tree with deny-overrides/default-deny semantics.

`ObjectAuthorizationService` specializes known `principal.*` / `request.*` values, retains `object.*`, composes:

```text
OR(allows) AND NOT OR(denies)
```

and translates the result directly to JPA Criteria before pagination.

`AuthorizationObjectQueryDialect` currently exposes direct queryable object fields.

`EffectiveStatementResolver` currently loads all Groups and Roles into JVM memory and builds hierarchy graphs per resolution. This is implementation debt and SHALL be replaced.

---

# 3. Specific Requirements

## 3.1 Statement Interface

### IF-STMT-001

Canonical Statement model:

```yaml
name: <name>
description: <description>
effect: allow | deny
scope: request | object

target:
  api:
    method: <HTTP method | *>
    path: <API path matcher>

policy: | # optional
  export default ({ request, principal, object }) => {
    return object.organizationId === principal.organizationId;
  };
```

### IF-STMT-002

Breaking change:

```diff
-target.type: request | object
-conditions: string[]
+scope: request | object
+policy: string?
```

### IF-STMT-003

Absent `policy` SHALL mean unconditional match after HTTP target matching.

### IF-STMT-004

The canonical persistence model SHALL store `scope` and optional `policy`. `target_type` and `statement_conditions` SHALL be removed.

## 3.2 JavaScript Policy

### FR-JS-001

A policy SHALL expose a default-exported function returning boolean.

### FR-JS-002

`effect` SHALL remain Statement metadata. Policy SHALL NOT return `allow` or `deny`.

### FR-JS-003

Taskmigo SHALL parse ECMAScript with a maintained parser and compile supported semantics into parser-independent Policy IR.

### FR-JS-004

Supported policy semantics SHALL include:

```text
function/arrow body
const
return
if/else
literals
property access
=== !== < <= > >=
&& || !
representable arithmetic
statically analyzable arrays/objects
membership
selected string predicates
```

### FR-JS-005

Unsupported semantics SHALL fail policy activation. No general-purpose JavaScript runtime fallback is allowed.

## 3.3 Policy IR

### FR-PIR-001

Policy IR SHALL be parser-independent, immutable, and concurrency-safe.

### FR-PIR-002

The same Policy IR SHALL support Request evaluation and Object partial evaluation.

### FR-PIR-003

Policy compilation SHALL occur when an immutable Statement revision becomes active, not once per request.

## 3.4 Authorization Input

### IF-INPUT-001

Policy roots SHALL be:

```text
request
principal
object
```

### IF-INPUT-002

`request` SHALL expose only approved immutable values, including route/path variables where needed.

### IF-INPUT-003

For Request Authorization, `object` SHALL contain explicitly resolved resources.

For Object Authorization, `object.*` SHALL remain symbolic during partial evaluation.

### SEC-INPUT-001

Policy SHALL NOT receive framework internals, JPA entities, repositories, filesystem/network/process access, or arbitrary host APIs.

## 3.5 Request-Time Resources

### FR-RES-001

A Request policy MAY declare:

```js
export function resources({ request, principal }) {
  return {
    project: resource("project", request.path.projectId),
    user: resource("user", request.path.userId),
  };
}
```

### FR-RES-002

`resource(type, key)` SHALL produce a declarative resource descriptor and SHALL NOT directly access persistence.

### FR-RES-003

Protected object identity SHALL be selected explicitly by policy. Taskmigo SHALL NOT infer it from URL identifiers.

### PERF-RES-001

Resource resolution SHALL be bounded, deduplicated, and free of N+1 behavior. Compatible lookups SHALL support batching.

## 3.6 Request Authorization

### FR-REQ-001

For `scope: request`, Taskmigo SHALL evaluate Policy IR before protected business execution.

### FR-REQ-002

Decision semantics:

```text
DENY if any matching deny Statement evaluates true
ELSE ALLOW if any matching allow Statement evaluates true
ELSE DENY
```

### FR-REQ-003

Compilation, resource-resolution, or evaluation failure SHALL fail closed.

## 3.7 Object Authorization

### FR-OBJ-001

For `scope: object`, Taskmigo SHALL partially evaluate Policy IR with `request` and `principal` known and `object.*` unknown.

### FR-OBJ-002

Residual object predicates SHALL be represented as Filter AST.

### FR-OBJ-003

Object Authorization SHALL execute database-side before pagination. No per-row JVM authorization fallback is allowed.

### FR-OBJ-004

Object-dependent policy logic SHALL either be converted to a valid database predicate or fail activation.

### FR-OBJ-005

Object visibility semantics SHALL remain:

```text
ANY(allow filters) AND NOT ANY(deny filters)
```

## 3.8 Filter AST / Filter Schema

### FR-FILTER-001

Filter AST SHALL be independent of JavaScript syntax and external query syntax.

### FR-FILTER-002

Filter AST SHALL support the operators required by Object Authorization and SHALL be extensible for future query filtering.

Target operators:

```text
AND OR NOT
EQ NE GT GE LT LE
IN NIN
CONTAINS
STARTS_WITH
ENDS_WITH
PRESENT
```

### FR-FILTER-003

Filter Schema SHALL own field/type/relationship persistence mappings used to compile Filter AST to JPA Criteria/Specification.

### FR-FILTER-004

Policy/user values SHALL be parameter-bound and SHALL NOT be concatenated into SQL.

## 3.9 Effective Statement Resolution

### PERF-STMT-001

Effective Statement resolution SHALL NOT load all Groups, Roles, or Statements into JVM memory.

### PERF-STMT-002

Resolution SHALL use bounded database queries independent of unrelated authorization graph size and SHALL avoid N+1 behavior.

### PERF-STMT-003

Performance/query-count verification SHALL cover a user with approximately 500 effective Statements targeting the same API.

---

# 4. Non-Functional Requirements

### NFR-SEC-001

Policy source SHALL be treated as untrusted compiler input. Source and compiler complexity SHALL be bounded.

### NFR-PERF-001

ACL predicates SHALL be applied through JPA Criteria/Specification before pagination.

### NFR-PERF-002

No implementation path may fetch unrestricted business rows and apply ACL in JVM memory.

### NFR-FAIL-001

Authorization failures SHALL fail closed.

### NFR-EXT-001

Policy IR and Filter AST public/internal boundaries SHALL remain implementation-independent so later filtering and relationship capabilities can be added without replacing existing callers.

---

# 5. Implementation Phases

Each phase SHALL deliver the named capability completely. A phase SHALL NOT contain partial implementation of a later feature.

## Phase 1 — Statement model migration

Complete the Statement contract migration:

- replace `target.type` with `scope`;
- replace `conditions[]` with optional `policy`;
- update persistence/schema;
- update Statement API request/response models;
- update bootstrap parsing/reconciliation;
- update validation;
- update all affected tests.

**Done:** no production path or test depends on `target.type`, `conditions[]`, or `statement_conditions`.

## Phase 2 — JavaScript Request Authorization

Complete JavaScript-backed Request Authorization:

- ECMAScript parser integration;
- policy module/default-export validation;
- parser-independent Policy IR;
- all JavaScript semantics required by this SRS for Request policies;
- activation-time policy compilation/validation;
- Policy IR evaluator;
- compiler diagnostics and complexity limits;
- Request Authorization fully migrated from restricted SpEL;
- default-deny, deny-overrides, unconditional-policy, and fail-closed behavior;
- remove obsolete SpEL Request Authorization implementation.

**Done:** every supported Request policy is authored in JavaScript and evaluated through Policy IR; Request Authorization has no legacy condition execution path.

## Phase 3 — Request Authorization Resources

Complete persisted-resource support for Request Authorization:

- `resources` named export;
- `resource(type, key)` intrinsic;
- resource descriptor representation;
- resource adapter registry;
- route/path-variable input;
- single and multiple named resources;
- bounded/deduplicated/batched resolution;
- Request policy evaluation against resolved `object` values;
- error semantics and query-count/N+1 verification.

**Done:** Request Authorization can use explicitly selected persisted resources without repository access from policy code and without N+1 behavior.

## Phase 4 — Object Authorization

Complete JavaScript-backed Object Authorization for the supported Filter Schema:

- Policy IR partial evaluation;
- known `request`/`principal` specialization;
- symbolic `object.*` handling;
- Filter AST;
- allow/deny Filter AST composition;
- Filter Schema field/type mappings;
- Filter AST -> JPA Criteria/Specification;
- supported Object policy operators from this SRS;
- ACL-before-pagination;
- non-translatable policy rejection;
- single-object/list semantic equivalence;
- migrate all current built-in Object authorization behavior from legacy conditions;
- remove obsolete Object condition translation path.

**Done:** all supported Object Authorization uses JavaScript Policy IR -> Filter AST -> database predicate, with no legacy condition path and no JVM row filtering.

## Phase 5 — Effective Statement Resolution Performance

Complete DB-first effective Statement resolution:

- remove full Group/Role graph loading from authorization resolution;
- resolve direct and inherited effective Statements with bounded DB queries;
- deduplicate Statements database-side or through bounded result processing;
- prevent N+1 queries;
- preserve current User/Group/Role inheritance semantics;
- verify unrelated graph growth does not increase query count;
- verify approximately 500 matching effective Statements;
- add regression/performance tests.

**Done:** authorization resolution no longer loads unrelated Groups/Roles into JVM memory and satisfies the bounded-query requirements.

## Phase 6 — Advanced Filtering and Relationships — TBD

This phase is intentionally unscheduled. When scheduled, it SHALL be specified as a complete capability before implementation.

Expected scope:

- nested filter fields;
- field-to-field comparison;
- registered joins/subqueries;
- advanced relationship predicates;
- additional Filter AST operators required by concrete product use cases.

## Phase 7 — Rollout, Cleanup, and Documentation

Complete migration and removal of legacy authorization artifacts:

- migrate all built-in Statements to canonical JavaScript policy;
- remove obsolete SpEL authorization code and dependencies;
- remove obsolete condition persistence artifacts;
- remove stale tests and documentation;
- document Statement policy contract;
- document supported JavaScript semantics;
- document Request resources;
- document Object Authorization / Filter Schema contract;
- document DB-first and ACL-before-pagination invariants;
- pass all server quality gates.

**Done:** repository, bootstrap data, tests, and documentation describe only the final authorization architecture.

---

# 6. Deferred Features

These requirements are retained but are not assigned to the active implementation phases above.

## `filterBy` query filtering

Future list APIs MAY expose:

```text
?filterBy=<expression>
```

Target grammar is SCIM-inspired with Taskmigo `in` / `nin` extensions:

```text
eq ne gt ge lt le
co sw ew pr
in nin
and or not
(...)
```

Values use JSON literal syntax and JSON escaping rules.

`filterBy` SHALL compile into the same Filter AST used by Object Authorization and SHALL combine as:

```text
business predicate
AND filterBy predicate
AND authorization predicate
```

before pagination.

## Multi-instance / compiled-policy cache hardening

Database remains the source of truth. Future compiled-policy caching SHALL use immutable Statement revision/version/hash identity and SHALL NOT make correctness depend on distributed cache invalidation.

---

# 7. Verification and Acceptance

| Phase | Required verification                                                                                  |
| ----- | ------------------------------------------------------------------------------------------------------ |
| 1     | Statement API, persistence, bootstrap, schema, and tests use only `scope` + `policy`.                  |
| 2     | Complete supported JavaScript Request Authorization; no SpEL Request execution path.                   |
| 3     | Persisted Request resources work for single/multiple resources with bounded query count and no N+1.    |
| 4     | Complete supported Object Authorization executes DB-side before pagination; no legacy/memory fallback. |
| 5     | Effective Statement resolution is DB-first and bounded, including ~500 matching Statements.            |
| 6     | TBD before implementation.                                                                             |
| 7     | Legacy artifacts removed, built-ins migrated, docs current, quality gates pass.                        |

The SRS is satisfied when all scheduled phases and their acceptance criteria are complete.
