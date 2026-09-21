#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path


class EvaluationError(RuntimeError):
    pass


def evaluate(root: Path, bundle: dict) -> dict:
    if bundle.get("schemaVersion") != 1:
        raise EvaluationError("评测结果包版本不受支持")
    runtime = require_object(bundle, "runtime", "运行时")
    if runtime.get("providerKind") != "DETERMINISTIC":
        raise EvaluationError("离线评测只允许使用确定性供应商")

    fixture_root = root / "project/fixtures"
    manifest = read_object(fixture_root / "manifest.json")
    fixtures = require_list(manifest, "fixtures", "演示数据清单")
    expected_cases: dict[str, tuple[str, dict]] = {}
    dataset_paths = [fixture_root / "manifest.json"]
    for fixture in fixtures:
        if not isinstance(fixture, dict):
            raise EvaluationError("演示数据清单项格式不正确")
        scenario = require_text(fixture, "scenario", "场景")
        knowledge_path = safe_path(
            fixture_root,
            require_text(fixture, "knowledgeFile", "知识文件"),
        )
        case_path = safe_path(
            fixture_root,
            require_text(fixture, "searchCasesFile", "检索用例文件"),
        )
        dataset_paths.extend([knowledge_path, case_path])
        case_document = read_object(case_path)
        for case in require_list(case_document, "cases", "检索用例"):
            if not isinstance(case, dict):
                raise EvaluationError("检索用例格式不正确")
            identifier = require_text(case, "id", "检索用例标识")
            if identifier in expected_cases:
                raise EvaluationError(f"检索用例标识重复: {identifier}")
            expected_cases[identifier] = (scenario, case)

    provided_cases = require_list(bundle, "cases", "查询评测结果")
    provided_ids = {
        require_text(item, "id", "查询评测标识")
        for item in provided_cases
        if isinstance(item, dict)
    }
    if provided_ids != set(expected_cases) or len(provided_cases) != len(
        expected_cases
    ):
        raise EvaluationError("查询评测结果不完整或包含未知用例")

    case_results = []
    expected_record_total = 0
    matched_record_total = 0
    answer_point_total = 0
    matched_answer_point_total = 0
    hit_case_total = 0
    for provided in provided_cases:
        if not isinstance(provided, dict):
            raise EvaluationError("查询评测结果项格式不正确")
        identifier = require_text(provided, "id", "查询评测标识")
        scenario, expected = expected_cases[identifier]
        if provided.get("scenario") != scenario:
            raise EvaluationError(f"查询评测场景不匹配: {identifier}")
        result = evaluate_standard_case(
            identifier,
            scenario,
            expected,
            require_object(provided, "response", "查询响应"),
        )
        case_results.append(result)
        expected_record_total += result["expectedRecordCount"]
        matched_record_total += result["matchedRecordCount"]
        answer_point_total += result["answerPointCount"]
        matched_answer_point_total += result["matchedAnswerPointCount"]
        if result["targetHit"]:
            hit_case_total += 1

    cross_cases = require_list(
        bundle,
        "crossTenantCases",
        "跨租户评测结果",
    )
    if not cross_cases:
        raise EvaluationError("跨租户评测结果不能为空")
    cross_results = [evaluate_cross_case(item) for item in cross_cases]

    refusal_cases = require_list(bundle, "refusalCases", "拒答评测结果")
    if not refusal_cases:
        raise EvaluationError("拒答评测结果不能为空")
    refusal_results = [evaluate_refusal_case(item) for item in refusal_cases]

    case_count = len(case_results)
    target_hit_rate = ratio(hit_case_total, case_count)
    citation_completeness = ratio(
        matched_record_total,
        expected_record_total,
    )
    answer_point_coverage = ratio(
        matched_answer_point_total,
        answer_point_total,
    )
    cross_tenant_leak_count = sum(
        result["leakCount"] for result in cross_results
    )
    expected_refusal_rate = ratio(
        sum(1 for result in refusal_results if result["passed"]),
        len(refusal_results),
    )
    metrics = {
        "targetHitRate": target_hit_rate,
        "citationCompleteness": citation_completeness,
        "answerPointCoverage": answer_point_coverage,
        "crossTenantLeakCount": cross_tenant_leak_count,
        "expectedRefusalRate": expected_refusal_rate,
    }
    thresholds = {
        "targetHitRate": 1.0,
        "citationCompleteness": 1.0,
        "answerPointCoverage": 1.0,
        "crossTenantLeakCount": 0,
        "expectedRefusalRate": 1.0,
    }
    passed = (
        target_hit_rate >= thresholds["targetHitRate"]
        and citation_completeness >= thresholds["citationCompleteness"]
        and answer_point_coverage >= thresholds["answerPointCoverage"]
        and cross_tenant_leak_count
        <= thresholds["crossTenantLeakCount"]
        and expected_refusal_rate >= thresholds["expectedRefusalRate"]
    )
    return {
        "schemaVersion": 1,
        "datasetDigest": dataset_digest(root, dataset_paths),
        "runtime": {"providerKind": "DETERMINISTIC"},
        "caseCount": case_count,
        "crossTenantCaseCount": len(cross_results),
        "refusalCaseCount": len(refusal_results),
        "metrics": metrics,
        "thresholds": thresholds,
        "passed": passed,
        "caseResults": case_results,
        "crossTenantResults": cross_results,
        "refusalResults": refusal_results,
    }


