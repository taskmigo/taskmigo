---
name: adversarial-self-review
description: "Use adversarial self-review to find the strongest solution and catch mistakes before finalizing work. Trigger whenever reviewing code or a pull request, reviewing your own implementation or output, comparing non-trivial design or implementation alternatives, or deciding whether a proposed fix is actually the best fit for the stated constraints. Uses bounded proposer/challenger/synthesizer passes, requires evidence for findings, actively searches for counterexamples and false positives, and reports only the synthesized rationale rather than hidden chain-of-thought."
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
8. **Keep the loop bounded.** More debate is not automatically better.

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

### 3. Produce the first candidate

Create the best initial answer from the evidence.

For implementation work, this is the proposed design or fix.

For review work, this is the initial set of findings and the current assessment of whether the change satisfies its intended behavior.

Do not finalize it yet.

### 4. Run the adversarial challenge

Challenge the candidate independently.

Ask the relevant questions rather than mechanically applying every category:

- Can a concrete input or state make this incorrect?
- Does it violate a documented contract, invariant, or repository rule?
- Does it silently change behavior outside the requested scope?
- Can failure leave data, state, or external effects partially applied?
- Are concurrency, ordering, retries, idempotency, or transaction boundaries relevant?
- Does it weaken authentication, authorization, validation, isolation, or information handling?
- Does it create API, schema, wire-format, or backward-compatibility risk?
- Does it add unnecessary abstraction, duplication, coupling, or maintenance burden?
- Is there a simpler solution that preserves the same guarantees?
- Is a performance claim measured, or only assumed?
- Can the design be operated and diagnosed when it fails?
- Are tests exercising the real failure boundary rather than merely the happy path?
- Could an apparent finding be intentional, unreachable, already guarded, or contradicted by stronger evidence?

For every potential review finding, attempt to construct a plausible disproof before reporting it.

### 5. Generate credible alternatives

For a non-trivial decision, identify at least one strong alternative.

Possible alternatives include:

- A smaller change.
- Reusing an existing abstraction instead of adding one.
- Moving validation or ownership to a different boundary.
- Preserving the existing design and fixing only the violated invariant.
- Deferring optimization until measurement exists.
- Doing nothing when the alleged problem is not supported by evidence.

Do not add alternatives solely to satisfy this step. They must be technically credible.

### 6. Compare using explicit criteria

Compare the candidate and credible alternatives against the decision criteria that actually matter.

Typical criteria include:

- Correctness and invariant preservation.
- Failure behavior and recoverability.
- Security.
- Compatibility.
- Simplicity.
- Maintainability.
- Consistency with the existing architecture.
- Developer experience.
- Operability and observability.
- Testability.
- Performance when performance is materially relevant and supported by evidence.

Avoid fake precision. Numerical scores are unnecessary unless the problem genuinely provides measurable weights.

If one option is strictly worse on the material criteria, discard it. If the trade-off depends on an unresolved product or architectural preference, state that uncertainty instead of manufacturing certainty.

### 7. Synthesize the final direction

The Synthesizer must explicitly decide internally whether to:

- Keep the initial proposal.
- Modify it.
- Replace it with an alternative.
- Report that the evidence is insufficient for a confident decision.

The final recommendation should be traceable to decisive evidence and constraints.

For code review findings, report only findings that survive the challenge pass.

### 8. Verify the synthesized result

Before considering the review or implementation complete, challenge the synthesized result one more time with emphasis on regression risk:

- Re-read the actual changed files, not only the intended design.
- Check error and boundary cases.
- Check references affected by renames or moves.
- Check compatibility and migration paths where applicable.
- Check tests and static-analysis coverage.
- Check documentation and generated artifacts that can become stale.
- Check pull-request title, description, issue linkage, and checklist when repository instructions require them.
- Check for unrelated scope drift introduced while fixing review findings.

If this second pass discovers a material issue, revise once and repeat the verification.

## Loop limit

Use at most two full challenge/synthesis rounds unless new external evidence appears.

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
8. Would a smaller or more conventional solution now be better after seeing the complete diff?

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
