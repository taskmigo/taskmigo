# Phase 1 - Target project and package graph

## Scope

This document implements the project/package-graph decision required by Phase 1 of
[issue #141](https://github.com/taskmigo/taskmigo/issues/141). It applies ADR-001 without moving business capabilities
prematurely.

Phase 1 establishes enforcement **before** the Identity and Access Control vertical migrations. It intentionally avoids a
repository-wide package move.

## Runtime project graph

The migration uses the following runtime project boundaries unless a later tracked phase records a stronger reason to
split or remove one.

| Project                   | Phase 1 decision                                          | Boundary rationale                                                                                                                                                                                                                                       |
| ------------------------- | --------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `:apps:web`               | **Fixed executable root**                                 | HTTP/API/OAuth/Spring Security driving adapter and web composition root. Executable applications remain dependency leaves.                                                                                                                               |
| `:apps:worker`            | **Fixed executable root**                                 | Background-job driving adapter and worker composition root.                                                                                                                                                                                              |
| `:apps:migration`         | **Fixed executable root**                                 | Schema/data/provisioning driving adapter and migration composition root.                                                                                                                                                                                 |
| `:modules:identity`       | **Retain one bounded-context project during migration**   | Identity capabilities share one ownership/lifecycle boundary. Onion rings are package boundaries because separate ring projects would add dependency plumbing without useful independent lifecycle or reuse.                                             |
| `:modules:access-control` | **Retain one bounded-context project during migration**   | Access Control capabilities share one ownership/lifecycle boundary and optimized authorization paths. Package rules provide ring isolation without forcing aggregate traversal or one-project-per-ring ceremony.                                         |
| `:modules:language`       | **Retain supporting-capability project**                  | Language is consumer-neutral, independently meaningful, and reusable by Query and Access Control without depending on either consumer.                                                                                                                   |
| `:modules:query`          | **Retain supporting-capability project**                  | Query owns consumer-neutral filter/query semantics used by multiple resource owners. Resource-specific database binding remains outside Query.                                                                                                           |
| `:modules:foundation`     | **Retain as the minimal technical dependency floor**      | Phase 5 confirmed four framework-neutral primitives shared across independent capabilities and web adaptation. Architecture enforcement prevents Foundation from acquiring bounded-context, supporting-capability, application, or persistence dependencies. |
| `:modules:database`       | **Retain shared technical infrastructure**                | Phase 5 confirmed a coherent shared owner: all three executables import the datasource configuration, Migration consumes the single V1 Flyway schema, and Identity plus Access Control share generic JPA Criteria comparison mechanics. Architecture enforcement keeps higher-level capability ownership out. |

The current physical directory name `server/modules/authorization` remains an implementation detail behind the logical
Gradle project `:modules:access-control`. Renaming that directory alone would add no enforcement and is therefore not a
Phase 1 goal.

## Test-only enforcement project

Phase 1 adds:

```text
:testing:architecture
```

This project contains reusable ArchUnit rules only. It is consumed through `testImplementation`; it is not a runtime
dependency and cannot become a shared production abstraction.

A dedicated test project is justified because the same Hexagonal/Onion rules must be applied consistently to Identity,
Access Control, and executable driving adapters. Copying those rules into each project would allow enforcement to drift.

## Why rings stay package boundaries

Identity and Access Control use the following target package vocabulary:

```text
io.taskmigo.<context>.<capability>
  domain/
  application/
    port/
      in/
      out/
    service/
  adapter/
    out/
      persistence/
      <external-system-or-context>/
```

Driving application packages converge on:

```text
io.taskmigo.web
  adapter/in/
  composition/

io.taskmigo.worker
  adapter/in/
  composition/

io.taskmigo.migration
  adapter/in/
  composition/
```

These are ArchUnit-enforced package boundaries because domain, application, and adapter code for one capability changes
under one bounded-context lifecycle. Creating Gradle projects for every ring would mainly replace package imports with
project dependency declarations while increasing build and composition ceremony.

A later adapter may become a separate Gradle project only when it gains a real independent lifecycle/reuse boundary or
when classpath isolation provides materially stronger protection than package enforcement.

## Enforcement allocation

| Concern                                                   | Primary enforcement                                 | Reason                                                                                              |
| --------------------------------------------------------- | --------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Executable applications are dependency leaves             | Gradle project graph                                | A reusable project cannot compile against an app unless the project dependency is declared.         |
| Supporting-capability / bounded-context project direction | Gradle + Spring Modulith                            | Project classpaths constrain coarse direction; Modulith constrains logical/private package access.  |
| Cross-context published contracts                         | Spring Modulith named interfaces + Gradle direction | Ownership is logical and should not require splitting every published interface into a project.     |
| Domain -> application/adapter prohibition                 | ArchUnit                                            | Domain and outer rings intentionally share a bounded-context project.                               |
| Application -> adapter prohibition                        | ArchUnit                                            | Same-project vertical slices need package-level dependency checks.                                  |
| Inbound/outbound port direction                           | ArchUnit                                            | Port direction is package semantics, not an independent deployment/reuse boundary.                  |
| Driving/driven adapter direction                          | ArchUnit                                            | Adapters may share an executable or bounded-context project while remaining directionally isolated. |
| JPA/Spring Data containment                               | ArchUnit                                            | Persistence frameworks are permitted only in current/future driven persistence adapter packages.    |
| Shared architecture rule implementation                   | `:testing:architecture`                             | Reusable test mechanism without creating a production dependency.                                   |

Spring Modulith verification in `web`, `worker`, and `migration` remains mandatory and is not replaced by ArchUnit.

## Transitional package policy

Phase 1 does not rename all existing packages. The shared rule configuration therefore recognizes current outward adapter
locations as **transitional adapter packages** while simultaneously installing the target rules.

Identity's transitional `infrastructure` / top-level `persistence` locations were retired during Phase 2. Shared JPA predicate binding now lives explicitly under `io.taskmigo.identity.adapter.out.persistence.query`, while capability-specific repositories/entities remain under their resource-owned driven persistence adapters.

Access Control's transitional `infrastructure` / top-level `persistence` locations were retired during Phase 3. Shared JPA predicate binding now lives under `io.taskmigo.authorization.adapter.out.persistence.query`, while Role hierarchy closure maintenance is owned by the Role driven persistence adapter.

The former `io.taskmigo.authorization.object.persistence..` package was also normalized during Phase 3. Its persistence-neutral expression/predicate representation now lives under `io.taskmigo.authorization.object.model..` and is published as the `object-model` named interface for resource-owned persistence binders; application-only expression validation remains in the Object Authorization application service package.

Query's former `io.taskmigo.query.persistence..` package was normalized during Phase 5. Its persistence-neutral expression/predicate model now lives under `io.taskmigo.query.model..` and is published as the `model` named interface; JPA binding remains in the resource-owning Identity and Access Control driven adapters.

Recognition is not compatibility approval. The tracked migration phases remove transitional locations slice by slice.

Target-only rules are empty-safe until a target package is introduced. Once a class appears under
`application.service`, `application.port.in`, `application.port.out`, `adapter.in`, or `adapter.out`, the rule
becomes active for that class immediately.

The existing domain/application/public-contract guards are **not** empty-safe; Phase 1 therefore does not weaken the
strategic/tactical protections already present before the migration.

## Rules installed before structural moves

The shared rule set rejects at least the following:

- Domain dependencies on application or adapter packages.
- Domain dependencies on Spring/JPA.
- Application orchestration dependencies on concrete adapters, Spring Data, or JPA.
- Target application services that depend on Spring or JPA.
- Inbound ports that depend on application-service implementations or adapters.
- Outbound ports that depend on application-service implementations or adapters.
- Driving adapters that depend on application-service implementations, outbound ports, driven adapters, or persistence frameworks.
- Driven adapters that depend on application-service implementations, inbound ports, or driving adapters.
- JPA/Spring Data dependencies outside configured driven persistence adapter packages.
- Published contracts that expose current/future tactical implementation types.
- Domain/application unit tests that pull in Spring or persistence runtimes.

`:testing:architecture` contains representative violating fixtures and verifies that these rules fail instead of only
describing the intended structure.

## Cross-context direction

The project graph keeps Access Control independent from Identity internals.

Identity may depend on deliberately published Access Control contracts because it provides adapters for Access-Control-
owned subject resolution and consumes narrow Access Control capabilities. This does not permit Identity to import Access
Control private application/domain/adapter packages; Spring Modulith named interfaces remain the logical boundary.

No reverse Access Control -> Identity project dependency is introduced.

## Phase 5 supporting and technical capability decision

Phase 5 revalidated the supporting and technical projects against actual consumers rather than preserving the Phase 1
graph by inertia.

- `:modules:language` remains a standalone supporting capability. Query and Access Control consume it, while Language
  does not depend back on either consumer, executable applications, persistence APIs, or Spring application-service
  mechanics. ANTLR remains confined to its compiler frontend; no artificial domain/application/adapter rings are added.
- `:modules:query` remains a standalone supporting capability. Its persistence-neutral predicate representation lives
  under `io.taskmigo.query.model`; resource-specific JPA binding remains in the Identity and Access Control driven
  adapters.
- `:modules:database` remains shared technical infrastructure because its datasource configuration is imported by all
  three executables, its single V1 Flyway schema is consumed by the migration runtime, and its generic JPA Criteria
  comparison helper is shared by Identity and Access Control. It must not acquire bounded-context or supporting-capability
  semantics.
- `:modules:foundation` remains the minimal dependency floor because its four production types are framework-neutral
  primitives shared across independent capabilities and HTTP adaptation. It must not acquire ports, adapters,
  bounded-context semantics, or framework dependencies.
- No provider-owned port or bounded-context abstraction is moved into Database or Foundation to simplify dependency
  wiring. Shared placement requires consumer-neutral semantics.

## Deferred graph decisions

The following remain intentionally deferred to their tracked phases:

- Whether specific persistence/external adapters deserve separate Gradle projects after the vertical migrations expose
  stable seams.
- Final `api` versus `implementation` exposure tightening.
- Removal of every transitional package and generic `spi` name.

Those deferrals do not weaken Phase 1: new target packages are already mechanically constrained, while current business
code remains protected until each vertical slice removes its legacy path.
