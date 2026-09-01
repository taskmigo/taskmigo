---
name: spring-boot-testing
description: "Write unit and integration tests for Spring Boot 4 (Java) code using JUnit 5, Mockito, AssertJ, and Testcontainers. Use this skill whenever the user asks to write, scaffold, or review a test for a Spring Boot class — service, controller, repository, or component — whether they say 'unit test', 'integration test', 'test this class/method', 'viet test', 'viet unit test', or 'viet integration test'. Enforces required conventions — every test method has @DisplayName, every test has a comment block explaining exactly what behavior it verifies and why, Arrange-Act-Assert structure, and shouldExpectedBehaviorWhenCondition naming. Also use this skill to decide whether a given piece of logic needs a unit test, an integration test, or both."
---

# Spring Boot 4 Testing (Unit + Integration)

A skill for writing consistent, high-quality tests for Spring Boot 4 (Java) code — both isolated unit tests and full-stack integration tests — following one fixed convention so tests across the codebase look and read the same way.

Stack assumed: JUnit 5, Mockito, AssertJ, Testcontainers, Spring Boot Test.

## Step 0: Decide unit vs integration test

Ask this before writing anything: **does this test need real collaborators (DB, HTTP layer, Spring context) to be meaningful, or can it verify behavior with the real object under test and everything else mocked?**

| Use a **unit test** when... | Use an **integration test** when... |
|---|---|
| Testing business/validation logic inside a single class | Testing DB constraints, triggers, or Liquibase-managed schema behavior |
| The class's collaborators can be faithfully mocked (repository, client, etc.) | Testing wiring across layers (controller → service → repository → DB) |
| You want fast, no-context feedback (milliseconds) | Testing actual HTTP request/response handling, serialization, status codes |
| Testing a `@Service`, mapper, validator, or util in isolation | Testing Spring Security / OIDC filter chains, `@ConfigurationProperties` binding, or anything context-dependent |

If both apply — e.g. a service with real logic AND a controller endpoint exposing it — write both: a unit test for the logic, an integration test for the wiring. Don't use `@SpringBootTest` to unit-test pure logic; it's slow and tests the wrong thing.

If it's unclear which one a request needs, ask the user rather than guessing — this determines the whole test structure.

## Step 1: Pick the template

Two ready-to-copy templates are bundled in `assets/`:

- `assets/UnitTestTemplate.java` — Mockito-based unit test for a service class, using `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`, and `@Nested` classes grouping tests by method-under-test.
- `assets/IntegrationTestTemplate.java` — Testcontainers-based integration test for a controller, using `@SpringBootTest(webEnvironment = RANDOM_PORT)`, a `PostgreSQLContainer` wired via `@ServiceConnection` (Spring Boot's built-in Testcontainers auto-configuration), and `WebTestClient`.

Copy the relevant template, then adapt class/method names and logic to the class actually under test. Don't invent a different structure — keep the same annotations, package layout, and comment style so all tests in the codebase stay consistent.

## Step 2: Required conventions (non-negotiable)

Every test method MUST have:

1. **`@DisplayName`** — a plain-English sentence describing the scenario, e.g. `@DisplayName("should throw IllegalArgumentException when name is blank")`. Not a restatement of the method name in different casing — write it as a sentence a non-Java-reader could understand.
2. **A Javadoc-style comment block directly above the method** (above `@Test`/`@DisplayName`, not inside the body) explaining what the test verifies, in detail — this is the primary documentation for maintaining the test later, so err on the side of more detail. Always three parts:
   - **Verifies that** — one sentence, the behavior under test, in plain language.
   - **Given** — the specific input/state/condition, including concrete values (e.g. "a request with name = blank string", "example id 999999 that does not exist").
   - **Expect** — the specific, concrete outcome (return value, exception, HTTP status, DB state, mock interaction) — not just "it works."

   ```java
   /**
    * Verifies that creating an example with a valid request persists it and
    * returns the persisted entity to the caller.
    *
    * Given: a valid ExampleRequest with name = "name".
    * Expect: repository.save() is invoked exactly once, and the returned
    * Example has id = 1 and name = "name".
    */
   @Test
   @DisplayName("should save and return example when input is valid")
   void shouldSaveAndReturnExampleWhenInputIsValid() { ... }
   ```

   Write this before writing the test body — if you can't fill in Given/Expect with concrete values, the test isn't specific enough yet.
3. **Method name in `shouldExpectedBehaviorWhenCondition` format** — e.g. `shouldSaveAndReturnExampleWhenInputIsValid`, `shouldReturn404WhenExampleNotFound`. Always starts with `should`, always includes `when`.
4. **Arrange / Act / Assert structure**, marked with `// Arrange`, `// Act`, `// Assert` comments (or `// Act + Assert` when they're one fluent call, e.g. `assertThatThrownBy`). These stay short — the *why* now lives in the Javadoc above; these just mark the three phases.
5. **AssertJ assertions** (`assertThat(...)`), not raw JUnit `assertEquals`/`assertTrue`. Use fluent chains: `assertThat(result.name()).isEqualTo(...)`.

Class naming: `XxxTest` for unit tests, `XxxIntegrationTest` for integration tests, both in `src/test/java` mirroring the main package structure (test for `com.workastra.example.ExampleService` lives at `com.workastra.example.ExampleServiceTest`).

## Step 3: Best practices

**Unit tests (Mockito):**
- Mock only *direct* collaborators of the class under test — don't mock things two layers deep.
- One behavior per test. If a test needs "and" in its description to explain what it checks, split it.
- Use `@Nested` classes to group tests by the method under test (see template) once a test class covers more than ~2 methods.
- Prefer `verify(mock).method(...)` only when the interaction itself is the thing being tested (e.g. "was the repository called") — don't verify things already covered by the returned value.
- Don't use `@MockBean`/`@SpringBootTest` for pure unit tests — that's what makes them slow. Plain `@ExtendWith(MockitoExtension.class)` is enough.

**Integration tests (Testcontainers):**
- Use `@ServiceConnection` (Spring Boot's Testcontainers integration) instead of manually wiring `@DynamicPropertySource` — less boilerplate, less to get wrong.
- Reuse containers across test classes where possible (a shared static container or Testcontainers' reuse mode) instead of starting a fresh container per class — this is the single biggest lever on integration test suite speed.
- Prefer Spring's test slices (`@DataJpaTest`, `@WebMvcTest`) over full `@SpringBootTest` when the test doesn't actually need the whole context — full `@SpringBootTest` should be reserved for true end-to-end scenarios.
- Assert on the actual persisted/returned state (DB row, HTTP response body), not just "no exception was thrown."
- Don't share mutable state between test methods; each test should be able to run alone and in any order.

**Both:**
- No test logic (loops, conditionals) inside a test method — if you need that, it's a sign the test is doing too much or needs parameterization (`@ParameterizedTest`).
- Test data via small builder/factory helpers when a class has many fields, so each test only sets what it actually cares about.
- Never leave a test that only exists to "keep coverage up" without a real behavior it verifies — every test's `@DisplayName` should describe a behavior someone would notice if it broke.

## Step 4: Verify

After writing the test, check:
- [ ] Every test method has `@DisplayName` and a `should...When...` name
- [ ] Every test has an Arrange comment explaining *why*, not just *what*
- [ ] Unit test has no Spring context (`@ExtendWith(MockitoExtension.class)` only); integration test boots one on purpose
- [ ] Assertions use AssertJ
- [ ] The test actually fails if you break the behavior it claims to verify (mentally trace this, or run it against a deliberately broken version if unsure)
