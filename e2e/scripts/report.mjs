import { existsSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { pathToFileURL } from "node:url";

const finalResultStatus = (results = []) => {
  const status = results.at(-1)?.status;
  if (status === "passed") return "passed";
  if (status === "skipped") return "skipped";
  return status ? "failed" : "skipped";
};

const normalizedStatus = (test) => {
  if (test.status === "expected") return "passed";
  if (test.status === "unexpected") return "failed";
  if (test.status === "flaky") return "flaky";
  if (test.status === "skipped") return "skipped";
  return finalResultStatus(test.results);
};

export const collectTests = (report) => {
  const collected = new Map();

  const visit = (suite, parents = []) => {
    const titles = suite.title ? [...parents, suite.title] : parents;

    for (const spec of suite.specs ?? []) {
      for (const test of spec.tests ?? []) {
        const title = [...titles, spec.title].filter(Boolean).join(" › ");
        const project = test.projectName || "default";
        const file = spec.file || suite.file || "unknown";
        const id = `${project} :: ${file} :: ${title}`;
        collected.set(id, {
          id,
          status: normalizedStatus(test),
          tags: [...new Set(spec.tags ?? [])].sort(),
        });
      }
    }

    for (const child of suite.suites ?? []) visit(child, titles);
  };

  for (const suite of report.suites ?? []) visit(suite);
  return collected;
};

const countsFor = (tests) => {
  const counts = { total: tests.length, passed: 0, failed: 0, flaky: 0, skipped: 0 };
  for (const test of tests) counts[test.status] += 1;
  return counts;
};

const escapeCell = (value) => String(value).replaceAll("|", "\\|").replaceAll("\n", " ");

const summaryTable = (counts) => [
  "| Total | Passed | Failed | Flaky | Skipped |",
  "| ---: | ---: | ---: | ---: | ---: |",
  `| ${counts.total} | ${counts.passed} | ${counts.failed} | ${counts.flaky} | ${counts.skipped} |`,
].join("\n");

const tagTable = (tests) => {
  const tags = new Map();
  for (const test of tests) {
    for (const tag of test.tags) {
      const tagged = tags.get(tag) ?? [];
      tagged.push(test);
      tags.set(tag, tagged);
    }
  }
  if (tags.size === 0) return "_No feature tags were reported._";

  return [
    "| Tag | Tests | Passed | Failed | Flaky | Skipped |",
    "| --- | ---: | ---: | ---: | ---: | ---: |",
    ...[...tags.entries()].sort(([left], [right]) => left.localeCompare(right)).map(([tag, tagged]) => {
      const counts = countsFor(tagged);
      return `| \`${escapeCell(tag)}\` | ${counts.total} | ${counts.passed} | ${counts.failed} | ${counts.flaky} | ${counts.skipped} |`;
    }),
  ].join("\n");
};

const changedTests = (current, baseline) => {
  const added = [...current.values()].filter(({ id }) => !baseline.has(id));
  const removed = [...baseline.values()].filter(({ id }) => !current.has(id));
  const changed = [...current.values()]
    .filter(({ id, status }) => baseline.has(id) && baseline.get(id).status !== status)
    .map((test) => ({ ...test, previousStatus: baseline.get(test.id).status }));
  return { added, removed, changed };
};

const detailList = (title, tests, format) => {
  if (tests.length === 0) return [];
  const visible = tests.slice(0, 20);
  const lines = ["", `**${title}**`, "", ...visible.map(format)];
  if (tests.length > visible.length) lines.push(`- …and ${tests.length - visible.length} more.`);
  return lines;
};

export const createMarkdown = (currentReport, baselineReport, options = {}) => {
  const current = collectTests(currentReport);
  const tests = [...current.values()];
  const counts = countsFor(tests);
  const result = counts.failed > 0 ? "❌ Failed" : counts.flaky > 0 ? "⚠️ Flaky" : "✅ Passed";
  const lines = [
    "## E2E report",
    "",
    options.runUrl ? `[${result} workflow run](${options.runUrl})` : result,
    "",
    summaryTable(counts),
    "",
    "### Feature tags",
    "",
    tagTable(tests),
    "",
    "### Baseline diff",
    "",
  ];

  if (!baselineReport) {
    lines.push(
      "No compatible report from a successful run on the target branch is available yet. This run will become a future baseline after the workflow succeeds.",
    );
    return `${lines.join("\n")}\n`;
  }

  const baseline = collectTests(baselineReport);
  const diff = changedTests(current, baseline);
  const baselineLabel = options.baselineUrl
    ? `[latest successful target-branch report](${options.baselineUrl})`
    : "latest successful target-branch report";
  lines.push(
    `Compared with the ${baselineLabel}: **${diff.added.length} added**, **${diff.removed.length} removed**, **${diff.changed.length} status changes**.`,
  );

  if (diff.added.length + diff.removed.length + diff.changed.length === 0) {
    lines.push("", "No test inventory or status changes.");
  }

  lines.push(
    ...detailList("Added", diff.added, (test) => `- \`${escapeCell(test.id)}\` — ${test.status}`),
    ...detailList("Removed", diff.removed, (test) => `- \`${escapeCell(test.id)}\``),
    ...detailList(
      "Status changes",
      diff.changed,
      (test) => `- \`${escapeCell(test.id)}\`: ${test.previousStatus} → ${test.status}`,
    ),
  );

  return `${lines.join("\n")}\n`;
};

const parseArguments = (values) => {
  const argumentsByName = {};
  for (let index = 0; index < values.length; index += 2) {
    const name = values[index];
    const value = values[index + 1];
    if (!name?.startsWith("--") || value === undefined) {
      throw new Error(`Invalid argument near ${name ?? "<end>"}`);
    }
    argumentsByName[name.slice(2)] = value;
  }
  return argumentsByName;
};

const readJson = async (path) => JSON.parse(await readFile(path, "utf8"));

const main = async () => {
  const argumentsByName = parseArguments(process.argv.slice(2));
  if (!argumentsByName.current || !argumentsByName.output) {
    throw new Error("--current and --output are required");
  }

  let markdown;
  if (!existsSync(argumentsByName.current)) {
    markdown = [
      "## E2E report",
      "",
      "⚪ The browser suite did not produce a Playwright JSON report. Inspect the deployment and test steps in this workflow run.",
      "",
    ].join("\n");
  } else {
    const current = await readJson(argumentsByName.current);
    const baseline =
      argumentsByName.baseline && existsSync(argumentsByName.baseline)
        ? await readJson(argumentsByName.baseline)
        : undefined;
    markdown = createMarkdown(current, baseline, {
      runUrl: argumentsByName["run-url"],
      baselineUrl: argumentsByName["baseline-url"],
    });
  }

  await mkdir(dirname(argumentsByName.output), { recursive: true });
  await writeFile(argumentsByName.output, markdown);
};

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await main();
}
