# Taskmigo

Taskmigo currently provides Taskmigo Console, an OAuth 2.1/OpenID Connect Authorization Server
and JWT Resource Server backed by PostgreSQL.

## Start the development stack

The checked-in sample contains local-only credentials and two environment-configured OAuth
clients:

```bash
bash examples/docker-compose.sh up --build
```

No OAuth client is fixed in application source or `application.yml`. Production deployments must
provide their own secrets and standard
`spring.security.oauth2.authorizationserver.client.<registration-id>` configuration.

The wrapper deliberately removes conflicting exported variables before invoking Compose. This
ensures the documented sample user remains `developer` / `developer-password`, even when the
shell contains credentials from another Taskmigo environment.

## Documentation

- [Architecture](docs/Architecture.md)
- Identity and Access
  - [Users, roles and login](docs/IdentityAndAccess.md)
  - [OAuth client management](docs/OAuthClientManagement.md)
  - [JWT security](docs/JwtSecurity.md)
- [System API](docs/SystemApi.md)
- [Use-case coverage](docs/UseCaseCoverage.md)
- [Console quick start and build](console/README.md)
