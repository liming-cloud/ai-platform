#!/usr/bin/env python3
import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path


MAX_FILE_BYTES = 20 * 1024 * 1024
TENANT_CODE = re.compile(r"[a-z][a-z0-9-]{2,62}")


class FixtureError(RuntimeError):
    pass


@dataclass(frozen=True)
class VerifyResult:
    fixture_count: int
    search_case_count: int


def verify(
    root: Path,
    expected_scenarios: set[str] | None = None,
) -> VerifyResult:
    fixture_root = root / "project/fixtures"
    manifest = read_json(fixture_root / "manifest.json")
    require_schema(manifest, "演示数据清单")
    fixtures = manifest.get("fixtures")
    if not isinstance(fixtures, list) or not fixtures:
        raise FixtureError("演示数据清单不能为空")

    scenarios: set[str] = set()
    case_ids: set[str] = set()
    case_count = 0
    for fixture in fixtures:
        if not isinstance(fixture, dict):
            raise FixtureError("演示数据项格式不正确")
        scenario = require_text(fixture, "scenario", "场景")
        if scenario in scenarios:
            raise FixtureError(f"场景重复: {scenario}")
        scenarios.add(scenario)
        tenant_code = require_text(fixture, "tenantCode", "租户编码")
        if TENANT_CODE.fullmatch(tenant_code) is None:
            raise FixtureError(f"租户编码格式不正确: {tenant_code}")

        knowledge_path = safe_fixture_path(
            fixture_root,
            require_text(fixture, "knowledgeFile", "知识文件"),
        )
        content_bytes = knowledge_path.read_bytes()
        if not content_bytes or len(content_bytes) > MAX_FILE_BYTES:
            raise FixtureError(f"知识文件大小不正确: {knowledge_path}")
        try:
            content = content_bytes.decode("utf-8")
        except UnicodeDecodeError as error:
            raise FixtureError(f"知识文件不是严格 UTF-8: {knowledge_path}") from error
        if "[合成数据]" not in content:
            raise FixtureError(f"知识文件缺少合成数据标识: {knowledge_path}")
        expected_digest = require_text(fixture, "sha256", "内容摘要")
        actual_digest = hashlib.sha256(content_bytes).hexdigest()
        if expected_digest != actual_digest:
            raise FixtureError(f"知识文件摘要不一致: {knowledge_path}")

        search_path = safe_fixture_path(
            fixture_root,
            require_text(fixture, "searchCasesFile", "检索用例文件"),
        )
        search_cases = read_json(search_path)
        require_schema(search_cases, f"检索用例 {scenario}")
        cases = search_cases.get("cases")
        if not isinstance(cases, list) or not cases:
            raise FixtureError(f"检索用例不能为空: {scenario}")
        for case in cases:
            verify_search_case(case, content, case_ids)
            case_count += 1

    if expected_scenarios is not None and scenarios != expected_scenarios:
        raise FixtureError(
            "演示场景不完整，实际为 "
            + ", ".join(sorted(scenarios))
        )
    return VerifyResult(len(fixtures), case_count)


def verify_search_case(
    case: object,
    content: str,
    case_ids: set[str],
) -> None:
    if not isinstance(case, dict):
        raise FixtureError("检索用例格式不正确")
    identifier = require_text(case, "id", "检索用例标识")
    if identifier in case_ids:
        raise FixtureError(f"检索用例标识重复: {identifier}")
    case_ids.add(identifier)
    require_text(case, "query", "检索问题")
    record_ids = case.get("expectedRecordIds")
    mentions = case.get("mustMention")
    if not is_text_list(record_ids) or not is_text_list(mentions):
        raise FixtureError(f"检索用例断言不完整: {identifier}")
    for record_id in record_ids:
        if f"[记录: {record_id}]" not in content:
            raise FixtureError(
                f"检索用例引用不存在的记录: {identifier}/{record_id}"
            )
    for mention in mentions:
        if mention not in content:
            raise FixtureError(
                f"检索用例要求的答案要点不存在: {identifier}/{mention}"
            )


def safe_fixture_path(root: Path, relative: str) -> Path:
    candidate = (root / relative).resolve()
    resolved_root = root.resolve()
    if resolved_root not in candidate.parents:
        raise FixtureError(f"演示数据路径越界: {relative}")
    if not candidate.is_file():
        raise FixtureError(f"演示数据文件不存在: {relative}")
    return candidate


def read_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise FixtureError(f"无法读取演示数据文件: {path}") from error
    if not isinstance(value, dict):
        raise FixtureError(f"演示数据文件根节点必须是对象: {path}")
    return value


def require_schema(value: dict, label: str) -> None:
    if value.get("schemaVersion") != 1:
        raise FixtureError(f"{label}版本不受支持")


def require_text(value: dict, field: str, label: str) -> str:
    result = value.get(field)
    if not isinstance(result, str) or not result or result != result.strip():
        raise FixtureError(f"{label}格式不正确")
    return result


def is_text_list(value: object) -> bool:
    return (
        isinstance(value, list)
        and bool(value)
        and all(
            isinstance(item, str) and bool(item) and item == item.strip()
            for item in value
        )
    )


if __name__ == "__main__":
    repository_root = Path(__file__).resolve().parents[2]
    result = verify(repository_root, {"store", "campus"})
    print(
        f"演示数据验证通过：{result.fixture_count} 个场景，"
        f"{result.search_case_count} 个检索用例"
    )
