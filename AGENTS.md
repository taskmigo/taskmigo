# AGENTS.md

Instructions for AI agents and automated contributors working in this repository.

## Repository workflow

- Read this file before making repository changes.
- Read more specific `AGENTS.md` files in subdirectories when working within their scope.
- Keep changes focused and preserve unrelated work.
- Group related file changes into a logical chunk. Do not create or push one commit per file.
- Prefer one commit per completed chunk; squash temporary or mechanical commits before updating the working branch.

## Pull requests

- Before creating or updating a pull request, read `.github/pull_request_template.md` from the target branch.
- The pull request description must follow that template's section structure and checklist. Do not replace it with a custom structure.
- Keep the description synchronized with the actual implementation as the pull request evolves.
- Only check a checklist item when it is actually satisfied.
- Do not claim the work is complete until all required CI checks have passed.

## Verification

- Run or observe the repository's required verification for the affected areas before declaring completion.
- If CI fails, inspect the failing job, fix the cause, and continue until the required checks pass or an external blocker is identified.
