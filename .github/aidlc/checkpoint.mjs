const marker = "<!-- taskmigo-ai-dlc:v1 -->";
const stages = new Set(["inception", "approval", "construction", "verification", "ready"]);
const approvals = new Set(["pending", "session", "external", "not-required"]);
const positive = (value) => Number.isSafeInteger(value) && value > 0;
const nonempty = (value) => typeof value === "string" && value.trim().length > 0;
const object = (value) => value !== null && typeof value === "object" && !Array.isArray(value);
const utc = (value) =>
  typeof value === "string" &&
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(value) &&
  Number.isFinite(Date.parse(value)) &&
  new Date(value).toISOString().replace(".000Z", "Z") === value;

export function parseCheckpoint(body, issueNumber) {
  const lines = body.trim().split(/\r?\n/);
  if (lines.shift() !== marker || lines.shift() !== "" || lines.shift() !== "```json" || lines.pop() !== "```") {
    throw new Error("Expected checkpoint marker and a single JSON fence");
  }

  const state = JSON.parse(lines.join("\n"));
  if (!object(state) || !positive(state.issue) || state.issue !== issueNumber) {
    throw new Error("Checkpoint issue must match the event issue");
  }
  if (!nonempty(state.profile) || !stages.has(state.stage) || !approvals.has(state.approval) || !nonempty(state.next)) {
    throw new Error("Invalid profile, stage, approval, or next action");
  }
  if (
    state.proposal !== null &&
    (!object(state.proposal) || !positive(state.proposal.comment_id) || !utc(state.proposal.updated_at))
  ) {
    throw new Error("Invalid proposal reference");
  }
  if (state.approval !== "not-required" && state.proposal === null) {
    throw new Error("Approval requires a proposal reference");
  }
  if (state.stage === "approval" && state.approval !== "pending") {
    throw new Error("Approval stage requires pending approval");
  }
  if (state.approval === "pending" && !["inception", "approval"].includes(state.stage)) {
    throw new Error("Cannot advance construction or verification with pending approval");
  }
  if (
    (state.pr === null) !== (state.head === null) ||
    (state.pr !== null && (!positive(state.pr) || typeof state.head !== "string" || !/^[0-9a-f]{40}$/.test(state.head)))
  ) {
    throw new Error("PR and full head SHA must be provided together");
  }
  if (state.stage === "verification" && state.pr === null) {
    throw new Error("Verification requires a PR and head SHA");
  }
  return state;
}
