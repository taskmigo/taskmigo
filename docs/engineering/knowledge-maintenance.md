# Maintaining engineering knowledge

Taskmigo treats engineering knowledge as a deliverable of AI-driven development. A developer should be able to understand a subsystem, make a safe change, diagnose a failure, or reuse a proven approach without repeating the original investigation or reading an agent transcript.

The [knowledge index](../README.md) is the starting point for humans and agents. Store human-facing knowledge in ordinary repository documentation; use repository skills to automate discovery and maintenance, not as the only place to document the system.

## What is worth retaining?

- Architecture and implementation: ownership, invariants, component boundaries, data flow, and non-obvious design rationale.
- Developer guides: how to implement or extend a capability, with concrete examples and verification steps.
- Troubleshooting: observable symptoms, diagnostic evidence, root cause, repair, and recurrence prevention.
- Operational knowledge: configuration, deployment, failure recovery, and safety constraints.
- Validated investigation: alternatives, trade-offs, limitations, and evidence that help a future decision, even when no defect occurred.

Keep only knowledge with a plausible future use. Do not preserve routine status messages, unhelpful error transcripts, transient CI failures with no reusable lesson, speculative claims, secrets, or personal data.

## Lifecycle

1. **Discover:** before a non-trivial task, consult the knowledge index, relevant articles and the authoritative sources. Read only the topics needed.
2. **Capture:** during analysis, implementation, review, user corrections or CI repair, identify reusable discoveries and affected articles. Note the source, test, specification revision, or other evidence supporting the discovery.
3. **Curate:** after a coherent implementation or investigation, review whether any existing knowledge is wrong, incomplete, or missing. Update the existing owner first; add a new article only if a distinct reusable topic is missing. Explain why, not just what changed.
4. **Validate:** distinguish intended behavior, observed implementation, verified outcomes and open questions. Check links and examples against the relevant revision. Do not call an unrun test successful.
5. **Publish:** include relevant documentation in the existing PR when it belongs to the approved scope, and report the knowledge review in the PR checklist. For a research-only request or an unrelated change, propose a focused documentation follow-up instead of mutating the repository without authorization. A pending learning candidate belongs in the existing issue until evidence is sufficient.
6. **Reuse and refresh:** future tasks read the relevant articles and update or remove obsolete knowledge when evidence changes. Do not silently rely on historical plans or stale examples.

Review is mandatory for non-trivial delivery, but new documentation is conditional. If nothing reusable changed, record a brief reason; do not generate filler. Knowledge maintenance does not bypass approval or reopen frozen implementation scope. If a new lesson is unrelated or discovered after the final CI cycle starts, record it for a focused follow-up rather than repeatedly restarting CI.

## Where each fact belongs

- Normative product requirements: the specification repository. Update it through its own approved process, not through a developer guide.
- Current implementation and behavior: source, tests, generated contracts and configuration. The guide explains the implications and links to them.
- Engineering rules: applicable AGENTS.md or CONTRIBUTING.md. Mandatory process changes require maintainer approval.
- Reusable agent procedures: the relevant skill. Human-facing explanations belong in the shared documentation, linked from the skill when helpful.
- Reusable engineering explanations and runbooks: the owning document in the knowledge index.
- Task-specific state, unvalidated hypotheses and review evidence: issue or PR; do not promote these to established knowledge.
- Long-lived approved design decisions: a focused decision record where rationale cannot be explained adequately in an existing guide.

Prefer references over copies. If a developer guide and an instruction disagree, identify the conflict and resolve it through the owning artifact. Never use a guide to weaken an existing instruction or change product behavior.

## Article quality

Write for someone unfamiliar with the original conversation. A useful article usually includes the problem or context, the current mechanism, why it works, how to apply it, relevant failure modes or limitations, and source or verification links. Include a small code example or diagram only when it clarifies the explanation. Use descriptive headings and stable terminology that also make the topic searchable by an agent.

For example, a formatter failure is not a useful permanent lesson by itself. The reusable insight is that a repository-wide formatter can report files outside the intended change. A troubleshooting article can explain how to identify pre-existing differences, use the canonical patch, and keep the repair separately reviewable. Link to the workflow and the incident when evidence supports the conclusion.

Keep current-source links relative where possible so they resolve with the checked-out revision. Use immutable commit or release links for historical evidence that must not move. Do not assert a permanent validation date or status without the evidence to support it. Explicitly identify unverified limitations.

## Governance

Any contributor may propose or correct evidence-backed documentation within approved scope. A document is not an authorization grant or a substitute for required checks. Do not introduce normative policy changes or new security requirements without approval. Prefer a deterministic test for a repeatable invariant, and a human-readable explanation for why it matters.

Review the substance in the existing PR. No separate database, vector store, per-task memory file, or model-specific knowledge format is required.
