import assert from "node:assert/strict";
import test from "node:test";
import { parseCheckpoint } from "./checkpoint.mjs";

const proposal = { comment_id: 456, updated_at: "2026-01-01T00:00:00Z" };
const base = {
  issue: 123,
  profile: "feature",
  stage: "approval",
  proposal,
  approval: "pending",
  pr: null,
  head: null,
  next: "Obtain approval",
};
const head = "a".repeat(40);
const comment = (state) => "<!-- taskmigo-ai-dlc:v1 -->\n\n```json\n" + JSON.stringify(state) + "\n```";

test("accepts a pending proposal and a research-only completion", () => {
  assert.deepEqual(parseCheckpoint(comment(base), 123), base);
  const research = { ...base, profile: "poc", stage: "ready", proposal: null, approval: "not-required" };
  assert.deepEqual(parseCheckpoint(comment(research), 123), research);
});

test("accepts verification metadata without certifying approval or checks", () => {
  const state = { ...base, stage: "verification", approval: "session", pr: 321, head };
  assert.deepEqual(parseCheckpoint(comment(state), 123), state);
});

test("rejects malformed or inconsistent checkpoints", () => {
  for (const change of [
    { issue: 124 },
    { profile: "" },
    { stage: "complete" },
    { approval: "approved" },
    { proposal: null },
    { proposal: { ...proposal, updated_at: "2026-02-30T00:00:00Z" } },
    { stage: "construction" },
    { stage: "approval", approval: "session" },
    { stage: "verification", approval: "session" },
    { pr: 321 },
    { pr: 321, head: "deadbeef" },
    { next: "" },
  ]) {
    assert.throws(() => parseCheckpoint(comment({ ...base, ...change }), 123));
  }
  assert.throws(() => parseCheckpoint("not a checkpoint", 123));
  assert.throws(() => parseCheckpoint(comment(base) + "\nextra", 123));
});
