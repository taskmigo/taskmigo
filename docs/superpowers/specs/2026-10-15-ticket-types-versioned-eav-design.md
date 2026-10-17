# Ticket Types with Versioned Typed EAV — Design

**Issue:** #236 — Support custom Ticket Types with versioned data-schema templates  
**Base branch:** `next`  
**Status:** Approved conversational design

## 1. Goals

Implement multiple Ticket Types whose user-defined custom fields are described by immutable published schema versions while preserving historical Ticket interpretation.

The design must provide:

- stable Ticket Type identity;
- versioned, immutable published custom-field schemas;
- historical schema pinning per Ticket;
- typed custom-field validation and persistence;
- database-side filtering, sorting, and supported Object Authorization over custom fields;
- pagination after authorization and filtering;
- no implicit migration or reinterpretation of historical data;
- reuse of Taskmigo Query Filtering and Policy Engine contracts rather than a Ticket-specific query language.

The implementation uses a **generic typed EAV capability** for versioned custom fields. The EAV subsystem must not contain Ticket-specific table names, APIs, or persistence branches.

## 2. Architecture

Taskmigo remains a DDD + Onion + Modular Monolith system.

Introduce:

```text
server/modules/eav
server/modules/ticket
```

Responsibilities:

```text
Ticket module
  owns Ticket Type and Ticket business semantics
  owns the common Ticket field contract
  consumes versioned EAV schemas and values
  publishes Ticket-specific application APIs

EAV module
  owns generic schema definitions and versions
  owns generic field definitions
  owns generic schema instances
  owns typed EAV values
  owns generic validation of field type/constraints
  must not depend on Ticket
```

Dependency direction:

```text
ticket -> eav
ticket -> query
ticket -> authorization

eav -X-> ticket
query -X-> ticket
authorization -X-> ticket
```

The Web app owns HTTP adapters. Feature modules must not own REST controllers.

## 3. Ticket Common Fields

The repository currently has no authoritative existing Ticket model/specification on `next`. For #236, the approved baseline common-field contract is:

```text
id
ticketTypeId
schemaInstanceId
title
status
createdBy
createdAt
updatedAt
version
```

Physical shape:

```text
tickets
  id UUID PK
  ticket_type_id UUID NOT NULL FK
  schema_instance_id UUID NOT NULL UNIQUE FK
  title VARCHAR NOT NULL
  status <Ticket status type> NOT NULL
  created_by UUID NOT NULL
  created_at TIMESTAMPTZ NOT NULL
  updated_at TIMESTAMPTZ NOT NULL
  version BIGINT NOT NULL
```

`version` is internal optimistic-locking metadata and does not need to be exposed as a business field. `schemaInstanceId` is structural historical-schema identity and does not need to be filterable merely because it is persisted.

The exact Ticket status value set is outside the custom-schema mechanism. #236 must not make `status` user-definable merely because custom fields are dynamic.

The following are not system common fields by default:

```text
description
priority
severity
assignee
reporter
dueDate
environment
customer
labels
component
resolution
estimate
```

A Ticket Type may define such concepts as custom fields when appropriate.

### Common-field collision rule

Custom schemas must never redefine common Ticket fields. A draft definition whose public key collides with the Ticket common namespace, such as `status`, `title`, `ticketTypeId`, or `createdAt`, is invalid and cannot be published.

## 4. Generic Versioned EAV Model

The EAV subsystem is generic and uses no Ticket-specific persistence naming.

Recommended relational shape:

```text
eav_schema_definitions
  id UUID PK
  key VARCHAR UNIQUE NOT NULL
  name VARCHAR NOT NULL
  current_version_id UUID NULL
  created_at TIMESTAMPTZ NOT NULL
  updated_at TIMESTAMPTZ NOT NULL

eav_schema_versions
  id UUID PK
  schema_definition_id UUID NOT NULL FK
  version BIGINT NOT NULL
  status DRAFT | PUBLISHED | RETIRED
  published_at TIMESTAMPTZ NULL
  retired_at TIMESTAMPTZ NULL
  UNIQUE(schema_definition_id, version)
  UNIQUE(schema_definition_id, id)

eav_field_definitions
  id UUID PK
  schema_version_id UUID NOT NULL FK
  field_id UUID NOT NULL
  key VARCHAR NOT NULL
  label VARCHAR NOT NULL
  data_type <EAV type> NOT NULL
  required BOOLEAN NOT NULL
  constraints_json JSONB NOT NULL
  UNIQUE(schema_version_id, field_id)
  UNIQUE(schema_version_id, key)
  UNIQUE(schema_version_id, field_id, data_type)

eav_schema_instances
  id UUID PK
  schema_definition_id UUID NOT NULL
  schema_version_id UUID NOT NULL
  UNIQUE(id, schema_version_id)
  UNIQUE(id, schema_definition_id, schema_version_id)
  FK(schema_definition_id, schema_version_id)
    -> eav_schema_versions(schema_definition_id, id)

eav_field_values
  schema_instance_id UUID NOT NULL
  schema_version_id UUID NOT NULL
  field_id UUID NOT NULL
  data_type <EAV type> NOT NULL
  string_value TEXT NULL
  number_value NUMERIC NULL
  boolean_value BOOLEAN NULL
  date_value DATE NULL
  datetime_value TIMESTAMPTZ NULL
  uuid_value UUID NULL
  enum_value VARCHAR NULL
  PRIMARY KEY(schema_instance_id, field_id)
  FK(schema_instance_id, schema_version_id)
    -> eav_schema_instances(id, schema_version_id)
  FK(schema_version_id, field_id, data_type)
    -> eav_field_definitions(schema_version_id, field_id, data_type)
```

Additionally, `eav_schema_definitions.current_version_id`, when non-null, must reference a version that belongs to the same schema definition. This can be enforced with a composite foreign key added after table creation to avoid the circular creation-order problem.

The concrete SQL may use equivalent PostgreSQL constraints/indexes where required by JPA and Flyway, but these referential invariants are mandatory.

### Why `schema_instances`

Do not use a polymorphic `(entity_type, entity_id)` reference. PostgreSQL cannot enforce a clean foreign key from such a polymorphic pair to arbitrary consumer tables.

Instead, a consumer owns a normal FK to an EAV schema instance:

```text
Ticket
  -> EAV Schema Instance
       -> exact Schema Version
       -> typed EAV values
```

This keeps EAV reusable while preserving a database-enforced historical schema/value graph.

The Ticket application transaction also verifies that the schema instance belongs to the schema definition owned by the selected Ticket Type; the EAV subsystem itself does not know what a Ticket Type is.

## 5. Ticket Type to EAV Mapping

Ticket Types remain Ticket domain objects:

```text
ticket_types
  id UUID PK
  key VARCHAR UNIQUE NOT NULL
  name VARCHAR NOT NULL
  status ACTIVE | RETIRED
  schema_definition_id UUID NOT NULL UNIQUE FK
  created_at TIMESTAMPTZ NOT NULL
  updated_at TIMESTAMPTZ NOT NULL
```

The Ticket domain concept “Ticket Template Version” is backed by an `eav_schema_versions` row owned through the Ticket Type's schema definition. Do not duplicate the same schema revision into a second `ticket_template_versions` table unless implementation uncovers a Ticket-only invariant that cannot be represented by the generic schema version.

A Ticket points to one `schema_instance_id`; the instance pins the exact historical schema version.

## 6. Schema and Field Identity

Three identities remain distinct:

```text
eav_field_definitions.id
  physical definition row within one schema version

field_id
  stable logical custom-field identity across compatible revisions

schema_version_id
  immutable historical schema context
```

Presentation-only change:

```text
v1
field_id = F1
key      = severity
label    = Severity
type     = ENUM

v2
field_id = F1
key      = severity
label    = Impact level
type     = ENUM
```

No value migration occurs.

Semantically incompatible change:

```text
v1
field_id = F1
key      = severity
type     = STRING

v2
field_id = F2
key      = severity
type     = ENUM
```

The old value retains v1 STRING semantics forever.

The machine-readable key is stable for a logical field. Changing a key is not treated as a label rename.

When a new draft revision is created from an existing published version, definitions are copied into the draft with new physical definition-row IDs while preserving `field_id` for unchanged logical fields.

## 7. Schema Lifecycle

```text
DRAFT -> PUBLISHED -> RETIRED
```

### DRAFT

- editable;
- not eligible for new schema instances;
- may change fields, labels, constraints, and requiredness;
- receives its version number when created.

### PUBLISHED

