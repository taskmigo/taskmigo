---
name: adversarial-self-review
description: "Use adversarial self-review to find the strongest solution with the least necessary implementation surface and catch mistakes before finalizing work. Trigger whenever reviewing code or a pull request, reviewing your own implementation or output, comparing non-trivial design or implementation alternatives, or deciding whether a proposed fix is actually the best fit for the stated constraints. Uses one bounded pre-implementation challenge and one bounded post-implementation review, requires semantic-boundary proof before implementation, freezes scope after synthesis, resets only when material evidence contradicts a premise, actively searches for counterexamples, false positives, and behavior-equivalent smaller implementations, and reports only the synthesized rationale rather than hidden chain-of-thought."
---

# Adversarial Self-Review

Use a bounded internal debate to challenge the first plausible answer before treating it as the final answer.

The goal is not to produce more argument. The goal is to find the strongest solution supported by the available evidence and the actual constraints.

## When to use

Use this skill for:

- Every code review and pull-request review.
- Every meaningful self-review before declaring implementation work complete.
- Architecture, API, data-model, testing, tooling, or implementation choices with real trade-offs.
- Bug fixes where more than one root cause or repair strategy is plausible.
- Refactors where the proposed structure may only look cleaner while weakening behavior, compatibility, or operability.
- Any request to find the "best", "safest", "cleanest", "simplest", or otherwise preferred solution when several credible options exist.

Do not force a debate around trivial mechanical work that has no meaningful alternative or material risk.

## Core principles

1. **Do not defend the first answer.** Treat the initial proposal as a hypothesis to falsify.
2. **Separate proposal from critique.** The challenge pass should re-read the requirements and evidence instead of merely reacting to the proposal's rationale.
3. **Prefer evidence over intuition.** Source code, specifications, tests, logs, contracts, benchmarks, and repository conventions outrank stylistic preference.
4. **Distinguish fact from inference.** Mark assumptions and unresolved uncertainty internally; do not promote them to findings without support.
5. **Steelman alternatives.** Compare against the strongest credible alternative, not an intentionally weak one.
6. **Search for false positives too.** Try to disprove review findings before reporting them.
7. **Optimize for the actual objective.** "Best" means best under the stated constraints, not universally best.
8. **Minimize implementation surface, not readability.** After correctness is established, challenge whether the same behavior can be delivered with less new code, fewer branches, fewer abstractions, fewer changed files, or more reuse of existing primitives. Never trade away clarity, tests, validation, or required behavior merely to reduce line count.
9. **Keep the loop bounded.** More debate is not automatically better.
10. **Prove the runtime boundary before editing.** A framework capability or similarly named concept is not proof that the application boundary accepts it. Verify the exact artifact, owner, transport, scope, and consumer before mutating code.
11. **Freeze scope after synthesis.** Once the selected direction satisfies the stated acceptance criteria and hard constraints, treat adjacent discoveries as out of scope unless they block correctness, safety, compatibility, or the completeness of the selected fix.
12. **Review at checkpoints, not continuously.** Do not restart a full adversarial review after every edit, formatter change, test refactor, PR-body update, or CI status change. Review once before implementation and once after the coherent implementation is complete.
13. **Prefer seamless repairs over patches.** Functional correctness is necessary but not sufficient. A good fix should fit the surrounding architecture, naming, abstractions, dependency direction, and local idioms so naturally that the repaired code looks intentionally designed rather than visibly patched. If the smallest local change would leave duplicated branches, awkward exceptions, special-case plumbing, misplaced ownership, or a one-off abstraction that makes the codebase visibly uglier, reconsider the repair boundary before accepting it.

## Debate roles

Use three internal passes. They are reasoning responsibilities, not personas to role-play in the final response.

### Proposer

Build the strongest initial solution or review assessment from the available evidence.

### Challenger

Assume the proposal may be wrong. Try to break it with counterexamples, conflicting requirements, hidden costs, regression paths, and stronger alternatives.

### Synthesizer

Resolve the disagreement using evidence and constraints. Keep, modify, or reject the initial proposal and define the verification needed for the selected direction.

## Workflow

### 1. Establish ground truth

