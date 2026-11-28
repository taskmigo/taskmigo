# Repository navigation

Use this guide to find the owning implementation before changing code. It explains the repository layout; the [specification](https://github.com/taskmigo/specification) defines intended product behavior, while the [server instructions](../../server/AGENTS.md) and [contribution guide](../../CONTRIBUTING.md) define engineering constraints.

## Start with the boundary

- [Client](../../client/): frontend application code and its packages and tests.
- [Web application](../../server/apps/web/): REST adapters, OAuth endpoints, HTTP transport, and OpenAPI configuration. An HTTP-specific DTO or controller belongs here, not in a reusable feature module.
- [Worker application](../../server/apps/worker/): background processing.
- [Migration application](../../server/apps/migration/): database migration and initial provisioning.
- [OpenAPI generator](../../server/apps/openapi-generator/): derives the checked-in API description from the implementation.
- [Server modules](../../server/modules/): reusable domain, application and supporting code. Start with the feature-owning module (for example, identity, ticket, authorization or audit); inspect its dependencies rather than routing unrelated behavior through Web.
- [Benchmarks](../../server/benchmarks/): performance experiments and regression measurements, separate from functional tests.
- [E2E](../../e2e/): deployment-level automation, with a reusable SDK and Playwright scenarios.
- [Helm](../../helm/) and [deployment resources](../../deploy/): application packaging and deployment.

Check [Gradle project declarations](../../server/settings.gradle.kts) for actual server module paths. Package and application ownership matters more than similar-looking class names.

## Changing an API

First locate the specified requirement, the feature-owning use case, and the HTTP adapter. Follow the request through authorization, validation, domain behavior, persistence and response mapping as applicable. Identify direct consumers before changing a public contract, including the client and E2E SDK.

The [checked-in OpenAPI snapshot](../../server/apps/web/src/main/resources/static/api/docs/openapi.yaml) is generated. Review its differences as contract evidence; regenerate it rather than writing a guessed description. The [E2E guide](../../e2e/README.md) explains how the public SDK boundary validates responses and uses the browser BFF transport.

If code, generated documentation and specification disagree, report the discrepancy. The generated artifact describes implementation; it does not by itself approve a requirement change.

## Changing persistence

The [database module](../../server/modules/database/) owns Flyway schema migration. The current [major-version schema](../../server/modules/database/src/main/resources/db/migration/V1__schema.sql) is the place to inspect tables, constraints and indexes. Follow the contribution guide and repository rules for schema evolution and persistence APIs. Check application ownership and integration tests; do not infer a valid migration from entity classes alone.

## Finding related knowledge

For changes involving actors, scheduled work, audit transactions or async propagation, start with the [audit attribution guide](../audit-attribution.md). For session lifecycle and deployment concerns, read [HTTP sessions](../operations/http-sessions.md). For failures after a push, use [CI evidence](ci-evidence.md).

These guides provide orientation, not permission to skip reading the actual source or applicable specification. Verify the relevant execution path at the current revision, especially when changing a cross-application boundary.
