#!/usr/bin/env python3
"""Reject empty bounded-context modules declared by the backend reactor."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parents[2]
DIRECT_CONTEXT_MODULE = re.compile(r"^contexts/[^/]+$")
PLACEHOLDER_NAMES = frozenset(
    {
        ".gitkeep",
        ".keep",
        ".placeholder",
        "readme",
        "readme.md",
        "readme.txt",
    }
)


def reactor_context_modules(root: Path) -> tuple[str, ...]:
    pom = root / "backend" / "pom.xml"
    document = ET.parse(pom)
    modules: list[str] = []
    for element in document.getroot().iter():
        if element.tag.rsplit("}", 1)[-1] != "module" or element.text is None:
            continue
        module = element.text.strip()
        if DIRECT_CONTEXT_MODULE.fullmatch(module):
            modules.append(module)
    return tuple(modules)


def tracked_source_files(root: Path, module: str) -> tuple[PurePosixPath, ...]:
    pathspecs = (
        f"backend/{module}/src/main",
        f"backend/{module}/src/test",
    )
    completed = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z", "--", *pathspecs],
        check=True,
        capture_output=True,
    )
    return tuple(
        PurePosixPath(item.decode("utf-8"))
        for item in completed.stdout.split(b"\0")
        if item
    )


def is_non_placeholder_source(root: Path, path: PurePosixPath) -> bool:
    return path.name.lower() not in PLACEHOLDER_NAMES and (root / path).is_file()


def empty_context_modules(root: Path) -> tuple[str, ...]:
    return tuple(
        module
        for module in reactor_context_modules(root)
        if not any(is_non_placeholder_source(root, path) for path in tracked_source_files(root, module))
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    arguments = parser.parse_args(argv)
    root = arguments.root.resolve()

    try:
        modules = reactor_context_modules(root)
        violations = empty_context_modules(root)
    except (OSError, subprocess.CalledProcessError, ET.ParseError) as error:
        print(f"backend context module gate error: {error}", file=sys.stderr)
        return 2

    if violations:
        print("backend context module gate failed:", file=sys.stderr)
        for module in violations:
            print(
                f"- {module}: no tracked non-placeholder file exists under src/main or src/test",
                file=sys.stderr,
            )
        return 1

    print(f"backend context module gate passed: {len(modules)} reactor context modules")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