Before evaluating a solution, identify the evidence that constrains it:

- User requirements and explicit non-goals.
- Applicable repository instructions and specifications.
- Existing architecture and local conventions.
- Relevant source code and changed lines.
- Tests, static-analysis results, logs, schemas, API contracts, or benchmarks.
- Compatibility, migration, security, operational, and developer-experience constraints.

Do not start from a preferred pattern and work backward.

### 2. Frame the decision

Write down internally:

- The objective.
- Hard constraints.
- Important soft constraints.
- Success criteria.
- Known unknowns.
- What must remain unchanged.

For a code review, also identify the behavior the code is trying to preserve or introduce and the invariants that should hold.

Create a compact internal scope ledger before implementation:

- **Must fix:** behavior required by the user, issue acceptance criteria, specification, or a blocker introduced by the selected change.
- **Must preserve:** existing behavior, compatibility, architecture, security, or operational constraints that cannot regress.
- **Not in scope:** adjacent cleanup, speculative improvements, unrelated refactors, and pre-existing defects that are not required to make the requested change complete.

Do not add an item to **Must fix** merely because it was discovered during self-review. It must be necessary to satisfy the requested outcome or prevent a regression introduced by the change.

### 3. Prove semantic boundaries before implementation

Before changing code for a non-trivial design that crosses components, protocols, processes, framework layers, or runtime ownership boundaries, build a compact evidence map for the path being changed.

For each relevant hop, establish:

- **Producer:** which component creates the value or state.
- **Artifact:** its exact representation, not a generic label such as "session", "auth", "context", or "request".
- **Transport:** how it moves to the next component.
- **Scope and lifetime:** origin/domain/path/process/request/transaction/worker scope where relevant.
- **Consumer:** which component actually interprets it.
- **Accepted mechanism:** what that consumer is configured to accept.
- **Transformation:** any proxy, adapter, decoder, exchange, serialization, or credential conversion between producer and consumer.

Verify application facts from source, configuration, executable behavior, or contracts. Verify framework behavior from the repository's pinned version or authoritative documentation when that behavior is material.

Do not substitute a framework capability for application proof. Examples:

- "Playwright shares cookies between a BrowserContext and its request client" does not prove the target API authenticates the cookie that happens to be present.
- "The browser is logged in" does not prove a different server process or security filter accepts the browser's session state.
- "The same host is used" does not prove two layers share the same session implementation.
- "The parser returns a document" does not prove the returned version/type matches the contract the application generated.

For authentication and session work, explicitly distinguish at least the relevant items among:

- browser/BFF session cookies;
- server HTTP-session cookies;
- OAuth/OIDC authorization transactions;
- access tokens;
- refresh tokens;
- ID tokens;
- application session handles.

Never use the word "session" alone as evidence that two components share authentication state.

Resolve every **decision-blocking unknown** before repository mutation. If a decisive boundary cannot be proven, stop at a decision record describing the missing evidence rather than implementing a speculative bridge, proxy, adapter, or authentication flow.

### 4. Produce the first candidate

Create the best initial answer from the evidence.

For implementation work, this is the proposed design or fix.

For review work, this is the initial set of findings and the current assessment of whether the change satisfies its intended behavior.

Do not finalize it yet.

### 5. Run the adversarial challenge

Run this challenge once before implementation for a non-trivial change. Challenge the candidate independently. Do not repeat the full challenge after each repository mutation; collect implementation evidence and use the designated post-implementation review in step 9.

Ask the relevant questions rather than mechanically applying every category:

- Can a concrete input or state make this incorrect?
- Does it violate a documented contract, invariant, or repository rule?
- Does it silently change behavior outside the requested scope?
- Can failure leave data, state, or external effects partially applied?
- Are concurrency, ordering, retries, idempotency, or transaction boundaries relevant?
- Does it weaken authentication, authorization, validation, isolation, or information handling?
- Does it create API, schema, wire-format, or backward-compatibility risk?
- Does it add unnecessary abstraction, duplication, coupling, or maintenance burden?
- Can the same behavior and invariants be achieved with less new code or a smaller diff?
- Does any new helper, wrapper, class, branch, configuration, or layer duplicate behavior already provided by the language, framework, standard library, dependency, or repository?
- Is there a simpler solution that preserves the same guarantees?
- Is a performance claim measured, or only assumed?
- Can the design be operated and diagnosed when it fails?
- Are tests exercising the real failure boundary rather than merely the happy path?
- Could an apparent finding be intentional, unreachable, already guarded, or contradicted by stronger evidence?

