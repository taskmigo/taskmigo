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
    files: ["src/domains/**/*.{ts,tsx}", "src/server/bff/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-properties": [
        "error",
        {
          object: "process",
          property: "env",
          message: "Read environment configuration through @/server/config.",
        },
      ],
    },
  },
  {
    files: ["src/app/**/*.{ts,tsx}", "src/server/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: [
                "@/domains/*/auth",
                "@/domains/*/auth/**",
                "@/domains/*/http/**",
                "**/domains/*/auth/**",
                "**/domains/*/http/**",
              ],
              message: "Use the domain's public entry points instead of its internal implementation.",
            },
          ],
        },
      ],
    },
  },
  {
    files: ["src/domains/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/app", "@/app/**", "@/server/bff", "@/server/bff/**", "**/server/bff/**"],
              message: "Domains must not depend on Next.js route entry points or BFF transport.",
            },
          ],
        },
      ],
    },
  },
  globalIgnores([".next/**", "coverage/**", "out/**", "build/**", "next-env.d.ts"]),
]);