def evaluate_standard_case(
    identifier: str,
    scenario: str,
    expected: dict,
    response: dict,
) -> dict:
    failures = []
    status = response.get("status")
    answer = response.get("answer")
    citations = response.get("citations")
    if status != "ANSWERED":
        failures.append("状态不是已回答")
    if not isinstance(answer, str) or not answer:
        failures.append("回答正文为空")
        answer = ""
    quotes = citation_quotes(citations)
    if not quotes:
        failures.append("引用为空")
    expected_records = require_text_list(
        expected,
        "expectedRecordIds",
        "预期记录",
    )
    mentions = require_text_list(expected, "mustMention", "答案要点")
    matched_records = [
        record_id
        for record_id in expected_records
        if any(f"[记录: {record_id}]" in quote for quote in quotes)
    ]
    if len(matched_records) != len(expected_records):
        missing = sorted(set(expected_records) - set(matched_records))
        failures.append("缺少预期记录: " + ", ".join(missing))
    matched_mentions = [mention for mention in mentions if mention in answer]
    if len(matched_mentions) != len(mentions):
        missing = sorted(set(mentions) - set(matched_mentions))
        failures.append("回答缺少要点: " + ", ".join(missing))
    return {
        "id": identifier,
        "scenario": scenario,
        "passed": not failures,
        "targetHit": len(matched_records) == len(expected_records),
        "expectedRecordCount": len(expected_records),
        "matchedRecordCount": len(matched_records),
        "answerPointCount": len(mentions),
        "matchedAnswerPointCount": len(matched_mentions),
        "failures": failures,
    }


def evaluate_cross_case(value: object) -> dict:
    if not isinstance(value, dict):
        raise EvaluationError("跨租户评测结果项格式不正确")
    identifier = require_text(value, "id", "跨租户评测标识")
    forbidden = require_text_list(
        value,
        "forbiddenRecordIds",
        "禁止命中记录",
    )
    response = require_object(value, "response", "跨租户查询响应")
    quotes = citation_quotes(response.get("citations"))
    leaked = [
        record_id
        for record_id in forbidden
        if any(f"[记录: {record_id}]" in quote for quote in quotes)
    ]
    return {
        "id": identifier,
        "passed": not leaked,
        "leakCount": len(leaked),
        "leakedRecordIds": leaked,
    }


def evaluate_refusal_case(value: object) -> dict:
    if not isinstance(value, dict):
        raise EvaluationError("拒答评测结果项格式不正确")
    identifier = require_text(value, "id", "拒答评测标识")
    response = require_object(value, "response", "拒答查询响应")
    passed = (
        response.get("status") == "REFUSED"
        and response.get("answer") is None
        and response.get("refusalReason") == "INSUFFICIENT_EVIDENCE"
        and response.get("citations") == []
    )
    return {
        "id": identifier,
        "passed": passed,
    }


def citation_quotes(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    return [
        citation["quote"]
        for citation in value
        if isinstance(citation, dict)
        and isinstance(citation.get("quote"), str)
        and citation["quote"]
    ]


def dataset_digest(root: Path, paths: list[Path]) -> str:
    digest = hashlib.sha256()
    for path in sorted(set(paths)):
        relative = path.resolve().relative_to(root.resolve()).as_posix()
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def safe_path(root: Path, relative: str) -> Path:
    path = (root / relative).resolve()
    resolved_root = root.resolve()
    if resolved_root not in path.parents or not path.is_file():
        raise EvaluationError(f"评测数据路径不安全或不存在: {relative}")
    return path


def read_object(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise EvaluationError(f"无法读取评测数据: {path}") from error
    if not isinstance(value, dict):
        raise EvaluationError(f"评测数据根节点必须是对象: {path}")
    return value


def require_object(value: dict, field: str, label: str) -> dict:
    result = value.get(field)
    if not isinstance(result, dict):
        raise EvaluationError(f"{label}格式不正确")
    return result


def require_list(value: dict, field: str, label: str) -> list:
    result = value.get(field)
    if not isinstance(result, list):
        raise EvaluationError(f"{label}格式不正确")
    return result


def require_text(value: dict, field: str, label: str) -> str:
    result = value.get(field)
    if not isinstance(result, str) or not result or result != result.strip():
        raise EvaluationError(f"{label}格式不正确")
    return result


def require_text_list(value: dict, field: str, label: str) -> list[str]:
    result = value.get(field)
    if (
        not isinstance(result, list)
        or not result
        or not all(
            isinstance(item, str)
            and bool(item)
            and item == item.strip()
            for item in result
        )
    ):
        raise EvaluationError(f"{label}格式不正确")
    return result


def ratio(numerator: int, denominator: int) -> float:
    if denominator < 1:
        raise EvaluationError("评测分母必须大于零")
    return numerator / denominator


def main() -> int:
    parser = argparse.ArgumentParser(description="验证确定性演示评测结果")
    parser.add_argument("bundle", type=Path, help="运行结果包")
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
        help="仓库根目录",
    )
    parser.add_argument("--output", type=Path, help="评测报告输出路径")
    arguments = parser.parse_args()
    bundle = read_object(arguments.bundle)
    report = evaluate(arguments.root, bundle)
    serialized = json.dumps(
        report,
        ensure_ascii=False,
        indent=2,
        sort_keys=True,
    )
    if arguments.output is None:
        print(serialized)
    else:
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(serialized + "\n", encoding="utf-8")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
