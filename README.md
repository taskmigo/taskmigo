# Taskmigo

Taskmigo is an open-source project management platform and a modern alternative to Redmine.

This repository is the main development home for Taskmigo. It contains the application source, deployment resources, tests, and documentation.

## Documentation

Project documentation is maintained in [`docs/`](docs/). Use it for product documentation, development guides, architecture, deployment, and other detailed project information.

## Contributing

Contributions are welcome. Before making a change, read [`CONTRIBUTING.md`](CONTRIBUTING.md) for repository-wide development requirements, verification steps, and pull request guidelines.

Please keep contributions focused, add or update tests and documentation when applicable, and ensure the relevant checks pass before requesting review.

Repository-specific instructions for automated contributors are documented in [`AGENTS.md`](AGENTS.md).

## Repository

Taskmigo is developed as a monorepo. The main areas are:

- [`client/`](client/) — Client application.
- [`server/`](server/) — Server applications and shared server code.
- [`docs/`](docs/) — Project documentation.
- [`helm/`](helm/) and [`deploy/`](deploy/) — Deployment resources.
- [`e2e/`](e2e/) — End-to-end tests.

Detailed implementation and operational information intentionally lives outside this README so the repository landing page remains concise and stable.
