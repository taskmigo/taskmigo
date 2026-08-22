# Taskmigo documentation

This directory contains the Taskmigo documentation site, built with Next.js and Fumadocs.

## Local development

From this directory, install the locked dependencies and start the development server:

```bash
npm ci
npm run dev
```

Open <http://localhost:3000> in your browser.

## Project layout

- `src/` — Next.js application and shared components.
- `content/` — Versioned documentation content.
- `src/lib/source.ts` — Content source adapter and collection definitions.
- `src/lib/layout.shared.tsx` — Shared layout utilities.

## Useful routes

| Route                   | Description                           |
| ----------------------- | ------------------------------------- |
| `src/app/(home)`        | Landing page and home routes          |
| `src/app/versions`      | Versioned documentation pages         |
| `src/app/llms.txt`      | Plain-text documentation index        |
| `src/app/llms-full.txt` | Complete plain-text documentation     |
| `src/app/og/versions`   | Open Graph images for versioned pages |

The root `.github/workflows/docs.yml` workflow verifies documentation changes in pull requests and deploys the static
export to GitHub Pages after documentation changes are merged into `next`.

## Verification

Run the gates in order:

```bash
npm run lint:check
npm run format:check
npm run types:check
npm run build
```

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) before installing dependencies or running verification in a new
environment.

For framework guidance, see the [Next.js documentation](https://nextjs.org/docs) and
[Fumadocs documentation](https://fumadocs.dev).