- immutable;
- eligible to become the schema definition's current creation version;
- changing schema requires a new draft version.

### RETIRED

- not eligible for new schema instances;
- remains resolvable indefinitely for historical instances;
- retirement never cascades into instances, values, or historical consumers.

### Publish transaction

```text
lock schema definition / owning Ticket Type
-> reload draft
-> verify still DRAFT
-> validate full schema
-> reject duplicate/common-field collisions
-> mark version PUBLISHED
-> update current_version_id
-> commit
```

Concurrent publish attempts for the same definition must serialize. Creation must never validate against one version and persist a different version identity.

Publishing a new version does not rewrite historical instances and does not have to retire the prior published version automatically. Only `current_version_id` chooses the version for new instances.

Retiring the current version clears `current_version_id` in the same transaction unless another version is explicitly selected/published. New creation then fails clearly until a current published version exists.

## 8. Ticket Creation and Mutation

### Create

```text
resolve Ticket Type
-> verify type is eligible for creation
-> resolve current PUBLISHED schema version
-> validate common fields
-> validate supplied custom keys
-> validate required custom fields
-> validate custom types and constraints
-> create EAV schema instance pinned to that version
-> persist Ticket referencing schema instance
-> persist typed EAV values
-> commit
```

The schema version used for validation must be exactly the version persisted by the schema instance.

### Update

```text
load Ticket through authorized persistence predicate
-> load schema instance
-> load its historical schema version
-> validate custom mutation against THAT version
-> update common fields
-> update typed EAV values
-> commit
```

Ordinary Ticket update never resolves the latest template version. Changing a Ticket from one schema version to another is a migration operation and is out of scope for #236.

## 9. Typed EAV Values

Do not store all values as text.

| Type       | Physical value column | Filter operators         | Sortable     |
| ---------- | --------------------- | ------------------------ | ------------ |
| `STRING`   | `string_value`        | EQ, NE, CONTAINS         | yes          |
| `NUMBER`   | `number_value`        | EQ, NE, GT, GTE, LT, LTE | yes          |
| `BOOLEAN`  | `boolean_value`       | EQ, NE                   | no           |
| `DATE`     | `date_value`          | EQ, NE, GT, GTE, LT, LTE | yes          |
| `DATETIME` | `datetime_value`      | EQ, NE, GT, GTE, LT, LTE | yes          |
| `UUID`     | `uuid_value`          | EQ, NE                   | no           |
| `ENUM`     | `enum_value`          | EQ, NE                   | no initially |

Initial constraints:

```text
STRING   minLength, maxLength
NUMBER   minimum, maximum
DATE     minimum, maximum
DATETIME minimum, maximum
ENUM     allowed stable option keys
BOOLEAN  none
UUID     none
```

Unsupported types/constraints are rejected instead of being accepted as opaque values. ENUM persists a stable option key rather than a display label.

### Null/missing semantics

An optional custom field with no value has no EAV value row. Do not persist explicit-null value rows.

```text
field == null  -> no value row exists
field != null  -> a value row exists
```

### Database CHECK constraint

Exactly the typed value column corresponding to `data_type` is populated for every stored value. A NUMBER row, for example, populates only `number_value`.

## 10. Query and Policy Namespace

Persistence is invisible to consumers. Use one flat namespace:

```text
ticketTypeId
status
createdAt
severity
customerTier
dueDate
```

Policy examples:

```text
object.ticketTypeId == '<uuid>'
object.status == 'OPEN'
object.severity == 'HIGH'
object.customerTier >= 3
```

Filter examples:

```text
ticketTypeId == '<uuid>'
status == 'OPEN'
severity == 'HIGH'
customerTier >= 3
```

Sorting:

```text
sortBy=dueDate
sortOrder=asc
```

Do not introduce `fields.*` or `object.fields.*` merely because persistence uses EAV.

## 11. QuerySchema Runtime Fields

Existing `QuerySchema` already separates static and runtime fields. #236 completes that extension point.

```text
QuerySchema
  staticFields()
    id
    ticketTypeId
    title
    status
    createdBy
    createdAt
    updatedAt

  runtimeFields(context)
    severity
    customerTier
    dueDate
```

Runtime metadata comes from server-resolved EAV schema metadata, never client-provided type metadata.

One operation-scoped runtime context must be used consistently by:

```text
FilterByCompiler
ObjectAuthorization
JPA predicate binding
sorting
```

