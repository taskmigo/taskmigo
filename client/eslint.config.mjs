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
  globalIgnores([".next/**", "out/**", "build/**", "next-env.d.ts"]),
]);
