# Resource Schema Policy Engine Design

## Intent

Issue #237 replaces the Policy Engine's duplicated schema concepts with one semantic resource model that treats build-time and runtime-defined fields identically. The migration is intentionally breaking and covers every current static resource in this repository. The separate `taskmigo/specification` repository remains read-only and is not changed by this work.

## Scope

This design:

- introduces one semantic `ResourceSchema` contract and a generic resolution boundary;
- binds compiler roots to resources instead of redeclaring their fields;
- resolves field references to stable semantic identities during compilation;
- separates semantic field metadata from query and persistence translation;
- migrates query filtering and Object Authorization to that shared model;
- makes schema fingerprints explicit compiled-policy and cache dependencies; and
- removes the superseded `EnvironmentSchema`, `QuerySchema`, and `ObjectAuthorizationSchema` APIs.

Ticket-specific runtime-field storage, a choice among JSONB/EAV/typed tables, and compatibility adapters for the removed APIs are out of scope.

## Semantic model ownership

The framework-neutral language module owns the semantic contracts:

- `ResourceType` is the stable identity of a resource kind and is independent of Java `Class<?>`.
- `FieldId` is the stable semantic identity of a field and is independent of its display or source path.
- `FieldPath` is the source-facing path used to resolve a field during compilation.
- `Field` contains a `FieldId`, canonical `FieldPath`, `LanguageType`, and nullability.
- `SchemaFingerprint` identifies the complete effective semantic contract of a resource schema.
- `ResourceSchema` exposes its resource type, resolves a `FieldPath`, enumerates declared fields when consumers need validation, and exposes its fingerprint.
- `ResourceSchemaResolver` resolves the effective schema for a resource type and schema context without exposing whether it came from code, configuration, a database, a plugin, a cache, or a composition of providers.

Static and runtime-defined schemas implement the same contract. Resource schema implementations contain no table, column, join, JSON, JPA, or other persistence metadata.

## Compiler environment and typed IR

`EnvironmentSchema` is replaced by a compiler environment whose responsibility is limited to binding language root names to resource schemas and assigning root slots. Authorization roots such as `principal`, `request`, and `object` are ordinary resource bindings.

During compilation, each source field path is resolved exactly once through the bound `ResourceSchema`. A compiled field reference retains:

- the bound root and its runtime slot;
- `ResourceType` and `FieldId` as semantic identities;
- the resolved language type and nullability;
- the canonical path required to read an approved runtime value; and
- source span and dependency information.

The canonical path is execution data, not a second schema lookup mechanism. Translators and executable IR consume the resolved field identity and must not rediscover type or existence from raw strings. Local variables remain compiler-owned references and are not resource fields.

Compiled metadata records the fingerprints of every effective resource schema rather than one opaque environment fingerprint. Its validity identity includes source content, language/compiler contract, compilation profile, compilation mode, root-to-resource bindings, and the effective schema fingerprints.

## Runtime evaluation

Runtime evaluation uses the root slot and the canonical resolved path embedded in the typed IR to read values from the approved runtime root objects. It does not call a schema resolver and does not infer types during execution.

A runtime value that cannot satisfy the already-compiled access contract fails closed through the existing language/authorization error boundary. Runtime-defined fields therefore have the same compile-time typing and runtime behavior as static fields after resolution.

## Query execution binding

`QuerySchema<Q>` is replaced by a query execution binding. A binding owns only translation concerns:

- the application contract type, when a typed application API needs it;
- the `ResourceType` and schema fingerprint it is compatible with;
- translation from a semantic `FieldId` to the query model or persistence path;
- the operators supported by that execution target; and
- construction/binding of the resulting query predicate.

Field types, nullability, source paths, and semantic fingerprints are not redeclared in the binding. Filter compilation receives both a `ResourceSchema` and its compatible binding. It compiles against the schema, produces an expression containing resolved field identities, validates execution operators against the binding, and emits the existing opaque typed query predicate.

Bindings reject unknown field identities and schema fingerprint mismatches. They never fall back to matching a raw field name.

## Object Authorization

`ObjectAuthorizationSchema` is removed. Routes and application integration associate an object contract with a `ResourceType`, its resolved `ResourceSchema`, and the compatible query/persistence binding used to materialize an object predicate.

Object Authorization consumes the same compiled typed policy IR used by runtime evaluation. Partial evaluation preserves resolved field references. Conversion into an object predicate carries semantic `FieldId` values and the schema fingerprint, and the resource-owned persistence binder translates those identities to its JPA paths.

`ObjectAuthorizationPredicate<T>` may remain as the opaque application integration type. Its generic Java type is not schema identity and is not used for field discovery.

## Multi-resource statements

An Object Statement target may match routes for multiple resource types. The current union schema cannot provide stable per-resource field identity, so it is removed.

The Statement artifact factory resolves every applicable resource schema and compiles one typed policy variant per distinct resource schema fingerprint. Statement validation succeeds only when the policy compiles and satisfies the Object Authorization contract for every applicable resource. At authorization time, the active object integration selects the variant whose resource type and fingerprint match its current schema.

The derived-artifact cache identity contains the authoritative Statement revision, source fingerprint, compiler/profile identity, target matcher identity, and the sorted resource type/fingerprint set. Adding, removing, or changing an applicable schema invalidates the derived artifact rather than silently changing policy semantics.

## Static resource migration

Every existing static query or Object Authorization surface is migrated to a single resource declaration. The resource owns one semantic schema; query filtering and JPA Object Authorization each contribute execution bindings referencing the same `FieldId` constants.

The migration removes duplicated type/nullability declarations from resource execution mappings. Tests must make accidental field-id omission, duplicate IDs, incompatible fingerprints, and unsupported operators explicit failures.

No compatibility facade, deprecated alias, or path-based fallback remains for `EnvironmentSchema`, `QuerySchema`, or `ObjectAuthorizationSchema`.

## Failure behavior

Compilation fails when a root, resource, or field path is not defined, or when language types are incompatible. Query/object translation fails when a binding is incompatible with the compiled schema fingerprint, does not bind a referenced field identity, or does not support an operator used by the compiled expression.

Schema resolution failures identify the requested `ResourceType` and context without leaking provider internals. Authorization wraps these failures in its established fail-closed boundary. No failure path retries against a raw path or another schema API.

## Verification strategy

Tests are added before or alongside each production slice and cover:

- deterministic resource, field, and schema identities;
- code-backed and runtime-built schemas through the same contract;
- compile-time field existence, type, and nullability checks;
- typed IR and expression visitors exposing resolved field identities;
- runtime evaluation without schema re-resolution;
- query translation through `FieldId` and rejection of missing/incompatible bindings;
- Object Authorization partial evaluation and JPA binding through the same semantic model;
- multi-resource Statement compilation and active-variant selection;
- cache invalidation when any effective schema fingerprint changes;
- migration of all existing static resources; and
- architecture/module boundaries and absence of the removed schema APIs.

Repository formatting, the sequential full Gradle build, and architecture verification run locally. Performance benchmarks are not run locally. After the coherent implementation is reviewed and pushed, every required GitHub Actions check is monitored to completion and any task-related failures are repaired within the owning problem commit.

## Completion criteria

The work is complete when all issue #237 acceptance criteria are represented by executable tests, every current resource uses `ResourceSchema`, executable IR contains resolved `FieldId` values, query and Object Authorization translate those identities through separate bindings, compiled validity includes schema fingerprints, the three superseded schema APIs and their duplicate metadata are gone, and all required local and hosted checks pass.
