# Server agent instructions

These instructions apply to `server/` and all of its descendants. Repository-wide instructions in the root `AGENTS.md` and `CONTRIBUTING.md` also apply.

## Persistence queries

- Do not use `org.springframework.data.jpa.repository.Query` by default. Prefer Spring Data derived queries, specifications, the persistence API supplied by the owning library, or another repository abstraction when those alternatives keep the solution readable, maintainable, and ergonomic.
- Do not optimize for avoiding `@Query` at the expense of developer experience. If the alternative introduces excessive boilerplate, awkward repository APIs, harder-to-understand code, or disproportionate implementation complexity, treat that DX regression as a legitimate reason to consider `@Query`.
- Balance the complete solution rather than applying the rule mechanically. Compare readability, maintainability, type safety, testability, implementation complexity, and developer experience, and choose the simplest solution with acceptable long-term trade-offs.
- If `@Query` appears preferable after that trade-off analysis, do not add or keep it based on agent judgment alone. Explain the alternatives considered, the DX or solution-quality cost of avoiding `@Query`, and ask a maintainer for explicit confirmation before using it.
- Treat maintainer confirmation as specific to the proposed use case. Do not infer a general exception for other repositories or queries.
- Native queries and other raw-SQL mechanisms remain prohibited by the repository contribution rules even when a maintainer approves use of `@Query`.
