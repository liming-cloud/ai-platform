#!/usr/bin/env python3
"""Regression tests for the backend bounded-context module gate."""

from __future__ import annotations

import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parent))

from validate_backend_context_modules import empty_context_modules, reactor_context_modules


class BackendContextModuleGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.write(
            "backend/pom.xml",
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modules>
                <module>contexts/java-context</module>
                <module>contexts/resources-context</module>
                <module>contexts/placeholder-context</module>
                <module>contexts/deleted-context</module>
                <module>platform-infrastructure/resources-only</module>
              </modules>
            </project>
            """,
        )
        self.write("backend/contexts/java-context/src/main/java/Example.java", "class Example {}")
        self.write("backend/contexts/resources-context/src/main/resources/schema.sql", "select 1;")
        self.write("backend/contexts/placeholder-context/src/main/.gitkeep", "")
        self.write("backend/contexts/placeholder-context/src/test/README.md", "future tests")
        self.write("backend/contexts/deleted-context/src/main/java/Deleted.java", "class Deleted {}")
        self.write("backend/platform-infrastructure/resources-only/pom.xml", "<project/>")
        subprocess.run(["git", "init", "--quiet"], cwd=self.root, check=True)
        subprocess.run(["git", "add", "."], cwd=self.root, check=True)
        (self.root / "backend/contexts/deleted-context/src/main/java/Deleted.java").unlink()

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_only_direct_reactor_context_modules_are_governed(self) -> None:
        self.assertEqual(
            reactor_context_modules(self.root),
            (
                "contexts/java-context",
                "contexts/resources-context",
                "contexts/placeholder-context",
                "contexts/deleted-context",
            ),
        )

    def test_tracked_java_and_resource_files_are_both_valid_implementations(self) -> None:
        self.assertEqual(
            empty_context_modules(self.root),
            ("contexts/placeholder-context", "contexts/deleted-context"),
        )

    def write(self, relative_path: str, content: str) -> None:
        target = self.root / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(textwrap.dedent(content).strip() + "\n", encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
