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
JAVA_SQL_CLIENT_REFERENCE = re.compile(
    r"\b(?:JdbcClient|JdbcTemplate|NamedParameterJdbcTemplate|DatabaseClient)\b"
)
JAVA_SQL_EXECUTION_CALL = re.compile(
    r"\.\s*(?:sql|query|queryForObject|queryForList|update|batchUpdate|execute)\s*\("
)
UUID_REFERENCE = re.compile(r"\bUUID\b")
SA_TOKEN_REFERENCE = re.compile(r"\bcn\.dev33\.satoken\b")
CURSOR_LIST_CONTRACT = re.compile(r"\b(?:nextCursor|String\s+cursor)\b")
MANUAL_LIST_SELECT = re.compile(
    r"<select\b[^>]*\bid=\"(?P<id>[^\"]*(?:Page|After)[^\"]*)\"[^>]*>"
    r".*?\bLIMIT\s+#\{limit\}",
    flags=re.IGNORECASE | re.DOTALL,
)
MAPPER_STATEMENT = re.compile(
    r"<(?P<kind>select|insert|update|delete)\b"
    r"[^>]*\bid=\"(?P<id>[^\"]+)\"[^>]*>",
    flags=re.IGNORECASE,
)
FLYWAY_MIGRATION_FILE = re.compile(r"^V(?P<version>[0-9]+)__.+\.sql$")
ERROR_CODE_LITERAL = re.compile(
    r'"([A-Z][A-Z_]*\.[A-Z_]+\.[^"]+)"'
)
DOMAIN_TYPE_DECLARATION = re.compile(
    r"\b(?:class|record|enum)\s+(?P<name>[A-Za-z][A-Za-z0-9_]*)"
    r"[^{};]*?\bimplements\s+(?P<interfaces>[^\{]+)\{",
    flags=re.DOTALL,
)
APPLICATION_SERVICE_CONSTRUCTOR = re.compile(
    r"\b(?P<name>[A-Za-z][A-Za-z0-9_]*Service)\s*\((?P<parameters>[^)]*)\)",
    flags=re.DOTALL,
)
VALID_ERROR_CODE = re.compile(
    r"^[A-Z][A-Z_]*\."
    r"(?:REQUEST|AUTHENTICATION|AUTHORIZATION|RESOURCE|CONFLICT|QUOTA|"
    r"DEPENDENCY_UNAVAILABLE|DEPENDENCY_RESPONSE|SYSTEM)\."
    r"\d{4}$"
)
MAX_PACKAGE_CLASSES = 12
BASELINE = Path("project/architecture-debt/WP-0008-backend.json")
COMPLEX_SQL_ALLOWLIST = Path(
    "project/architecture-debt/complex-sql-allowlist.json"
)
COMPLEX_SQL_CATEGORIES = {
    "POSTGRES_ADVISORY_LOCK",
    "ROW_LOCK",
    "SKIP_LOCKED",
    "MULTI_TABLE",
    "ATOMIC_STATE_TRANSITION",
    "AGGREGATE",
    "POSTGRES_TYPE",
    "OUTBOX",
    "EVENT_REPLAY",
    "SECURITY_PROJECTION",
}
LIST_CATEGORIES = {
    "sqlAnnotationFiles": "SQL 注解",
    "javaInlineSqlFiles": "Java 内联 SQL",
    "uuidMainFiles": "UUID",
    "runtimeNonEntryFiles": "运行模块业务类",
    "saTokenOutsidePlatformFiles": "Sa-Token 越界",
    "nonAggregateEntityFiles": "非聚合根实体",
    "applicationServiceDomainStateDependencies": "应用服务构造器领域状态依赖",
}
COUNT_CATEGORIES = {
    "legacyContextApiCount": "上下文 api 目录",
    "legacyContextAdapterCount": "上下文 adapter 目录",
    "runtimeDirectoryFileCount": "运行模块目录",
    "cursorListContractCount": "游标列表分页",
    "manualListLimitCount": "手工列表分页",
}


def main_java_files(root):
    return sorted(
        path
        for path in (root / "backend").glob("**/src/main/java/**/*.java")
        if path.is_file()
    )


def production_runtime_files(root):
    runtime_root = root / "backend" / "runtimes"
    if not runtime_root.is_dir():
        return []
    return sorted(
        path
        for path in runtime_root.glob("**/*")
        if path.is_file() and "target" not in path.parts
    )


def mapper_xml_files(root):
    return sorted(
        path
        for path in (root / "backend" / "contexts").glob(
            "*/src/main/resources/mapper/**/*.xml"
        )
        if path.is_file()
    )


