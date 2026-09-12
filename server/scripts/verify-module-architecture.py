#!/usr/bin/env python3
"""Verify the normative v1.0.0-alpha.10 server module and build boundaries."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

SERVER = Path(__file__).resolve().parents[1]
MODULES = SERVER / "modules"
BUILD_LOGIC = SERVER / "build-logic"

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
EXPECTED_TOOL_VERSIONS = {
    "spring-modulith": "2.1.0",
    "archunit": "1.4.2",
    "errorprone-gradle-plugin": "5.1.0",
    "errorprone-core": "2.50.0",
    "nullaway": "0.14.0",
    "jspecify": "1.0.1",
    "checkstyle": "14.1.0",
    "spotless": "8.10.1",
}
EXPECTED_CONVENTIONS = {
    "modules/foundation/build.gradle.kts": "taskmigo.java-library",
    "modules/database/build.gradle.kts": "taskmigo.java-library",
    "modules/language/build.gradle.kts": "taskmigo.spring-module",
    "modules/query/build.gradle.kts": "taskmigo.spring-module",
    "modules/authorization/build.gradle.kts": "taskmigo.spring-module",
    "modules/identity/build.gradle.kts": "taskmigo.spring-module",
    "benchmarks/authorization/build.gradle.kts": "taskmigo.java-base",
    "apps/bootstrap/build.gradle.kts": "taskmigo.spring-application",
    "apps/web/build.gradle.kts": "taskmigo.spring-application",
    "apps/worker/build.gradle.kts": "taskmigo.spring-application",
}
EXPECTED_ARCHITECTURE_TEST_CONVENTIONS = {
    "modules/identity/build.gradle.kts",
    "apps/web/build.gradle.kts",
}
REDUNDANT_CONVENTION_DEPENDENCIES = (
    "libs.jspecify",
    "libs.spring.modulith.bom",
    "libs.spring.modulith.starter.core",
    "libs.spring.modulith.starter.test",
    "libs.archunit.junit5",
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


def verify_build_conventions(errors: list[str]) -> None:
    settings = (SERVER / "settings.gradle.kts").read_text(encoding="utf-8")
    if 'includeBuild("build-logic")' not in settings:
        errors.append("settings.gradle.kts does not include the build convention layer")

    required_build_logic_files = {
        "settings.gradle.kts",
        "build.gradle.kts",
        "src/main/kotlin/taskmigo.java-base.gradle.kts",
        "src/main/kotlin/taskmigo.java-library.gradle.kts",
        "src/main/kotlin/taskmigo.spring-module.gradle.kts",
        "src/main/kotlin/taskmigo.spring-application.gradle.kts",
        "src/main/kotlin/taskmigo.architecture-test.gradle.kts",
    }
    for relative_path in sorted(required_build_logic_files):
        if not (BUILD_LOGIC / relative_path).is_file():
            errors.append(f"missing build convention file: build-logic/{relative_path}")

    catalog = (SERVER / "gradle/libs.versions.toml").read_text(encoding="utf-8")
    for name, expected in EXPECTED_TOOL_VERSIONS.items():
        if not re.search(rf"^{re.escape(name)}\s*=\s*\"{re.escape(expected)}\"$", catalog, re.MULTILINE):
            errors.append(f"libs.versions.toml does not pin {name} to {expected}")

    wrapper = (SERVER / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    if "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.7.1-bin.zip" not in wrapper:
        errors.append("Gradle wrapper is not pinned to 9.7.1")
    if "distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a" not in wrapper:
        errors.append("Gradle wrapper distribution checksum is not pinned to the normative value")

    for relative_path, convention in EXPECTED_CONVENTIONS.items():
        build_text = (SERVER / relative_path).read_text(encoding="utf-8")
        if f'id("{convention}")' not in build_text:
            errors.append(f"{relative_path} does not apply {convention}")
        for dependency in REDUNDANT_CONVENTION_DEPENDENCIES:
            if dependency in build_text:
                errors.append(f"{relative_path} redundantly declares convention dependency {dependency}")

    for relative_path in sorted(EXPECTED_ARCHITECTURE_TEST_CONVENTIONS):
        build_text = (SERVER / relative_path).read_text(encoding="utf-8")
        if 'id("taskmigo.architecture-test")' not in build_text:
            errors.append(f"{relative_path} does not apply taskmigo.architecture-test")

    java_base = (BUILD_LOGIC / "src/main/kotlin/taskmigo.java-base.gradle.kts").read_text(encoding="utf-8")
    required_java_base_tokens = (
        'id("net.ltgt.errorprone")',
        'id("com.diffplug.spotless")',
        "checkstyle",
        'JavaLanguageVersion.of(26)',
        'add("compileOnlyApi"',
        'add("compileOnly"',
        'add("errorprone"',
        'NullAway:JSpecifyMode',
    )
    for token in required_java_base_tokens:
        if token not in java_base:
            errors.append(f"java-base convention is missing required configuration: {token}")


def main() -> int:
    errors: list[str] = []
    try:
        verify_named_modules(errors)
        verify_dependency_graph(errors)
        verify_foundation(errors)
        verify_build_conventions(errors)
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