The current Web behavior that resolves schemas without an effective runtime field context is insufficient for #236 and must be extended.

## 12. Generic JPA Persistence Binding

Current JPA Query binding assumes every approved field maps to a static JPA path. Typed EAV requires a generic persistence-binding abstraction.

```text
QueryField
  descriptor
  persistenceBinding

PersistenceBinding
  JpaPathBinding
  DynamicJpaBinding
```

Examples:

```text
status
  -> JpaPathBinding(root.status)

customerTier
  -> DynamicJpaBinding(
       fieldId = ...,
       type = NUMBER,
       valueColumn = number_value
     )
```

The generic JPA/query infrastructure translates dynamic bindings into Criteria API predicates and must not contain Ticket-specific branches.

A predicate such as `customerTier >= 3` executes database-side with semantics equivalent to a correlated `EXISTS` against the typed EAV value relation.

Do not introduce native/raw SQL. Use JPA repositories, Specifications, Criteria API, and approved persistence abstractions.

## 13. Ambiguous Runtime Fields

A flat namespace can be ambiguous when the same key has incompatible logical identities or types in the effective query scope.

```text
Schema A: severity -> F1 / STRING
Schema B: severity -> F2 / ENUM
```

The system fails closed rather than casting or reinterpreting values:

```text
same public key + incompatible logical identity/type
=> runtime field is ambiguous
=> filter/sort/policy reference is rejected
```

Compatible historical versions that preserve the same field identity, key, type, and operator semantics may resolve as one runtime field. No lexical fallback is allowed for numeric/date/enum semantics.

## 14. Sorting

Add generic Query sorting rather than a Ticket-specific language. Runtime field descriptors expose sortability.

For supported fields:

```text
ASC  -> present values first, missing values last
DESC -> present values first, missing values last
```

Always append stable identity ordering:

```text
..., id ASC
```

Sorting occurs in the database before LIMIT/OFFSET. Non-sortable field types are rejected explicitly.

## 15. Object Authorization

Object Authorization and client filtering share the same operation-scoped runtime schema.

```text
normal visibility predicate
AND Object Authorization predicate
AND client filterBy predicate
-> typed DB ordering
-> pagination
```

Never:

```text
pagination
-> fetch candidates
-> in-memory custom filter
-> in-memory object authorization
```

Supported custom-field policy expressions are limited to the Policy Engine subset that can be translated through Query/JPA persistence binding. Unsupported expressions fail closed. #236 adds no in-memory fallback.

Both the FilteredQuery Web argument resolver and ObjectAuthorization Web argument resolver use the same server-owned runtime `QueryFieldContext` for the operation.

## 16. HTTP API

Ticket Type/template management:

```text
POST   /api/v0/ticket-types
GET    /api/v0/ticket-types
GET    /api/v0/ticket-types/{ticketTypeId}
PATCH  /api/v0/ticket-types/{ticketTypeId}

POST   /api/v0/ticket-types/{ticketTypeId}/templates
GET    /api/v0/ticket-types/{ticketTypeId}/templates
GET    /api/v0/ticket-types/{ticketTypeId}/templates/{templateVersionId}
PATCH  /api/v0/ticket-types/{ticketTypeId}/templates/{templateVersionId}
POST   /api/v0/ticket-types/{ticketTypeId}/templates/{templateVersionId}/publish
POST   /api/v0/ticket-types/{ticketTypeId}/templates/{templateVersionId}/retire
```

Ticket surface required by #236:

```text
POST  /api/v0/tickets
GET   /api/v0/tickets
GET   /api/v0/tickets/{ticketId}
PATCH /api/v0/tickets/{ticketId}
```

Do not add Ticket deletion or schema-migration endpoints as part of #236.

Recommended payload shape:

```json
{
  "title": "Login fails",
  "status": "OPEN",
  "ticketTypeId": "...",
  "customFields": {
    "severity": "HIGH",
    "customerTier": 3
  }
}
```

`customFields` is only API payload grouping. It does not create a nested Query/Policy namespace. Read responses should expose enough template/schema-version identity for clients to interpret the returned custom values historically, without exposing EAV persistence internals.

## 17. Authorization Boundaries

Use existing Taskmigo authorization mechanisms.

Request Authorization protects every management and Ticket endpoint.

