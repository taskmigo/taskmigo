import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    setupFiles: ["./test/setup.ts"],
    coverage: {
      provider: "v8",
      include: ["packages/auth/src/**/*.ts", "packages/config/src/**/*.ts", "src/auth.ts", "src/auth/**/*.ts"],
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
