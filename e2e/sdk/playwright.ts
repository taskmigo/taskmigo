import { expect, test as base } from "@playwright/test";

import { taskmigoConfigFromEnvironment } from "./config.js";
import { Taskmigo } from "./taskmigo.js";

interface TaskmigoFixtures {
  taskmigo: Taskmigo;
}

export const test = base.extend<TaskmigoFixtures>({
  taskmigo: async ({ context, page }, use) => {
    await use(new Taskmigo(page, context, taskmigoConfigFromEnvironment()));
  },
});

export { expect };
