#!/usr/bin/env python3
import argparse
import json
import math
import os
import sys
import time
import uuid
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from threading import Semaphore
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class TenantScenario:
    code: str
    question: str
    expected_fragment: str


SCENARIOS = (
    TenantScenario(
        "demo-store",
        "青柚气泡水在哪里，价格是多少",
        "一层 A 区 03 号货架第二层",
    ),
    TenantScenario(
        "demo-campus",
        "中心图书馆周末开放到几点",
        "09:00-18:00",
    ),
)


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="执行可信查询端到端容量测试")
    parser.add_argument("--control-base", required=True)
    parser.add_argument("--query-base", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--concurrency-per-tenant", type=int, default=4)
    parser.add_argument("--warmup-per-tenant", type=int, default=5)
    parser.add_argument("--samples-per-tenant", type=int, default=20)
    parser.add_argument("--p95-limit-milliseconds", type=int, default=1000)
    parser.add_argument("--minimum-throughput-per-second", type=float, default=5)
    return parser.parse_args()


def request_json(
    url: str,
    *,
    method: str = "GET",
    payload: dict | None = None,
    token: str | None = None,
    idempotency_key: str | None = None,
    timeout_seconds: float = 10,
) -> tuple[int, dict[str, str], dict]:
    headers = {"Accept": "application/json"}
    body = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    if token is not None:
        headers["Authorization"] = f"Bearer {token}"
    if idempotency_key is not None:
        headers["Idempotency-Key"] = idempotency_key

    request = Request(url, data=body, headers=headers, method=method)
    with urlopen(request, timeout=timeout_seconds) as response:
        response_body = json.loads(response.read().decode("utf-8"))
        response_headers = {
            name.lower(): value for name, value in response.headers.items()
        }
        return response.status, response_headers, response_body


def login(control_base: str, tenant_code: str, password: str) -> str:
    status, _, body = request_json(
        f"{control_base}/api/v1/auth/sessions",
        method="POST",
        payload={
            "tenantCode": tenant_code,
            "loginName": "admin",
            "password": password,
        },
    )
    if status != 201 or not body.get("tokenValue"):
        raise RuntimeError("演示管理员登录失败")
    return str(body["tokenValue"])


def assistant_version(query_base: str, token: str) -> str:
    status, _, body = request_json(
        f"{query_base}/api/v1/query-assistants",
        token=token,
    )
    items = body.get("items", [])
    if status != 200 or len(items) != 1:
        raise RuntimeError("查询助手目录必须且只能包含一个已发布助手")
    return str(items[0]["assistantVersionId"])


def percentile(values: list[float], ratio: float) -> float:
    if not values:
        return 0
    ordered = sorted(values)
    position = max(0, math.ceil(len(ordered) * ratio) - 1)
    return ordered[position]


def execute_query(
    query_base: str,
    token: str,
    assistant_version_id: str,
    scenario: TenantScenario,
    idempotency_key: str,
    semaphore: Semaphore,
) -> dict:
    started = time.perf_counter()
    replayed = False
    error_type = None
    try:
        with semaphore:
            status, headers, body = request_json(
                f"{query_base}/api/v1/query-runs",
                method="POST",
                payload={
                    "assistantVersionId": assistant_version_id,
                    "question": scenario.question,
                },
                token=token,
                idempotency_key=idempotency_key,
                timeout_seconds=15,
            )
        replayed = headers.get("idempotency-replayed", "").lower() == "true"
        citations = body.get("citations", [])
        answer = body.get("answer") or ""
        if (
            status != 201
            or replayed
            or body.get("status") != "ANSWERED"
            or not citations
            or scenario.expected_fragment not in answer
        ):
            error_type = "响应契约不满足"
    except HTTPError as exception:
        error_type = f"HTTP_{exception.code}"
    except (URLError, TimeoutError):
        error_type = "网络或超时"
    except (KeyError, TypeError, ValueError, json.JSONDecodeError):
        error_type = "响应解析失败"
    except Exception:
        error_type = "未分类异常"

    return {
        "latencyMilliseconds": (time.perf_counter() - started) * 1000,
        "replayed": replayed,
        "errorType": error_type,
    }


def execute_phase(
    *,
    query_base: str,
    tokens: dict[str, str],
    versions: dict[str, str],
    semaphores: dict[str, Semaphore],
    per_tenant_count: int,
    concurrency_per_tenant: int,
    phase: str,
    run_id: str,
) -> tuple[list[dict], float]:
    if per_tenant_count == 0:
        return [], 0

    tasks = []
    for sequence in range(per_tenant_count):
        for scenario in SCENARIOS:
            tasks.append((scenario, sequence))

    started = time.perf_counter()
    results = []
    with ThreadPoolExecutor(
        max_workers=concurrency_per_tenant * len(SCENARIOS)
    ) as executor:
        futures = [
            executor.submit(
                execute_query,
                query_base,
                tokens[scenario.code],
                versions[scenario.code],
                scenario,
                f"capacity-{run_id}-{phase}-{scenario.code}-{sequence}",
                semaphores[scenario.code],
            )
            for scenario, sequence in tasks
        ]
        for future in as_completed(futures):
            results.append(future.result())
    return results, time.perf_counter() - started


def build_report(arguments: argparse.Namespace) -> dict:
    password = os.environ.get("FOUNDATION_CAPACITY_DEMO_PASSWORD")
    if not password:
        raise RuntimeError("缺少演示容量测试密码环境变量")
    if arguments.concurrency_per_tenant < 1:
        raise ValueError("每租户并发数必须大于零")
    if arguments.warmup_per_tenant < 0 or arguments.samples_per_tenant < 1:
        raise ValueError("预热数不能为负且采样数必须大于零")

    tokens = {
        scenario.code: login(arguments.control_base, scenario.code, password)
        for scenario in SCENARIOS
    }
    versions = {
        scenario.code: assistant_version(
            arguments.query_base,
            tokens[scenario.code],
        )
        for scenario in SCENARIOS
    }
    semaphores = {
        scenario.code: Semaphore(arguments.concurrency_per_tenant)
        for scenario in SCENARIOS
    }
    run_id = uuid.uuid4().hex

    warmup_results, _ = execute_phase(
        query_base=arguments.query_base,
        tokens=tokens,
        versions=versions,
        semaphores=semaphores,
        per_tenant_count=arguments.warmup_per_tenant,
        concurrency_per_tenant=arguments.concurrency_per_tenant,
        phase="warmup",
        run_id=run_id,
    )
    sample_results, sample_duration = execute_phase(
        query_base=arguments.query_base,
        tokens=tokens,
        versions=versions,
        semaphores=semaphores,
        per_tenant_count=arguments.samples_per_tenant,
        concurrency_per_tenant=arguments.concurrency_per_tenant,
        phase="sample",
        run_id=run_id,
    )

    all_results = warmup_results + sample_results
    errors = Counter(
        result["errorType"]
        for result in all_results
        if result["errorType"] is not None
    )
    replayed_count = sum(result["replayed"] for result in all_results)
    latencies = [
        result["latencyMilliseconds"]
        for result in sample_results
        if result["errorType"] is None
    ]
    throughput = (
        len(sample_results) / sample_duration if sample_duration > 0 else 0
    )
    p95 = percentile(latencies, 0.95)
    passed = (
        not errors
        and replayed_count == 0
        and len(latencies) == len(sample_results)
        and p95 <= arguments.p95_limit_milliseconds
        and throughput >= arguments.minimum_throughput_per_second
    )

    return {
        "passed": passed,
        "modelType": "本地确定性对话与嵌入模型",
        "tenantCount": len(SCENARIOS),
        "concurrencyPerTenant": arguments.concurrency_per_tenant,
        "totalConcurrency": arguments.concurrency_per_tenant * len(SCENARIOS),
        "warmupRequestCount": len(warmup_results),
        "sampleRequestCount": len(sample_results),
        "errorCount": sum(errors.values()),
        "errorTypes": dict(sorted(errors.items())),
        "replayedCount": replayed_count,
        "p50Milliseconds": round(percentile(latencies, 0.50), 3),
        "p95Milliseconds": round(p95, 3),
        "p99Milliseconds": round(percentile(latencies, 0.99), 3),
        "maximumMilliseconds": round(max(latencies, default=0), 3),
        "p95LimitMilliseconds": arguments.p95_limit_milliseconds,
        "throughputPerSecond": round(throughput, 3),
        "minimumThroughputPerSecond": (
            arguments.minimum_throughput_per_second
        ),
    }


def main() -> int:
    arguments = parse_arguments()
    try:
        report = build_report(arguments)
    except Exception as exception:
        print(f"端到端容量测试无法执行：{exception}", file=sys.stderr)
        return 2

    output = Path(arguments.output)
    if not output.is_absolute():
        print("容量报告路径必须是绝对路径", file=sys.stderr)
        return 2
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    output.chmod(0o600)

    print(
        "端到端可信查询容量："
        f"采样 {report['sampleRequestCount']} 次，"
        f"95 分位 {report['p95Milliseconds']} 毫秒，"
        f"吞吐 {report['throughputPerSecond']} 次每秒，"
        f"错误 {report['errorCount']} 次"
    )
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