For every potential review finding, attempt to construct a plausible disproof before reporting it.

### 6. Generate credible alternatives

For a non-trivial decision, identify at least one strong alternative.

For implementation work, one credible alternative should be a behavior-equivalent smaller implementation whenever one plausibly exists. Search in this order:

1. Delete code made unnecessary by the change.
2. Reuse an existing language, framework, library, or repository primitive.
3. Simplify control flow or data flow.
4. Remove a redundant helper, wrapper, layer, configuration point, or indirection.
5. Localize the fix to the violated invariant instead of broadening the design.

Other credible alternatives include moving validation or ownership to a better boundary, preserving the existing design and fixing only the violated invariant, deferring optimization until measurement exists, or doing nothing when the alleged problem is not supported by evidence.

Do not add alternatives solely to satisfy this step. They must be technically credible. Do not count compressed syntax, removed tests, weakened validation, or hidden complexity as a smaller implementation.

### 7. Compare using explicit criteria

Compare the candidate and credible alternatives against the decision criteria that actually matter.

Typical criteria include:

- Correctness and invariant preservation.
- Failure behavior and recoverability.
- Security.
- Compatibility.
- Simplicity.
- Implementation surface: new code, changed code, branches, abstractions, indirection, and files touched.
- Maintainability.
- Consistency with the existing architecture.
- Developer experience.
- Operability and observability.
- Testability.
- Performance when performance is materially relevant and supported by evidence.

Avoid fake precision. Numerical scores are unnecessary unless the problem genuinely provides measurable weights.

If one option is strictly worse on the material criteria, discard it. If the trade-off depends on an unresolved product or architectural preference, state that uncertainty instead of manufacturing certainty.

### 8. Apply the seamless-repair test

Before freezing the direction, evaluate the candidate as if the defect had never existed and the code were being designed correctly today.

Ask:

- Would this solution still be the natural design if there were no historical bug to patch around?
- Does the fix live at the invariant-owning boundary, or is it compensating elsewhere because that was easier to edit?
- Does it reuse the codebase's existing abstractions and dependency direction, or introduce a one-off path that future maintainers must remember?
- Does the resulting code read uniformly with its neighbors, without "except for this bug" branches, duplicated policy, adapter-specific workarounds, or unexplained asymmetry?
- If the changed code were shown without the issue history, would a reviewer reasonably believe it had been designed this way from the beginning?

A repair may be larger than the absolute minimum diff when the extra change is required to restore a coherent invariant and remove visible patchwork. Conversely, do not broaden the task merely to beautify unrelated pre-existing code. The target is the **smallest coherent repair**, not the fewest changed lines and not a general cleanup.

Use the tailoring metaphor internally: after repair, the "fabric" should look whole and purpose-made. If the functionality works but the repair leaves obvious seams, mismatched pieces, or accumulated patches in the affected design surface, the solution is not finished yet.

### 9. Synthesize and freeze the final direction

The Synthesizer must explicitly decide internally whether to:

- Keep the initial proposal.
- Modify it.
- Replace it with an alternative.
- Report that the evidence is insufficient for a confident decision.

The final recommendation should be traceable to decisive evidence and constraints.

For implementation work, this decision is the **scope freeze**. Record the selected invariant, the files or boundaries expected to change, and the acceptance criteria that will prove completion. From this point onward, do not reopen architecture exploration because of an interesting adjacent improvement.

For code review findings, report only findings that survive the challenge pass.

### 10. Verify the synthesized result

Before considering the review or implementation complete, challenge the synthesized result one more time with emphasis on regression risk:

