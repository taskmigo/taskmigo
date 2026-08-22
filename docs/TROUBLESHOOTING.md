# Troubleshooting

Environment-specific issues encountered while working on this repository. Check
this file before installing dependencies or running verification gates.

## Verification tools are missing in a fresh checkout

**Environment:** A fresh or ephemeral checkout where npm dependencies have not
been installed.

**Symptom:** `npm run lint:check` fails with `oxlint: not found` (and other npm
scripts may report missing locally installed tools).

**Workaround:** Install the locked dependencies with `npm ci` before starting the
verification sequence. After installation succeeds, restart the full sequence at
`npm run lint:check`.

## Format check fails after documentation edits

**Environment:** Any checkout after changing Markdown, MDX, JSON, YAML, or other
files covered by Prettier.

**Symptom:** `npm run format:check` lists files with formatting differences. MDX
Tables, JSX/Fumadocs components, wrapped prose, or embedded content may look
correct by inspection but still differ from Prettier output.

**Workaround:** Do not reproduce Prettier output manually. In a writable checkout
with locked dependencies installed, run:

```bash
npm run format:fix
```

Review the formatter-generated diff, then restart the complete verification
sequence from:

```bash
npm run lint:check
```

Continue with `format:check`, `types:check`, and `build` only after the preceding
gate passes. If the current environment cannot provide a writable checkout, move
the formatting step to another valid environment rather than guessing spacing or
rewriting MDX by hand.

## npm cannot write to its default cache

**Environment:** A restricted or containerized environment where `/root/.npm` is
not writable.

**Symptom:** `npm ci` fails while trying to write to the default npm cache and may
leave an incomplete `node_modules` directory. Subsequent verification commands
can then fail with missing or partially installed packages.

**Workaround:** Move the incomplete `node_modules` directory out of the checkout,
then reinstall from the lockfile with a writable temporary cache:

```bash
mv node_modules /tmp/taskmigo-docs-node_modules-incomplete
npm ci --cache /tmp/taskmigo-docs-npm-cache
```

Do not reuse the incomplete installation. After `npm ci` succeeds, restart the
full verification sequence at `npm run lint:check`.

## Local checkout cannot reach GitHub

**Environment:** A restricted container where outbound DNS for `github.com` is
unavailable.

**Symptom:** `git clone` fails with `Could not resolve host: github.com`, preventing
that environment from creating or refreshing a local checkout.

**Workaround:** Do not substitute manual formatting or skip verification. Use an
existing checkout when available, or move the work to another writable environment
that can obtain the repository and run the required commands. GitHub Actions can
verify a pushed head with `lint:check`, `format:check`, `types:check`, and `build`,
but a check-only workflow does not replace `npm run format:fix` when files need to
be rewritten. After any formatter or code fix, restart verification from
`npm run lint:check`.
