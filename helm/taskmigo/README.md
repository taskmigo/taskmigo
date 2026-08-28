# Taskmigo Helm chart

This chart deploys the Taskmigo bootstrap job, web server, worker, and browser client. PostgreSQL is intentionally external to the chart so schema migration can run as a Helm `pre-install` / `pre-upgrade` hook before runtime workloads are created.

## Prerequisites

- Kubernetes 1.30 or newer.
- Helm 4 or a compatible Helm 3 release.
- A reachable PostgreSQL database.
- A Kubernetes Secret containing Taskmigo runtime credentials.

The default Secret name is `taskmigo-secrets` and the chart expects these keys:

| Key | Used by |
| --- | --- |
| `database-password` | Bootstrap, web, worker |
| `bootstrap-user-password` | Bootstrap system-user reconciliation |
| `auth-client-secret` | Browser OAuth client reconciliation and client runtime |
| `auth-session-secret` | Client session encryption; must be at least 32 characters |

When the Helm integration test OAuth client is enabled, the Secret must additionally contain `integration-test-client-secret`.

## Install

Create the Secret outside Helm so upgrades never rotate credentials implicitly:

```bash
kubectl create namespace taskmigo
kubectl -n taskmigo create secret generic taskmigo-secrets \
  --from-literal=database-password='replace-me' \
  --from-literal=bootstrap-user-password='replace-me' \
  --from-literal=auth-client-secret='replace-me' \
  --from-literal=auth-session-secret='replace-with-at-least-32-characters'
```

Install Taskmigo and point it at PostgreSQL. For a public deployment, set the externally reachable web and client URLs so OAuth issuer and redirect URIs are stable:

```bash
helm upgrade --install taskmigo ./helm/taskmigo \
  --namespace taskmigo \
  --set database.url='jdbc:postgresql://postgresql.example.internal:5432/taskmigo' \
  --set database.username='taskmigo' \
  --set web.publicUrl='https://api.taskmigo.example' \
  --set client.publicUrl='https://taskmigo.example' \
  --set client.auth.issuer='https://api.taskmigo.example' \
  --set client.auth.allowInsecureRequests=false \
  --set client.auth.cookie.secure=true
```

The lifecycle is:

1. Helm runs the bootstrap Job before install or upgrade.
2. Bootstrap runs Flyway migrations and reconciles installation state such as the system user and managed OAuth clients.
3. Only after the hook succeeds does Helm create or update web, worker, and client workloads.
4. Web and worker keep Flyway disabled and only consume the migrated schema.

A failed bootstrap hook fails the Helm release before runtime workloads are changed.

## Ingress

Ingress is disabled by default. Enable it and provide separate hosts for the browser client and authorization/API server:

```yaml
ingress:
  enabled: true
  className: nginx
  clientHost: taskmigo.example
  webHost: api.taskmigo.example
```

Keep `client.publicUrl`, `web.publicUrl`, and `client.auth.issuer` aligned with those external URLs.

## Test

The chart includes an optional Helm test pod. With `tests.enabled=true`, it validates OIDC discovery, the unauthenticated API contract, and the browser client. With both `tests.oauthClient.enabled=true` and `bootstrap.testClient.enabled=true`, bootstrap also provisions a test-only machine client and the Helm test obtains a real access token before calling the authenticated API.

The repository GitHub Actions integration workflow exercises this mode against a disposable Minikube cluster and PostgreSQL instance.
