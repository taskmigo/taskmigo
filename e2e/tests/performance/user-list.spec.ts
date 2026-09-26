import { randomUUID } from "node:crypto";

import { expect, test } from "#taskmigo-sdk";

const MAXIMUM_PAGE_SIZE = 100;
const SAMPLE_COUNT = 7;
const MAXIMUM_PAGE_SLOWDOWN_FACTOR = 10;
const MAXIMUM_PAGE_FIXED_OVERHEAD_MS = 20;

const median = (values: number[]): number => {
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.floor(sorted.length / 2)] ?? 0;
};

test.describe("User list performance", { tag: ["@performance", "@users"] }, () => {
  test("keeps maximum-page server execution time bounded", async ({ taskmigo }, testInfo) => {
    test.slow();
    await taskmigo.signIn();

    await taskmigo.api.v0.users.createMany(
      Array.from({ length: MAXIMUM_PAGE_SIZE + 2 }, (_, index) => {
        const suffix = `${index}-${randomUUID()}`;
        return {
          username: `e2e-user-${suffix}`,
          emails: [`e2e-user-${suffix}@example.com`],
          firstName: "E2E",
          lastName: "User",
        };
      }),
    );

    await test.step("Warm up request shapes", async () => {
      await taskmigo.api.v0.users.list({ pageSize: 1 });
      await taskmigo.api.v0.users.list({ pageSize: MAXIMUM_PAGE_SIZE });
    });

    const singleItemDurations: number[] = [];
    const maximumPageDurations: number[] = [];
    await test.step("Collect server execution samples", async () => {
      for (let sample = 0; sample < SAMPLE_COUNT; sample += 1) {
        singleItemDurations.push((await taskmigo.api.v0.users.list({ pageSize: 1 })).meta.execution.duration);
        maximumPageDurations.push(
          (await taskmigo.api.v0.users.list({ pageSize: MAXIMUM_PAGE_SIZE })).meta.execution.duration,
        );
      }
    });

    const singleItemMedian = median(singleItemDurations);
    const maximumPageMedian = median(maximumPageDurations);
    await testInfo.attach("server-execution-timings.json", {
      body: JSON.stringify(
        {
          maximumPageSize: MAXIMUM_PAGE_SIZE,
          singleItemDurations,
          maximumPageDurations,
          singleItemMedian,
          maximumPageMedian,
        },
        null,
        2,
      ),
      contentType: "application/json",
    });

    expect(maximumPageMedian).toBeLessThanOrEqual(
      singleItemMedian * MAXIMUM_PAGE_SLOWDOWN_FACTOR + MAXIMUM_PAGE_FIXED_OVERHEAD_MS,
    );
  });
});
