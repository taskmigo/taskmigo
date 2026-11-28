import { readFileSync } from "node:fs";
import { parseCheckpoint } from "./checkpoint.mjs";

const event = JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH, "utf8"));
try {
  parseCheckpoint(event.comment.body, event.issue.number);
  console.log("Checkpoint format valid; approval and CI evidence are not certified.");
} catch {
  console.error("Invalid AI-DLC checkpoint. Follow the checkpoint contract in docs/engineering/ai-dlc.md.");
  process.exitCode = 1;
}
