# Taskmigo engineering knowledge

Start here when developing, reviewing, operating, or investigating Taskmigo. This is a shared engineering reference for developers and AI agents, maintained alongside the implementation rather than in an agent-specific memory store.

## Get started

- [Repository navigation](engineering/repository-navigation.md): locate ownership, implementation, contracts, and tests.
- [Contribution guide](../CONTRIBUTING.md): development setup, required checks, and PR conventions.
- [AI-driven development](engineering/ai-dlc.md): Chat and native workflows, approval, checkpoints, and delivery.
- [Knowledge maintenance](engineering/knowledge-maintenance.md): how to contribute and keep these articles useful.

## Implementation and architecture

- [Audit attribution](audit-attribution.md): actor, executor, cause, transaction ownership, and async propagation.
- [E2E SDK and testing](../e2e/README.md): browser and API transport, SDK boundaries, and running tests.

## Operations and troubleshooting

- [HTTP sessions](operations/http-sessions.md): deployment, persistence, and session lifecycle.
- [Reading CI evidence](engineering/ci-evidence.md): distinguish source errors from tooling failures, locate useful artifacts, and verify the right revision.
- [Helm deployment reference](../helm/taskmigo/README.md): chart usage and configuration.

## Source of truth

The [Taskmigo specification](https://github.com/taskmigo/specification) defines intended product behavior. Source code, tests and configuration establish actual implementation. These articles explain how to work with that implementation and link to its evidence; they are not a replacement for the specification, executable contracts, or [repository instructions](../AGENTS.md).

[Historical design and implementation plans](superpowers/) can explain earlier decisions, but are not necessarily current. Verify a historical claim against the current implementation before relying on it.

When adding or moving an article, update the relevant index. Keep the most useful entry points here rather than listing every internal document.