def duplicate_flyway_migration_versions(root):
    versions = {}
    for path in sorted((root / "backend" / "contexts").glob(
        "*/src/main/resources/db/migration/**/*.sql"
    )):
        match = FLYWAY_MIGRATION_FILE.fullmatch(path.name)
        if match:
            versions.setdefault(match.group("version"), []).append(relative(root, path))
    return [
        f"{version}：{', '.join(paths)}"
        for version, paths in sorted(versions.items())
        if len(paths) > 1
    ]


def relative(root, path):
    return path.relative_to(root).as_posix()


def mapper_statements(root, mapper_texts):
    return {
        f"{relative(root, path)}#{match.group('id')}": {
            "path": relative(root, path),
            "statementId": match.group("id"),
            "kind": match.group("kind").lower(),
            "text": match.group(0),
        }
        for path, text in mapper_texts.items()
        for match in MAPPER_STATEMENT.finditer(text)
    }


def read_complex_sql_allowlist(root):
    path = Path(root) / COMPLEX_SQL_ALLOWLIST
    if not path.is_file():
        return {"schemaVersion": 1, "statements": []}
    return json.loads(path.read_text(encoding="utf-8"))


def declared_domain_types(text):
    return {
        match.group("name"): match.group("interfaces")
        for match in DOMAIN_TYPE_DECLARATION.finditer(text)
    }


def non_aggregate_entity_files(root, texts):
    return [
        relative(root, path)
        for path, text in texts.items()
        if "domain" in path.parts
        and any(
            "Entity" in interfaces and "AggregateRoot" not in interfaces
            for interfaces in declared_domain_types(text).values()
        )
    ]


def application_service_domain_state_dependencies(root, texts):
    domain_types = {
        name
        for path, text in texts.items()
        if "domain" in path.parts
        for name, interfaces in declared_domain_types(text).items()
        if (
            ("Entity" in interfaces
             or "AggregateRoot" in interfaces
             or "ValueObject" in interfaces)
            and "DomainPolicy" not in interfaces
        )
    }
    violations = []
    for path, text in texts.items():
        if "application" not in path.parts:
            continue
        for match in APPLICATION_SERVICE_CONSTRUCTOR.finditer(text):
            dependencies = sorted(
                domain_type
                for domain_type in domain_types
                if re.search(rf"\b{re.escape(domain_type)}\b", match.group("parameters"))
            )
            if dependencies:
                violations.append(
                    f"{relative(root, path)}#{match.group('name')}"
                    f"({', '.join(dependencies)})"
                )
    return violations


