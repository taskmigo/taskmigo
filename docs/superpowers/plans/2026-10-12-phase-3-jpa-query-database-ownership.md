# Phase 3 JPA Query and Database ownership

## Context

Issue #252 requires JPA Query to own reusable JPA-backed query mechanics while Database remains focused on coherent database runtime/configuration responsibilities.

Before this slice, Database exports a `criteria` Spring Modulith interface containing `JpaCriteriaComparison`. Its only production consumer is `JpaQueryExpressionBinder`, which forces JPA Query to depend on Database. Audit, Identity, and Access Control also declare direct Database project dependencies even though they already own their JPA compile/runtime dependencies and do not import Database types.

## Decision

JPA Criteria mechanics used to translate Taskmigo Query expressions belong to JPA Query. Database is not a general JPA helper library; it is a runtime/resource module for datasource/JPA configuration, PostgreSQL support, and migration resources consumed by executable applications.

## Implementation

1. Add an architecture rule proving JPA Query cannot depend on `io.taskmigo.database..` and prove it RED against the current Criteria helper.
2. Move ordered Criteria comparison mechanics beside `JpaQueryExpressionBinder` and keep the helper package-private.
3. Remove JPA Query's Gradle and Spring Modulith dependency on Database.
4. Remove the obsolete `database :: criteria` interface and bounded-context dependency declarations.
5. Remove direct Database project dependencies from Audit, Identity, and Access Control.
6. Make Web, Worker, and Migration consume Database as a runtime-only resource/runtime module.
7. Reduce Database's own dependency surface to runtime JPA/PostgreSQL support plus its existing resource/migration content.
8. Preserve query semantics, persistence behavior, database schema/migrations, application startup, and authorization performance.

## Verification contract

The slice is complete only when:

- JPA Query has no production dependency on Database;
- Database exports no reusable query/Criteria Java API;
- bounded contexts do not depend on Database;
- Web, Worker, and Migration still receive `application-database.yaml` and database runtime resources;
- all repository verification gates pass on one exact final SHA.
