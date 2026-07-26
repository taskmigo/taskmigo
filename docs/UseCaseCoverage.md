# Use-case Coverage

`./gradlew test` is the acceptance gate. The criterion is complete coverage of the canonical use
cases below, not 100% line coverage.

| Area | Canonical use cases | Tests |
|---|---|---|
| Identity bootstrap | Create; update; re-enable; preserve additional roles; password match; encoder failure; repository failure | `BootstrapAdministratorTest`, `PersistenceIntegrationTest` |
| User lookup | Active; disabled; missing | `JpaUserDetailsServiceTest` |
| Client synchronization | Empty; single/re-entry; multiple; insert; update; disable; reactivate; persistent manual override; public/confidential; complete settings; invalid configuration; transactional behavior | `OAuthClientSynchronizerTest`, `SpringRegisteredClientMapperTest`, `RegisteredClientStateTest`, `PersistenceIntegrationTest` |
| Client management | Secret-free list; enable inactive/already-active/missing; confirmation create/replace/expire; valid/reused/malformed/wrong-client/wrong-requester delete; configured-client rejection; missing client | `OAuthClientManagementServiceTest`, `OAuthClientManagementWebTest`, `PersistenceIntegrationTest` |
| Signing keys | Existing; missing; concurrent initialization; selector match/no-match/multiple; invalid JWK; persistence failure; cryptographic failure | `SigningKeyLifecycleTest`, `DatabaseJwkSourceTest`, `RsaSigningKeyGeneratorTest`, `PersistenceIntegrationTest` |
| JWT and security | Chain precedence; active issuance; existing JWT after disable/delete; rejected new token; expired JWT `401`; scope behavior; admin API scope | `PersistenceIntegrationTest` |
| PostgreSQL | Flyway V1-V4 compatibility; lifecycle constraints; multiple clients; disable; manual enable; authorization cascade; deletion; configuration re-add | `PersistenceIntegrationTest` |
| Architecture | Modulith verification; exactly access/system; all security chains owned by access; no generic API/composition module; no cross-feature internal access; no `@Autowired` | `ArchitectureTest` |

When adding or changing a workflow, update this inventory and its test mapping in the same change.
