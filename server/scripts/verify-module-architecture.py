#!/usr/bin/env python3
"""Verify the normative v1.0.0-alpha.9 server module dependency boundaries."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

SERVER = Path(__file__).resolve().parents[1]
MODULES = SERVER / "modules"

ALLOWED_PROJECT_DEPENDENCIES = {
    "foundation": set(),
    "language": {":modules:foundation"},
    "database": {":modules:foundation"},
    "query": {":modules:foundation", ":modules:language"},
    "authorization": {":modules:foundation", ":modules:language"},
    "identity": {
        ":modules:foundation",
        ":modules:language",
        ":modules:query",
        ":modules:authorization",
        ":modules:database",
    },
}

PROJECT_DEPENDENCY = re.compile(r"project\(\s*[\"']([^\"']+)[\"']\s*\)")
FOUNDATION_PROHIBITED_BUILD_TOKENS = ("project(",)
FOUNDATION_PROHIBITED_SOURCE_TOKENS = (
    "package io.taskmigo.query",
    "package io.taskmigo.language",
    "package io.taskmigo.authorization",
    "package io.taskmigo.identity",
    "import org.springframework",
    "import jakarta.persistence",
    "import org.antlr",
)


def project_dependencies(module: str) -> set[str]:
    build_file = MODULES / module / "build.gradle.kts"
    if not build_file.is_file():
        raise FileNotFoundError(f"Missing module build file: {build_file.relative_to(SERVER)}")
    return set(PROJECT_DEPENDENCY.findall(build_file.read_text(encoding="utf-8")))


def verify_dependency_graph(errors: list[str]) -> None:
    graph: dict[str, set[str]] = {}
    for module, allowed in ALLOWED_PROJECT_DEPENDENCIES.items():
        declared = project_dependencies(module)
        prohibited = sorted(declared - allowed)
        if prohibited:
            errors.append(
                f"{module} declares prohibited project dependencies: {', '.join(prohibited)}"
            )
        graph[module] = {
            dependency.removeprefix(":modules:")
            for dependency in declared
            if dependency.startswith(":modules:")
            and dependency.removeprefix(":modules:") in ALLOWED_PROJECT_DEPENDENCIES
        }

    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(module: str, trail: tuple[str, ...]) -> None:
        if module in visiting:
            errors.append("Module dependency cycle: " + " -> ".join((*trail, module)))
            return
        if module in visited:
            return
        visiting.add(module)
        for dependency in sorted(graph[module]):
            visit(dependency, (*trail, module))
        visiting.remove(module)
        visited.add(module)

    for module in graph:
        visit(module, ())


def verify_foundation(errors: list[str]) -> None:
    build_file = MODULES / "foundation" / "build.gradle.kts"
    build_text = build_file.read_text(encoding="utf-8")
    for token in FOUNDATION_PROHIBITED_BUILD_TOKENS:
        if token in build_text:
            errors.append(f"foundation build contains prohibited framework dependency token: {token}")

    source_root = MODULES / "foundation" / "src"
    for source in source_root.rglob("*"):
        if not source.is_file() or source.suffix not in {".java", ".kt", ".kts"}:
            continue
        text = source.read_text(encoding="utf-8")
        for token in FOUNDATION_PROHIBITED_SOURCE_TOKENS:
            if token in text:
                errors.append(
                    f"foundation source {source.relative_to(SERVER)} contains prohibited token: {token}"
                )


def verify_named_modules(errors: list[str]) -> None:
    settings = (SERVER / "settings.gradle.kts").read_text(encoding="utf-8")
    required = {"foundation", "language", "query", "authorization", "identity", "database"}
    for module in sorted(required):
        if f'":modules:{module}"' not in settings:
            errors.append(f"settings.gradle.kts does not include required module: {module}")
    if '":modules:embedded-language"' in settings:
        errors.append("settings.gradle.kts still includes the superseded embedded-language module")
    if '":modules:auth"' in settings:
        errors.append("settings.gradle.kts still includes the superseded auth module")


def main() -> int:
    errors: list[str] = []
    try:
        verify_named_modules(errors)
        verify_dependency_graph(errors)
        verify_foundation(errors)
    except (OSError, UnicodeError) as exception:
        errors.append(str(exception))

    if errors:
        print("Module architecture verification failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    resolved = subprocess.run(
        ["./gradlew", "--no-daemon", "verifyResolvedModuleArchitecture", "--console=plain"],
        cwd=SERVER,
        check=False,
        capture_output=True,
        text=True,
    )
    if resolved.returncode != 0:
        print("Resolved module architecture verification failed:", file=sys.stderr)
        output = (resolved.stdout + "\n" + resolved.stderr).splitlines()
        for line in output[-200:]:
            print(line, file=sys.stderr)
        return resolved.returncode

    print("Module architecture verification passed for the static and resolved dependency graphs.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