- Re-read the actual changed files, not only the intended design.
- Check error and boundary cases.
- Check references affected by renames or moves.
- Check compatibility and migration paths where applicable.
- Check tests and static-analysis coverage.
- Check documentation and generated artifacts that can become stale.
- Check pull-request title, description, issue linkage, and checklist when repository instructions require them.
- Check for unrelated scope drift introduced while fixing review findings.
- Re-run the minimization challenge against the final diff: every new helper, abstraction, branch, layer, and configuration point should justify why a smaller behavior-equivalent implementation is worse.
- Run the seamless-repair test on the final diff: verify that the affected design surface has no new special-case seams, duplicated policy, ownership leaks, asymmetric handling, or workaround-shaped abstractions that exist only because of the bug.

Classify every finding from this post-implementation pass before changing code:

- **Blocker:** the requested behavior is still wrong, unsafe, incompatible, incomplete, or the current change introduced a regression. Fix it in one batch.
- **Required integration gap:** the selected change cannot work end to end without the adjacent change. Fix it in the same batch, then verify once more.
- **Adjacent improvement:** useful but not necessary for the requested outcome. Do not expand the current implementation; record it for follow-up when appropriate.
- **Style/minimization only:** apply it only if it is deterministic, low-risk, and does not trigger a new design/CI cycle.

After fixing blockers or required integration gaps, perform one targeted verification of the affected invariant. Do not restart the full proposer/challenger/synthesizer workflow unless new external evidence invalidates a material premise.

## Scope triage and freeze point

The most common self-review failure mode is turning discovery into scope expansion. Prevent it explicitly.

After synthesis:

1. **Freeze the problem statement.** The current task is defined by the user's request, issue acceptance criteria, specification, and regressions introduced by the chosen fix.
2. **Scan the complete execution surface once.** Before the first implementation push, inspect direct consumers, adapters, serializers, switch statements, persistence binders, generated artifacts, and tests that must understand the changed contract. This is where required integration gaps should be found.
3. **Batch required changes before CI.** Prefer one coherent implementation and one preflight over a sequence of speculative pushes.
4. **Triage later discoveries.** Fix only blockers and required integration gaps. Defer adjacent cleanup and unrelated pre-existing defects.
5. **Do not reopen a settled design for code minimization alone.** Once correctness is established, minimization may simplify the current diff but must not introduce a new architecture, abstraction family, or dependency direction.
6. **Stop when the acceptance criteria are satisfied and the affected surface is coherent.** A self-review is successful when it proves the requested behavior is correct and the repair integrates cleanly into the affected design surface, not when it exhausts every possible improvement elsewhere.

Examples of discoveries that normally stay out of scope after the freeze:

- a nearby class that could be renamed more cleanly;
- a pre-existing duplicated helper unrelated to the invariant;
- a broader abstraction that could unify several modules;
- an optional performance optimization without evidence of a regression;
- another issue found while reading adjacent code that is independently actionable.

Examples that break the freeze and must be fixed:

- the new public enum value cannot be consumed by an existing required adapter;
- the fix passes the validator but fails at the persistence or transport boundary it is supposed to reach;
- the implementation violates a specification or architecture rule;
- a regression test reveals the original bug still exists through another required path.

## Contradiction reset and implementation-churn guard

Treat new evidence that invalidates a material premise differently from an ordinary review finding.

When source code, runtime evidence, authoritative documentation, or a user correction contradicts a premise that the current design depends on:

1. **Stop editing immediately.** Do not patch around the contradiction.
2. **Invalidate downstream conclusions.** Any design choice derived from the false premise must be reconsidered, even if parts of the implementation still compile.
3. **Return to ground truth and semantic-boundary proof.** Re-run Workflow steps 1–3 for the affected path.
4. **Remove superseded scaffolding before adding the replacement design.** Do not layer token flow, cookie flow, proxy flow, adapters, or wrappers on top of one another merely because earlier code already exists.
5. **Select one coherent design before resuming repository mutation.** State the decisive evidence internally and identify what was rejected.
6. **Preflight the complete change before pushing.** Re-read changed files, check references, and apply deterministic formatter/compiler feedback when available. Do not intentionally push transient compile-invalid or semantically contradictory states unless the user explicitly requested an exploratory prototype.

If two material design pivots occur in the same task, perform a mandatory full reset before a third attempt:

