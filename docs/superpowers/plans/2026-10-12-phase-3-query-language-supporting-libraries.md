# Phase 3 Query and Language supporting-library review

## Context

Issue #252 requires Query and Language to remain simple, consumer-neutral supporting libraries while Taskmigo moves from mandatory Hexagonal terminology to DDD + Onion + Modular Monolith.

Language is already structurally flat and framework-neutral apart from compile-only Spring Modulith boundary metadata. Its architecture tests reject bounded-context, persistence, Spring runtime, JPA, servlet, and parser-front-end leakage.

Query is also persistence-neutral, but `FilterByCompiler` is still a Spring `@Service`. That annotation makes the reusable Query module own runtime component registration and requires a Spring Boot starter even though Web is the only runtime dependency-injection consumer.

## Decision

Keep Spring Modulith annotations as compile-only module metadata, but remove runtime Spring component ownership from Query. Web owns construction of `FilterByCompiler` because Web is the runtime adapter that injects it into `FilteredQueryArgumentResolver`.

Language requires no production-code change in this slice.

## Implementation

1. Strengthen `QueryPackageArchitectureTest` so Query production classes cannot depend on Spring runtime stereotypes.
2. Prove the guard RED against the current `@Service` annotation.
3. Remove the `@Service` annotation and Query's now-unneeded production Spring Boot starter dependency.
4. Register `FilterByCompiler` as a Web-owned bean next to the Spring MVC Query adapter.
5. Preserve all Query filtering, Language compilation/evaluation, public API, persistence, authorization, and performance behavior.
6. Run the full repository verification gates on one final commit.

## Verification contract

The final slice is complete only when:

- Query has no Spring runtime stereotype dependency;
- Language remains consumer-neutral with no new production dependencies;
- Web still starts with exactly one injectable `FilterByCompiler`;
- Query and Language tests remain green;
- Formatting, Markdown, Server, Authorization performance, Qodana, and Kubernetes Integration pass on the same final SHA.