Object-level list/read/update operations use operation-scoped `@QueryOperation` schemas and `ObjectAuthorizationPredicate`, binding authorization into persistence rather than applying it after entity loading.

Single-object reads/updates resolve their target with an authorized DB predicate rather than unrestricted `findById()` followed by Java authorization.

Ticket Type/template mutations on existing resources use object-level authorization whenever their operation contract exposes those resources as authorization objects.

## 18. Persistence and Migration Rules

All application-major-version-1 schema changes go into:

```text
server/modules/database/src/main/resources/db/migration/V1__schema.sql
```

Do not create `V2__...` or feature-specific migration files.

Baseline typed-value indexes:

```text
(field_id, string_value, schema_instance_id)
(field_id, number_value, schema_instance_id)
(field_id, boolean_value, schema_instance_id)
(field_id, date_value, schema_instance_id)
(field_id, datetime_value, schema_instance_id)
(field_id, uuid_value, schema_instance_id)
(field_id, enum_value, schema_instance_id)
```

Partial indexes may replace/specialize these if PostgreSQL integration tests and representative query plans justify them. Do not create per-user-field DDL/indexes as part of ordinary schema editing.

## 19. Error Handling

Return controlled client errors for:

- missing/ineligible Ticket Type;
- no current published schema;
- unknown custom field key;
- custom/common-field collision;
- incompatible value type;
- failed type-specific constraints;
- missing required value;
- editing immutable published/retired schema;
- unsupported filter operator;
- unsupported/non-sortable sort field;
- ambiguous runtime field identity/type;
- unsupported Object Authorization expression that cannot be translated.

Authorization and runtime-schema failures fail closed.

## 20. Tests

Implementation follows TDD.

### Domain/unit

Cover schema lifecycle, immutability, stable field identity, semantic type changes, duplicate/common-field collisions, constraints, requiredness, and ENUM stable keys.

### Query/JPA

Cover runtime-field resolution, shared runtime context, typed predicates, STRING CONTAINS, null/missing EXISTS semantics, ambiguity rejection, typed sorting, missing-last ordering, stable ID tie-breaking, and filter + authorization composition.

### PostgreSQL integration

Use Testcontainers/PostgreSQL, not H2 substitution. Cover V1 migration, FK/CHECK invariants, exact historical schema/value membership, concurrent publication, create-vs-publish races, historical preservation, typed filtering/sorting, pagination, and Object Authorization push-down.

### Web integration

Cover management APIs, Ticket create/list/read/update, Request Authorization, Object Authorization, `filterBy`, custom sorting, malformed/unsupported runtime fields, and historical reads/updates.

## 21. Out of Scope

- UI/form layout or rendering metadata;
- conditional field visibility;
- form-builder behavior;
- automatic/bulk historical Ticket migration;
- Ticket deletion unless separately specified;
- arbitrary untyped JSON custom values;
- a Ticket-specific filtering language;
- in-memory fallback for unsupported Object Authorization;
- per-field runtime database DDL;
- making stable system Ticket metadata user-definable.

## 22. Implementation Order

After implementation-plan approval:

1. Generic EAV domain contracts and schema lifecycle.
2. EAV JPA persistence + `V1__schema.sql` invariants.
3. Ticket Type/domain module and common Ticket baseline.
4. Ticket create/read/update pinned to EAV schema instances.
5. Runtime QuerySchema context plumbing.
6. Generic dynamic JPA field binding for typed EAV predicates.
7. Generic sorting support.
8. Object Authorization runtime-schema integration.
9. Web API adapters and error mappings.
10. PostgreSQL/API integration and authorization regression coverage.
11. Full repository verification and CI repair.

## 23. Success Criteria

The implementation is complete only when #236 acceptance criteria hold and:

- Ticket common fields remain stable system-owned columns;
- versioned custom fields use the generic typed EAV subsystem;
- EAV persistence contains no Ticket-specific coupling;
- each Ticket retains exact historical schema identity;
- each stored EAV value is constrained to a field/type from that exact historical schema version;
- schema evolution never implicitly rewrites historical data;
- Query and Policy consumers use a flat field namespace;
- supported custom filtering, sorting, and Object Authorization execute database-side;
- authorization and filtering occur before pagination;
- ambiguous/unsupported dynamic semantics fail closed;
- all schema changes remain in `V1__schema.sql` while application major version is 1.
