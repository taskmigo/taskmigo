# Taskmigo Helm chart

This chart deploys the Taskmigo migration job, web server, worker, and browser client. PostgreSQL is intentionally external to the chart so schema migration can run as a Helm `pre-install` / `pre-upgrade` hook before runtime workloads are created.

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

| Key                    | Used by                                                                 |
| ---------------------- | ----------------------------------------------------------------------- |
| `database-password`    | Migration, web, worker                                                  |
| `system-user-password` | Initial system-user credential for migration and local/CI browser login |
| `auth-client-secret`   | Browser OAuth client migration and browser runtime                      |
| `auth-session-secret`  | Client session encryption; must be at least 32 characters               |

## Install

Create the Secret outside Helm so upgrades never rotate credentials implicitly:

```bash
kubectl create namespace taskmigo
kubectl -n taskmigo create secret generic taskmigo-secrets \
  --from-literal=database-password='replace-me' \
  --from-literal=system-user-password='replace-me' \
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

1. Helm runs the migration Job before install or upgrade.
2. Migration runs Flyway migrations and reconciles installation state such as the system user and managed OAuth clients.
3. Raw credentials from the Kubernetes Secret are hashed by the migration application before persistence. A User password is an initial credential: once that User has a password hash, later migration runs preserve it.
4. Only after the hook succeeds does Helm create or update web, worker, and client workloads.
5. Web and worker do not include Flyway and only consume the migrated schema.

A failed migration hook fails the Helm release before runtime workloads are changed.

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

Browser API traffic uses the client BFF namespace `/backend/v0/*`. Because that prefix is not owned by the public web
route, it reaches the Next.js client through the normal client route and is forwarded internally to Spring as
`/api/v0/*` with the server-held OAuth access token. The client defaults `client.backend.url` to the in-cluster web
Service URL; override it only with a trusted HTTP(S) backend origin. Do not point it at the public Gateway URL, which
would add an unnecessary public hop and can create proxy-routing loops.

The BFF behaves as a reverse proxy for this namespace: end-to-end request headers are forwarded by default, hop-by-hop
headers and browser-supplied authentication/forwarding headers are removed, and the BFF injects its server-held Bearer
token plus canonical `Forwarded`, `Via`, and `X-Forwarded-*` metadata derived from the configured public client URL.
Responses produced by Spring pass through unchanged apart from hop-by-hop/security headers and backend-location
rewriting; failures owned by the BFF itself use RFC Problem Details (`application/problem+json`) so they remain
distinct from the versioned backend API envelope.

Multi-pod refresh-token rotation is intentionally not solved in the reverse-proxy layer. The Authorization Server
rotation/reuse-window design is tracked separately in taskmigo/taskmigo#198.

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

The chart includes an optional Helm test pod. With `tests.enabled=true`, it validates OIDC discovery, the unauthenticated API contract, and the single browser client.

The repository GitHub Actions integration workflow exercises this mode against a disposable Minikube cluster and PostgreSQL instance.