- reconstruct the end-to-end evidence map from source;
- list the premises that failed;
- identify the smallest remaining solution surface;
- verify that the new design does not depend on either rejected premise.

A user correction is evidence, not merely a requested syntax change. Revisit the assumption that produced the rejected implementation instead of only changing the visible code shape.

## Loop limit

Default budget for implementation work:

- one pre-implementation challenge/synthesis round;
- one post-implementation self-review;
- at most one targeted repair/verification pass for blockers or required integration gaps.

A second full challenge/synthesis round is allowed only when **new external evidence** invalidates a material premise: a user correction, failing runtime evidence, authoritative specification/documentation, or a concrete CI/test result. An internally discovered adjacent improvement is not sufficient reason to restart the full debate.

Stop when:

- No new material issue is discovered.
- The selected solution satisfies the hard constraints.
- Remaining trade-offs are understood.
- Required verification is defined or completed.
- Residual uncertainty is explicit.

Do not continue debating merely to produce more text.

## Code-review finding quality

A code-review finding should contain enough evidence to be actionable.

Before reporting one, establish:

- **Evidence:** the code, contract, test, log, or specification that supports it.
- **Trigger:** the concrete condition under which the problem appears.
- **Impact:** what becomes incorrect, unsafe, incompatible, or unnecessarily costly.
- **Direction:** the invariant or behavior the fix should restore; avoid prescribing a larger redesign unless necessary.
- **Confidence:** whether the evidence is direct or whether important uncertainty remains.

Use severity only when it helps prioritization:

- **Blocker:** cannot safely merge or ship without correction.
- **High:** material correctness, security, data, or compatibility risk.
- **Medium:** real defect or maintainability problem with bounded impact.
- **Low:** worthwhile improvement that does not materially threaten correctness.

Do not inflate severity to make a finding look important.

"No material finding" is a valid review result.

## Self-review before finalizing work

When reviewing your own work, assume implementation familiarity creates blind spots.

Re-evaluate from the perspective of a new reviewer:

1. Does the final diff solve the requested problem rather than the problem you drifted into solving?
2. Did the implementation preserve every stated constraint and non-goal?
3. Is there duplicated logic, a reimplemented library function, or a new abstraction that the repository already provides?
4. Did the fix move the failure elsewhere instead of restoring the underlying invariant?
5. Are edge cases and failure paths covered?
6. Are tests strong enough to fail on the original defect or rejected alternative?
7. Are comments, documentation, generated files, and PR metadata still accurate?
8. Can the same behavior, invariants, and test coverage be preserved with less code or a smaller diff after seeing the complete implementation?
9. Does every new helper, abstraction, branch, layer, or configuration point earn its complexity compared with reusing or deleting code?
10. Can every cross-component assumption in the final design be traced to direct evidence about the exact artifact and consumer, rather than a framework feature or a similarly named concept?
11. Did any user correction or new evidence invalidate an earlier premise? If so, was all dependent implementation reconsidered rather than incrementally patched?
12. Is the branch free of superseded scaffolding from rejected designs and transient states that no longer serve the selected direction?
13. Has every new discovery been classified as blocker, required integration gap, adjacent improvement, or style/minimization only?
14. Am I about to expand scope because the new idea is better in general, or because the current task is actually incomplete without it?
15. Have I completed one full execution-surface scan so I do not discover obvious consumers only after starting CI?
16. Does the final code look like a coherent design, or like a sequence of patches accumulated around the defect?
17. If I removed the issue history, would the chosen ownership, abstractions, naming, and control flow still make sense on their own?
18. Did I choose the smallest coherent repair rather than either the smallest possible diff or an unnecessarily broad cleanup?

Apply the same evidence standard to your own implementation as to someone else's.

## Output discipline

The debate is an internal quality-control process.

Do not dump a role-play transcript or hidden chain-of-thought into the final response. Present the useful result:

- The selected direction.
- The decisive evidence.
- The material trade-offs.
- Surviving review findings.
- Residual risks or uncertainty.
- Verification performed or still required.

If the user explicitly asks to see the competing arguments, provide a concise decision record containing the strongest arguments and evidence for each option, not a private reasoning transcript.
