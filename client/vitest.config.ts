import { fileURLToPath } from "node:url";

import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("src", import.meta.url)),
    },
  },
  test: {
    setupFiles: ["./test/setup.ts"],
    coverage: {
      provider: "v8",
      include: [
        "packages/auth/src/**/*.ts",
        "packages/config/src/**/*.ts",
        "packages/foundation/src/**/*.ts",
        "src/auth/**/*.ts",
        "src/app/api/auth/**/route.ts",
      ],
      exclude: ["**/*.test.ts", "**/*.test.tsx"],
      reporter: ["text"],
      thresholds: {
        perFile: true,
        statements: 100,
        branches: 100,
        functions: 100,
        lines: 100,
      },
    },
  },
});
