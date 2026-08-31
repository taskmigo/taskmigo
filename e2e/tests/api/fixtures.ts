import { expect, test as base } from "@playwright/test";

import { TaskmigoApi } from "./client.js";
import { TestDataScope } from "./cleanup.js";

export { expect };

export const test = base.extend<{ api: TaskmigoApi }>({
  api: async ({ request }, use) => {
    const api = new TaskmigoApi(request, new TestDataScope());
    try {
      await use(api);
    } finally {
      await api.cleanupOwnedData();
    }
  },
});
