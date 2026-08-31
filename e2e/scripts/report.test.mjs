import assert from "node:assert/strict";
import test from "node:test";

import { collectTests, createMarkdown } from "./report.mjs";

const report = (specifications) => ({
  suites: [
    {
      title: "auth",
      specs: specifications.map(({ title, status, tags = ["@auth"] }) => ({
        title,
        file: `tests/auth/${title}.spec.ts`,
        tags,
        tests: [{ projectName: "chromium", status, results: [] }],
      })),
    },
  ],
});

test("collectTests keeps stable identity, status, and feature tags", () => {
  const tests = [...collectTests(report([{ title: "login", status: "expected" }])).values()];

  assert.deepEqual(tests, [
    {
      id: "chromium :: tests/auth/login.spec.ts :: auth › login",
      status: "passed",
      tags: ["@auth"],
    },
  ]);
});

test("createMarkdown reports additions, removals, and status changes", () => {
  const baseline = report([
    { title: "login", status: "expected" },
    { title: "removed", status: "expected" },
  ]);
  const current = report([
    { title: "login", status: "unexpected" },
    { title: "session", status: "expected", tags: ["@auth", "@session"] },
  ]);

  const markdown = createMarkdown(current, baseline, {
    runUrl: "https://example.test/current",
    baselineUrl: "https://example.test/baseline",
  });

  assert.match(markdown, /1 added/);
  assert.match(markdown, /1 removed/);
  assert.match(markdown, /1 status changes/);
  assert.match(markdown, /passed → failed/);
  assert.match(markdown, /`@session`/);
});
