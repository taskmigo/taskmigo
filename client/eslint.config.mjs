import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import unicorn from "eslint-plugin-unicorn";

export default defineConfig([
  ...nextVitals,
  ...nextTs,
  unicorn.configs.recommended,
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      parserOptions: {
        projectService: true,
      },
    },
    rules: {
      "@typescript-eslint/no-deprecated": "error",
      curly: ["error", "all"],
      "no-restricted-syntax": [
        "error",
        {
          selector: "PropertyDefinition[accessibility='private']",
          message: "Use ECMAScript #private fields instead of TypeScript private fields.",
        },
        {
          selector: "MethodDefinition[accessibility='private']",
          message: "Use ECMAScript #private methods instead of TypeScript private methods.",
        },
        {
          selector: "TSParameterProperty[accessibility='private']",
          message: "Use an ECMAScript #private field instead of a TypeScript private parameter property.",
        },
      ],
    },
  },
  {
    files: ["src/auth.ts", "src/auth/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-properties": [
        "error",
        {
          object: "process",
          property: "env",
          message: "Read environment configuration through @/config/server.",
        },
      ],
    },
  },
  {
    files: ["src/app/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          paths: [
            {
              name: "@/auth/core",
              message: "Application code must use the @/auth facade.",
            },
            {
              name: "@/auth/openid-client",
              message: "Application code must use the @/auth facade.",
            },
            {
              name: "@/config/server",
              message: "Application code must use the @/auth facade instead of auth configuration internals.",
            },
          ],
          patterns: [
            {
              group: ["@/auth/*", "**/auth/runtime"],
              message: "Import authentication through @/auth; runtime internals are composition-only.",
            },
          ],
        },
      ],
    },
  },
  globalIgnores([".next/**", "coverage/**", "out/**", "build/**", "next-env.d.ts"]),
]);
