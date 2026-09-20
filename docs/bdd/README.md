# Business BDD

This directory stores Taskmigo business acceptance scenarios in BDD form.

The authoritative product specification remains the
[Taskmigo specification repository](https://github.com/taskmigo/specification).
BDD scenarios in this repository are the implementation-facing acceptance contract derived from that specification:
business E2E coverage must implement these scenarios rather than define independent behavior.

## Scope

Use this area for observable business behavior that can be expressed as an acceptance scenario.

Do not use it for:

- Unit-test cases.
- Component or integration-test implementation details.
- Framework behavior, internal class structure, database details, selectors, or transport-level mechanics.
- Test-runner setup and fixtures.

## Layout

```text
docs/bdd/
├── README.md
└── features/
    ├── README.md
    └── <business-domain>/
        └── <capability>.feature
```

Group feature files by business domain. Use kebab-case for directory and file names.

## Scenario format

Write scenarios in Gherkin and describe outcomes in business language.

Every scenario must have a stable identifier tag using this format:

```text
@BDD-<DOMAIN>-<NNN>
```

For example, an authentication scenario could use `@BDD-AUTH-001`. Once published, an identifier must not be reused for a different business scenario.

A feature file should follow this shape:

```gherkin
Feature: <business capability>

  @BDD-<DOMAIN>-001
  Scenario: <business outcome>
    Given <business precondition>
    When <business action>
    Then <observable business result>
```

Prefer one business rule or outcome per scenario. Keep steps implementation-agnostic so the same scenario remains valid even when UI, API, or internal architecture changes.

## Change workflow

When product behavior changes:

1. Update or add the BDD scenario before, or in the same pull request as, the implementation.
2. Keep the scenario aligned with the authoritative Taskmigo specification.
3. Update the E2E implementation that covers the affected scenario.
4. Preserve the stable scenario identifier when the business intent is unchanged.
5. Create a new identifier when a genuinely new business scenario is introduced.

A behavior change is not complete when its BDD contract and E2E coverage disagree.

## E2E traceability

Playwright tests under `e2e/` implement this contract.

Every E2E test that implements a BDD scenario must include the scenario identifier in its test title, for example:

```ts
test('BDD-AUTH-001: authenticated user can access the account page', async ({ page }) => {
  // ...
});
```

One BDD scenario may require more than one E2E test when multiple technical paths are necessary to prove the same business outcome. In that case, every implementing test should carry the same BDD identifier.

Support helpers, environment checks, and test infrastructure do not need BDD identifiers because they do not represent business scenarios.
