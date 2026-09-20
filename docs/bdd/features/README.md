# BDD Features

Store business feature files in this directory.

Use one subdirectory per business domain:

```text
features/
└── <business-domain>/
    └── <capability>.feature
```

Each scenario must follow the conventions in [Business BDD](../README.md), including a stable `@BDD-<DOMAIN>-<NNN>` identifier.

## Feature index

No business feature files have been added yet.

When adding the first scenario for a domain, create the domain directory and add its `.feature` file in the same pull request as the behavior or acceptance-criteria change that requires it.
