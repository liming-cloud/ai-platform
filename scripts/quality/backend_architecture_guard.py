#!/usr/bin/env python3

import json
import re
import sys
from collections import Counter
from pathlib import Path


SQL_ANNOTATION = re.compile(
    r"@(Select|Insert|Update|Delete|"
    r"SelectProvider|InsertProvider|UpdateProvider|DeleteProvider)\b"
)
UUID_REFERENCE = re.compile(r"\bUUID\b")
SA_TOKEN_REFERENCE = re.compile(r"\bcn\.dev33\.satoken\b")
MAX_PACKAGE_CLASSES = 12
BASELINE = Path("project/architecture-debt/WP-0008-backend.json")
LIST_CATEGORIES = {
    "sqlAnnotationFiles": "SQL 注解",
    "uuidMainFiles": "UUID",
    "runtimeNonEntryFiles": "运行模块业务类",
    "saTokenOutsidePlatformFiles": "Sa-Token 越界",
}


def main_java_files(root):
    return sorted(
        path
        for path in (root / "backend").glob("**/src/main/java/**/*.java")
        if path.is_file()
    )


def relative(root, path):
    return path.relative_to(root).as_posix()


def scan(root):
    root = Path(root)
    files = main_java_files(root)
    texts = {
        path: path.read_text(encoding="utf-8")
        for path in files
    }
    package_counts = Counter(
        relative(root, path.parent)
        for path in files
    )
    return {
        "schemaVersion": 1,
        "sqlAnnotationFiles": [
            relative(root, path)
            for path, text in texts.items()
            if SQL_ANNOTATION.search(text)
        ],
        "uuidMainFiles": [
            relative(root, path)
            for path, text in texts.items()
            if UUID_REFERENCE.search(text)
        ],
        "runtimeNonEntryFiles": [
            relative(root, path)
            for path in files
            if relative(root, path).startswith("backend/runtimes/")
            and not path.name.endswith("Application.java")
        ],
        "saTokenOutsidePlatformFiles": [
            relative(root, path)
            for path, text in texts.items()
            if SA_TOKEN_REFERENCE.search(text)
            and not relative(root, path).startswith(
                "backend/platform/platform-security/"
                "platform-security-satoken/"
            )
        ],
        "flatPackages": {
            package: count
            for package, count in sorted(package_counts.items())
            if count > MAX_PACKAGE_CLASSES
        },
        "forbiddenInfrastructureFiles": [
            relative(root, path)
            for path in files
            if {"infrastructure", "infra"} & set(path.parts)
        ],
    }


def compare(actual, baseline):
    errors = []
    if baseline.get("schemaVersion") != 1:
        return ["架构债务基线版本不受支持"]
    for category, label in LIST_CATEGORIES.items():
        new_items = sorted(
            set(actual.get(category, []))
            - set(baseline.get(category, []))
        )
        if new_items:
            errors.append(
                f"新增{label}违规：" + ", ".join(new_items)
            )

    baseline_packages = baseline.get("flatPackages", {})
    for package, count in actual.get("flatPackages", {}).items():
        allowed = baseline_packages.get(package, MAX_PACKAGE_CLASSES)
        if count > allowed:
            errors.append(
                f"包规模新增违规：{package} 当前 {count}，允许 {allowed}"
            )

    infrastructure = actual.get("forbiddenInfrastructureFiles", [])
    if infrastructure:
        errors.append(
            "新增基础设施目录违规：" + ", ".join(infrastructure)
        )
    return errors


def read_baseline(root):
    path = Path(root) / BASELINE
    if not path.is_file():
        raise RuntimeError(f"缺少架构债务基线：{BASELINE}")
    return json.loads(path.read_text(encoding="utf-8"))


def capture(root):
    path = Path(root) / BASELINE
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(scan(root), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"架构债务基线已写入：{BASELINE}")


def verify(root):
    actual = scan(root)
    baseline = read_baseline(root)
    errors = compare(actual, baseline)
    if errors:
        raise RuntimeError("\n".join(errors))
    print(
        "架构债务递减门禁通过："
        f"SQL 文件 {len(actual['sqlAnnotationFiles'])}，"
        f"UUID 文件 {len(actual['uuidMainFiles'])}，"
        f"运行时非启动文件 {len(actual['runtimeNonEntryFiles'])}，"
        f"Sa-Token 越界文件 {len(actual['saTokenOutsidePlatformFiles'])}，"
        f"超预算包 {len(actual['flatPackages'])}"
    )


def cli():
    root = Path(__file__).resolve().parents[2]
    command = sys.argv[1] if len(sys.argv) > 1 else "verify"
    try:
        if command == "capture":
            capture(root)
        elif command == "verify":
            verify(root)
        else:
            raise RuntimeError(f"未知命令：{command}")
    except (OSError, ValueError, RuntimeError) as error:
        print(f"后端架构门禁失败：{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(cli())
