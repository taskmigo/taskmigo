# HTTP sessions

Web instances share HTTP sessions in PostgreSQL through Spring Session JDBC. Flyway owns the `spring_session` and `spring_session_attributes` tables in `V1__schema.sql`; Spring Session schema initialization is disabled.

All Web instances and Workers must use the same Identity database. Web keeps the `JSESSIONID` cookie and a 30-minute inactivity timeout. Sessions use Java serialization and index authenticated ownership by the stable User UUID. Workers delete session rows through Identity without reading serialized attributes.

User deletion removes HTTP sessions, OAuth authorizations and consents in the same transaction as lifecycle changes and audit writes. Authenticated session and interactive OAuth saves first lock the ACTIVE User; a save arriving after deletion cannot recreate authentication state. Session reads also reject missing or inactive Users.

## Deployment

Deploy the session change to all Web instances together. Existing in-memory sessions are not migrated, so users must sign in again. Do not run mixed versions of Web: all instances must understand the UUID-bearing principal and share the PostgreSQL session repository.

This schema change amends the major-version schema file. For an existing database, provision the new Spring Session tables before starting the updated applications using the project's database rollout process; Flyway does not replay an already-applied `V1__schema.sql` automatically.
