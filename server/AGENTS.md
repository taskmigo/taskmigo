# Server agent instructions

These instructions apply to `server/` and all of its descendants. Repository-wide instructions in the root `AGENTS.md` and `CONTRIBUTING.md` also apply.

## Persistence queries

- Do not use `org.springframework.data.jpa.repository.Query` by default. Prefer Spring Data derived queries, specifications, the persistence API supplied by the owning library, or another repository abstraction that keeps query intent type-safe and maintainable.
- If `@Query` appears necessary for a reasonable technical reason, do not add or keep it based on agent judgment alone. Explain why the preferred alternatives are insufficient and ask a maintainer for explicit confirmation before using it.
- Treat maintainer confirmation as specific to the proposed use case. Do not infer a general exception for other repositories or queries.
- Native queries and other raw-SQL mechanisms remain prohibited by the repository contribution rules even when a maintainer approves use of `@Query`.
