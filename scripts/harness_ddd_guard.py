#!/usr/bin/env python3
"""Scan backend source for DDD layering drift that is easy to miss in unit tests."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = ROOT / "backend" / "business" / "ai-platform-modules"
SHARED_DDD_ROOT = ROOT / "backend" / "infrastructure" / "ai-platform-shared" / "src" / "main" / "java" / "org" / "fujay" / "ai" / "platform" / "shared" / "ddd"
APPLICATION_YML = ROOT / "backend" / "apps" / "ai-platform-boot" / "src" / "main" / "resources" / "application.yml"
SECRET_CIPHER = ROOT / "backend" / "infrastructure" / "ai-platform-security" / "src" / "main" / "java" / "org" / "fujay" / "ai" / "platform" / "security" / "SecretCipher.java"
EXCLUDED_PARTS = {"target", "build", "dist", "node_modules", ".next"}


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def java_files() -> list[Path]:
    if not BACKEND_ROOT.exists():
        return []
    return [
        path
        for path in BACKEND_ROOT.rglob("*.java")
        if not EXCLUDED_PARTS.intersection(path.parts) and "/src/main/java/" in str(path)
    ]


def line_count(text: str) -> int:
    return text.count("\n") + 1


def declares_marker(text: str, marker: str) -> bool:
    return re.search(rf"\bimplements\b[^{{;]*\b{marker}\b", text) is not None


def main() -> int:
    failures: list[str] = []
    warnings: list[str] = []

    for path in java_files():
        text = path.read_text(encoding="utf-8", errors="ignore")
        source_path = str(path)

        if "/domain/repository/" in source_path:
            nested_records = [
                match.group(1)
                for match in re.finditer(r"^\s*(?:public\s+)?record\s+(\w+)\s*\(", text, re.MULTILINE)
            ]
            if nested_records:
                failures.append(
                    f"{rel(path)} declares nested repository records: {', '.join(nested_records)}"
                )

        if "/application/service/" in source_path:
            lines = line_count(text)
            if lines > 300:
                failures.append(f"{rel(path)} is a fat application service: {lines} lines > 300")
            imported_records = re.findall(
                r"import\s+org\.fujay\.ai\.platform\.[^.]+\.domain\.repository\.[^;]+\.(\w+Record);",
                text,
            )
            if imported_records:
                failures.append(
                    f"{rel(path)} imports repository record types: {', '.join(sorted(set(imported_records)))}"
                )
            constructed_records = re.findall(r"new\s+(\w+Record)\s*\(", text)
            if constructed_records:
                failures.append(
                    f"{rel(path)} constructs repository records: {', '.join(sorted(set(constructed_records)))}"
                )
            if "SnowflakeIdGenerator" in text:
                failures.append(f"{rel(path)} generates ids inside application service")
            if "/model/" in source_path and "import org.fujay.ai.platform.ai." in text:
                failures.append(f"{rel(path)} imports platform AI infrastructure from application service")
            if "/model/" in source_path and "import org.fujay.ai.platform.security.SecretCipher;" in text:
                failures.append(f"{rel(path)} decrypts provider credentials from application service")
            private_methods = re.findall(r"^\s*private\s+(?!static final)[^{;=]+\(", text, re.MULTILINE)
            if len(private_methods) > 8:
                warnings.append(
                    f"{rel(path)} has {len(private_methods)} private helper methods; inspect for domain behavior leakage"
                )

        if "/infrastructure/bootstrap/" in source_path:
            if ".infrastructure.persistence.mapper." in text or ".infrastructure.persistence.po." in text:
                failures.append(f"{rel(path)} directly imports persistence mapper/PO from bootstrap")
            imported_records = re.findall(
                r"import\s+org\.fujay\.ai\.platform\.[^.]+\.domain\.repository\.[^;]+\.(\w+Record);",
                text,
            )
            constructed_records = re.findall(r"new\s+(\w+Record)\s*\(", text)
            if imported_records or constructed_records:
                leaked_records = sorted(set(imported_records + constructed_records))
                failures.append(
                    f"{rel(path)} constructs repository records from bootstrap: {', '.join(leaked_records)}"
                )

        if "/infrastructure/persistence/repository/" in source_path:
            lines = line_count(text)
            if lines > 300:
                failures.append(f"{rel(path)} is a fat persistence repository implementation: {lines} lines > 300")
            nested_records = re.findall(r"^\s*private\s+record\s+(\w+)\s*\(", text, re.MULTILINE)
            if nested_records:
                failures.append(
                    f"{rel(path)} declares private records inside repository implementation: {', '.join(nested_records)}"
                )
            conversion_methods = re.findall(
                r"^\s*private\s+[\w<>, ?]+\s+(?:to|from)[A-Z]\w*\s*\(", text, re.MULTILINE
            )
            if len(conversion_methods) > 6:
                failures.append(
                    f"{rel(path)} hides mapping in {len(conversion_methods)} private conversion methods"
                )
            if re.search(r"\bDEFAULT_[A-Z_]+\s*=", text):
                failures.append(f"{rel(path)} owns default seed/catalog data inside repository implementation")

        if "/infrastructure/persistence/store/" in source_path:
            failures.append(f"{rel(path)} keeps a persistence store layer after repository split")

        if any(part in source_path for part in ("/domain/aggregate/", "/domain/model/", "/domain/value/", "/domain/valueobject/")):
            if "/domain/aggregate/" in source_path and not declares_marker(text, "AggregateRoot"):
                failures.append(f"{rel(path)} aggregate does not implement AggregateRoot")
            elif ("/domain/value/" in source_path or "/domain/valueobject/" in source_path) and not declares_marker(text, "ValueObject"):
                failures.append(f"{rel(path)} value object does not implement ValueObject")
            elif "/domain/model/" in source_path and not any(
                declares_marker(text, marker) for marker in ("AggregateRoot", "Entity", "ValueObject")
            ):
                failures.append(f"{rel(path)} domain model does not declare a DDD stereotype marker")

    for marker in ("DomainObject", "AggregateRoot", "Entity", "ValueObject"):
        if not (SHARED_DDD_ROOT / f"{marker}.java").exists():
            failures.append(f"shared DDD marker is missing: {rel(SHARED_DDD_ROOT / f'{marker}.java')}")

    for config_path in (APPLICATION_YML, SECRET_CIPHER):
        if not config_path.exists():
            continue
        text = config_path.read_text(encoding="utf-8", errors="ignore")
        if "ai-platform-local-secret-change-me" in text or "${ai.platform.secret.master-key:" in text:
            failures.append(f"{rel(config_path)} ships a local master-key fallback outside local-only config")
        if config_path == APPLICATION_YML and "master-key: ${AI_PLATFORM_SECRET_MASTER_KEY:" in text:
            failures.append(f"{rel(config_path)} makes production master key optional with a default fallback")

    for warning in warnings:
        print(f"WARN: {warning}")
    for failure in failures:
        print(f"FAIL: {failure}")

    if failures:
        print(f"ddd_guard=blocked failure_count={len(failures)} warning_count={len(warnings)}")
        return 1
    print(f"ddd_guard=pass warning_count={len(warnings)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
