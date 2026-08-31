import { expect, test as base } from "@playwright/test";

import { TaskmigoApi } from "./client.js";

export { expect };

export const test = base.extend<{ api: TaskmigoApi }>({
  api: async ({ request }, use) => {
    await use(new TaskmigoApi(request));
  },
});