def validate_complex_sql_allowlist(root, statements):
    errors = []
    allowlist = read_complex_sql_allowlist(root)
    if allowlist.get("schemaVersion") != 1:
        return ["复杂 SQL 允许清单版本不受支持"], {}
    entries = allowlist.get("statements")
    if not isinstance(entries, list):
        return ["复杂 SQL 允许清单 statements 必须是数组"], {}

    registered = {}
    for index, entry in enumerate(entries, start=1):
        if not isinstance(entry, dict):
            errors.append(f"复杂 SQL 允许清单第 {index} 项必须是对象")
            continue
        path = entry.get("path")
        statement_id = entry.get("statementId")
        category = entry.get("category")
        reason = entry.get("reason")
        key = f"{path}#{statement_id}"
        if (
            not isinstance(path, str)
            or not path.startswith("backend/contexts/")
            or not path.endswith(".xml")
        ):
            errors.append(f"复杂 SQL 路径无效：{path}")
        if (
            not isinstance(statement_id, str)
            or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*", statement_id)
        ):
            errors.append(f"复杂 SQL 语句编号无效：{statement_id}")
        if category not in COMPLEX_SQL_CATEGORIES:
            errors.append(f"复杂 SQL 分类无效：{category}")
        if not isinstance(reason, str) or len(reason.strip()) < 6:
            errors.append(f"复杂 SQL 原因不充分：{key}")
        if key in registered:
            errors.append(f"复杂 SQL 重复登记：{key}")
        registered[key] = entry

    unregistered = sorted(set(statements) - set(registered))
    stale = sorted(set(registered) - set(statements))
    if unregistered:
        errors.append("XML SQL 未登记：" + ", ".join(unregistered))
    if stale:
        errors.append("复杂 SQL 失效登记：" + ", ".join(stale))
    return errors, registered


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
    context_files = [
        path
        for path in files
        if relative(root, path).startswith("backend/contexts/")
    ]
    mapper_texts = {
        path: path.read_text(encoding="utf-8")
        for path in mapper_xml_files(root)
    }
    statements = mapper_statements(root, mapper_texts)
    allowlist_errors, registered_statements = validate_complex_sql_allowlist(
        root, statements
    )
    error_codes = [
        code
        for path, text in texts.items()
        if path.name.endswith("ErrorCode.java")
        for code in ERROR_CODE_LITERAL.findall(text)
    ]
    error_code_counts = Counter(error_codes)
    return {
        "schemaVersion": 1,
        "sqlAnnotationFiles": [
            relative(root, path)
            for path, text in texts.items()
            if SQL_ANNOTATION.search(text)
        ],
        "javaInlineSqlFiles": [
            relative(root, path)
            for path, text in texts.items()
            if JAVA_SQL_CLIENT_REFERENCE.search(text)
            and JAVA_SQL_EXECUTION_CALL.search(text)
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
        "nonAggregateEntityFiles": non_aggregate_entity_files(root, texts),
        "applicationServiceDomainStateDependencies": (
            application_service_domain_state_dependencies(root, texts)
        ),
        "flatPackages": {
            package: count
            for package, count in sorted(package_counts.items())
            if count > MAX_PACKAGE_CLASSES
        },
        "legacyContextApiCount": sum(
            1 for path in context_files if "api" in path.parts
        ),
        "legacyContextAdapterCount": sum(
            1 for path in context_files if "adapter" in path.parts
        ),
        "runtimeDirectoryFileCount": len(production_runtime_files(root)),
        "cursorListContractCount": sum(
            1
            for path in context_files
            if CURSOR_LIST_CONTRACT.search(texts[path])
        ),
        "manualListLimitCount": sum(
            1
            for path, text in mapper_texts.items()
            for match in MANUAL_LIST_SELECT.finditer(text)
            if registered_statements.get(
                f"{relative(root, path)}#{match.group('id')}", {}
            ).get("category") != "EVENT_REPLAY"
        ),
        "complexSqlAllowlistErrors": allowlist_errors,
        "duplicateFlywayMigrationVersions": duplicate_flyway_migration_versions(root),
        "invalidErrorCodes": sorted(
            code for code in error_codes if not VALID_ERROR_CODE.fullmatch(code)
        ),
        "duplicateErrorCodes": sorted(
            code for code, count in error_code_counts.items() if count > 1
        ),
    }


def compare(actual, baseline):
    errors = []
    if baseline.get("schemaVersion") != 1:
        return ["架构债务基线版本不受支持"]
    if actual.get("invalidErrorCodes"):
        errors.append(
            "错误码格式违规："
            + ", ".join(actual["invalidErrorCodes"])
        )
    if actual.get("duplicateErrorCodes"):
        errors.append(
            "错误码重复："
            + ", ".join(actual["duplicateErrorCodes"])
        )
    errors.extend(actual.get("complexSqlAllowlistErrors", []))
    if actual.get("duplicateFlywayMigrationVersions"):
        errors.append(
            "Flyway 迁移版本重复："
            + "；".join(actual["duplicateFlywayMigrationVersions"])
        )
    for category, label in LIST_CATEGORIES.items():
        new_items = sorted(
            set(actual.get(category, []))
            - set(baseline.get(category, []))
        )
        if new_items:
            errors.append(
                f"新增{label}违规：" + ", ".join(new_items)
            )

    for category, label in COUNT_CATEGORIES.items():
        current = int(actual.get(category, 0))
        allowed = int(baseline.get(category, 0))
        if current > allowed:
            errors.append(
                f"{label}新增违规：当前 {current}，允许 {allowed}"
            )

    baseline_packages = baseline.get("flatPackages", {})
    for package, count in actual.get("flatPackages", {}).items():
        allowed = baseline_packages.get(package, MAX_PACKAGE_CLASSES)
        if count > allowed:
            errors.append(
                f"包规模新增违规：{package} 当前 {count}，允许 {allowed}"
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
        f"Java 内联 SQL 文件 {len(actual['javaInlineSqlFiles'])}，"
        f"UUID 文件 {len(actual['uuidMainFiles'])}，"
        f"运行时非启动文件 {len(actual['runtimeNonEntryFiles'])}，"
        f"Sa-Token 越界文件 {len(actual['saTokenOutsidePlatformFiles'])}，"
        f"超预算包 {len(actual['flatPackages'])}，"
        f"旧 api 文件 {actual['legacyContextApiCount']}，"
        f"旧 adapter 文件 {actual['legacyContextAdapterCount']}，"
        f"runtimes 文件 {actual['runtimeDirectoryFileCount']}，"
        f"游标列表契约 {actual['cursorListContractCount']}，"
        f"手工列表分页 {actual['manualListLimitCount']}"
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
