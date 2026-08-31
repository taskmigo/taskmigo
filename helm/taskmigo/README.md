# Taskmigo Helm chart

This chart deploys the Taskmigo bootstrap job, web server, worker, and browser client. PostgreSQL is intentionally external to the chart so schema migration can run as a Helm `pre-install` / `pre-upgrade` hook before runtime workloads are created.

For a complete local Minikube deployment, run `task kubernetes:deploy` from the repository root. The Taskfile builds and
loads the local images, provisions PostgreSQL and credentials, installs Envoy Gateway, installs this chart, and verifies
the deployed stack.

## Prerequisites

- Kubernetes 1.30 or newer.
- Helm 4 or a compatible Helm 3 release.
- A reachable PostgreSQL database.
- A Kubernetes Secret containing Taskmigo runtime credentials.
- When Gateway API routing is enabled, Gateway API CRDs and a conformant Gateway controller installed in the cluster.

The default Secret name is `taskmigo-secrets` and the chart expects these keys:

| Key                       | Used by                                                   |
| ------------------------- | --------------------------------------------------------- |
| `database-password`       | Bootstrap, web, worker                                    |
| `bootstrap-user-password` | Bootstrap system-user reconciliation                      |
| `auth-client-secret`      | Browser OAuth client reconciliation and client runtime    |
| `auth-session-secret`     | Client session encryption; must be at least 32 characters |

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
  --set web.publicUrl='https://taskmigo.example' \
  --set client.publicUrl='https://taskmigo.example' \
  --set client.auth.issuer='https://taskmigo.example' \
  --set client.auth.allowInsecureRequests=false \
  --set client.auth.cookie.secure=true
```

The lifecycle is:

1. Helm runs the bootstrap Job before install or upgrade.
2. Bootstrap runs Flyway migrations and reconciles installation state such as the system user and managed OAuth clients.
3. Only after the hook succeeds does Helm create or update web, worker, and client workloads.
4. Web and worker keep Flyway disabled and only consume the migrated schema.

A failed bootstrap hook fails the Helm release before runtime workloads are changed.

## Gateway API

Gateway API routing is disabled by default. The chart can either create a `Gateway` for Taskmigo or attach its `HTTPRoute` resources to an existing shared Gateway.

To create a dedicated Gateway:

```yaml
gateway:
  enabled: true
  create: true
  className: your-gateway-class
  addresses:
    - type: IPAddress
      value: 192.0.2.1
  clientHost: taskmigo.example
  webHost: taskmigo.example
  listeners:
    client:
      name: client
      port: 443
      protocol: HTTPS
      tls:
        mode: Terminate
        certificateRefs:
          - kind: Secret
            name: taskmigo-client-tls
    web:
      name: web
      port: 443
      protocol: HTTPS
      tls:
        mode: Terminate
        certificateRefs:
          - kind: Secret
            name: taskmigo-web-tls
```

When `clientHost` and `webHost` are identical, the chart creates one public listener. `/api/auth` remains on the client
BFF; `/api`, `/.well-known`, `/oauth2`, `/login`, `/connect`, `/logout`, and `/error` route to the web backend; and every
other path routes to the browser client. The longest `PathPrefix` match keeps `/api/auth` on the client ahead of `/api`.
The client listener settings provide TLS for this shared-host mode. Distinct host values retain the two-listener behavior.

To use an existing Gateway, disable Gateway creation and identify the Gateway and listener section names:

```yaml
gateway:
  enabled: true
  create: false
  name: shared-gateway
  namespace: gateway-system
  clientHost: taskmigo.example
  webHost: api.taskmigo.example
  listeners:
    client:
      name: taskmigo-client
    web:
      name: taskmigo-web
```

For a cross-namespace shared Gateway, its listeners must allow routes from the Taskmigo release namespace. Keep `client.publicUrl`, `web.publicUrl`, and `client.auth.issuer` aligned with the public Gateway URLs.

## Test

The chart includes an optional Helm test pod. With `tests.enabled=true`, it validates OIDC discovery, the unauthenticated API contract, and the browser client. With both `tests.oauthClient.enabled=true` and `bootstrap.testClient.enabled=true`, bootstrap also provisions a test-only machine client and the Helm test obtains a real access token before calling the authenticated API.

The repository GitHub Actions integration workflow exercises this mode against a disposable Minikube cluster and PostgreSQL instance.
