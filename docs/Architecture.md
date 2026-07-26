# Console Architecture

## Capability-oriented modules

Taskmigo Console is a Spring Modulith application. Modules represent cohesive business
capabilities, while packages inside a module separate technical responsibilities.

```text
ConsoleApplication
├── access
│   └── internal/
│       ├── application/{identity,oauth,signing}
│       ├── domain/{identity,oauth,signing}
│       ├── persistence/{identity,oauth,signing}
│       ├── web/oauth
│       └── configuration/{identity,oauth}
└── system
    └── internal/web
```

`access` is the Identity and Access Management bounded context. It owns users, form login,
OAuth clients, Authorization Server, Resource Server configuration, signing keys and OAuth
client administration. Keeping these responsibilities together makes the complete authentication
and authorization flow visible without turning each technical concept into a module.

`system` owns only the current sample endpoints. Future capabilities such as tasks, projects and
notifications become separate application modules.

## Security overview

All HTTP security chains are declared in `AccessSecurityConfiguration`. Spring Security uses the
first matching chain:

| Order | Matcher | Responsibility |
|---:|---|---|
| 100 | Authorization Server endpoints | OAuth 2.1 and OpenID Connect |
| 200 | `/api/**` | JWT Resource Server; `/api/public` is explicitly public |
| 300 | Fallback | Form login and browser routes |

Order values are named constants in the same class. Feature-specific access rules remain close to
their controllers or use cases through `@PreAuthorize`.

## Dependency rules

- Each feature root has `@ApplicationModule(allowedDependencies = {})`.
- Module internals are never imported by another feature.
- A module is created for a cohesive capability, not for a framework type.
- Layer and sub-capability separation happens through internal packages.
- Shared web code may contain filters, serialization or a common error envelope, but never
  business endpoints.
- Constructor injection is mandatory. `@Autowired` is prohibited.
- `ApplicationModules.verify()` is the architecture acceptance test.

## Adding a feature or API

1. Create `<feature>/package-info.java` and annotate it with `@ApplicationModule`.
2. Add application, domain, persistence, web and configuration packages only as needed.
3. Put controllers, DTOs, validation and API tests inside the owning feature.
4. Do not import another module's `internal` types.
5. Keep `/api/**` authenticated by default and add `@PreAuthorize` for feature scopes or roles.
6. Add new workflows to `UseCaseCoverage.md` and map them to tests.
7. Run `./gradlew test`; module and security ownership verification are part of that gate.

A framework component moves to an application-level composition root only when it genuinely
coordinates multiple business modules. Multiple beans of one type are not sufficient reason to
create another module.
