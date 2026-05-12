#!/usr/bin/env python3
"""Validate document-system consistency before implementation agents run."""

from __future__ import annotations

import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GENERATED = ROOT / "docs" / "_generated"

REQUIRED_DOCS = [
    "docs/00-index.md",
    "docs/01-document-system.md",
    "docs/10-discovery-brief.md",
    "docs/20-prd-and-scope.md",
    "docs/30-project-harness.md",
    "docs/31-context-loading.md",
    "docs/32-control-surface.md",
    "docs/33-team-topology.md",
    "docs/40-architecture-and-tech-spec.md",
    "docs/45-module-and-service-design.md",
    "docs/45-details/00-index.md",
    "docs/45-details/01-backend-code-organization.md",
    "docs/45-details/02-platform-infrastructure-design.md",
    "docs/45-details/03-frontend-console-design-system.md",
    "docs/45-details/04-provider-model-gateway-design.md",
    "docs/50-delivery-plan.md",
    "docs/60-implementation-notes.md",
    "docs/evidence/gate-verdicts.md",
    "docs/evidence/overall-design-review-2026-05-10.md",
    "docs/evidence/team-startup-review-2026-05-10.md",
    "docs/evidence/ddd-architecture-drift-review-2026-05-11.md",
    "docs/evidence/full-code-compliance-audit-2026-05-11.md",
    "docs/evidence/s1-s2-implementation-evidence-2026-05-10.md",
    "docs/evidence/s3-auth-slice-evidence-2026-05-10.md",
    "docs/evidence/s4-provider-catalog-evidence-2026-05-10.md",
    "docs/facts/repo-preflight.md",
    "docs/facts/environment-readiness.md",
    "docs/facts/command-catalog.md",
    "docs/facts/market-sources.md",
    "docs/facts/framework-official-research.md",
    "docs/facts/cache-official-research.md",
]

SCAN_DOCS = [
    "docs/00-index.md",
    "docs/01-document-system.md",
    "docs/30-project-harness.md",
    "docs/31-context-loading.md",
    "docs/32-control-surface.md",
    "docs/33-team-topology.md",
    "docs/40-architecture-and-tech-spec.md",
    "docs/45-module-and-service-design.md",
    "docs/45-details/00-index.md",
    "docs/45-details/01-backend-code-organization.md",
    "docs/45-details/02-platform-infrastructure-design.md",
    "docs/45-details/03-frontend-console-design-system.md",
    "docs/45-details/04-provider-model-gateway-design.md",
    "docs/50-delivery-plan.md",
    "docs/60-implementation-notes.md",
]

STALE_PATTERNS = [
    ("S1 early vertical slice", "stale architecture handoff numbering"),
    ("P2 之后可并行", "stale delivery parallel numbering"),
    ("## 已确认技术栈", "ambiguous confirmed tech-stack heading"),
    ("20/30/40/45/50` 未评审", "stale review gate blocker"),
    ("20/30/40/45/50 未评审", "stale review gate blocker"),
    ("不阻塞后端 baseline", "stale S2 dependency bypass"),
    ("前端方案未确认不阻塞", "stale S2 dependency bypass"),
    ("document_system_ready", "stale document gate key"),
    ("`not-auto-flow`", "stale auto-flow verdict"),
    ("agent 可按 `50` 推进 S1/S2", "stale uncontrolled S1/S2 flow wording"),
    ("docs/20/30/40/45/50` 相关文档已评审通过", "stale implementation preflight review gate"),
    ("架构评审和详细设计评审", "stale manual architecture review gate"),
    ("storage/observability", "stale platform infrastructure name"),
    ("ai-platform-observability", "stale telemetry artifact id"),
    ("tool/approval modules", "stale approval module split"),
    ("gateway 模块", "undefined gateway module label"),
    ("approval 模块", "undefined approval module label"),
    ("telemetry/eval", "undefined telemetry/eval module label"),
]


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def read_text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def read_json(path: str) -> dict:
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def require_metadata(
    failures: list[str], path: str, *, status: str | None = None, review: str, approval: str
) -> None:
    text = read_text(path)
    if status and f"Status: {status}" not in text:
        failures.append(f"{path} must declare Status: {status}")
    if f"Review Status: {review}" not in text:
        failures.append(f"{path} must declare Review Status: {review}")
    if f"Approval: {approval}" not in text:
        failures.append(f"{path} must declare Approval: {approval}")


def max_source_mtime() -> float:
    candidates: list[Path] = []
    for base in ["docs", "scripts", "deploy"]:
        root = ROOT / base
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if not path.is_file():
                continue
            if "docs/_generated" in rel(path):
                continue
            if "__pycache__" in path.parts:
                continue
            candidates.append(path)
    return max(path.stat().st_mtime for path in candidates)


def main() -> int:
    failures: list[str] = []
    warnings: list[str] = []

    for doc in REQUIRED_DOCS:
        if not (ROOT / doc).exists():
            failures.append(f"missing required doc: {doc}")

    context_path = GENERATED / "context-pack.json"
    preflight_path = GENERATED / "repo-preflight.json"
    if not context_path.exists():
        failures.append("missing docs/_generated/context-pack.json")
    if not preflight_path.exists():
        failures.append("missing docs/_generated/repo-preflight.json")

    if failures:
        for item in failures:
            print(f"FAIL: {item}")
        return 1

    source_mtime = max_source_mtime()
    for generated in [context_path, preflight_path]:
        if generated.stat().st_mtime < source_mtime:
            failures.append(f"generated file is stale: {rel(generated)}")

    context = read_json("docs/_generated/context-pack.json")
    preflight = read_json("docs/_generated/repo-preflight.json")

    if context.get("drift_signals"):
        failures.append(f"context drift_signals not empty: {context['drift_signals']}")
    if preflight.get("drift_signals"):
        failures.append(f"preflight drift_signals not empty: {preflight['drift_signals']}")

    gates = context.get("gates", {})
    if gates.get("document_engineering_ready") not in {"pass", "agent-approved"}:
        failures.append("document_engineering_ready must be pass or agent-approved")
    if gates.get("team_topology_ready") != "pass":
        failures.append("team_topology_ready must be pass")
    if gates.get("harness_ready") != "agent-approved":
        failures.append("harness_ready must be agent-approved after document engineering review")
    if gates.get("design_review_passed") != "agent-approved":
        failures.append("design_review_passed must be agent-approved after multi-role review")
    if gates.get("environment_ready") != "pass":
        failures.append("environment_ready must be pass after S1 evidence")
    if gates.get("backend_baseline_ready") != "pass":
        failures.append("backend_baseline_ready must be pass after S2 evidence")
    if gates.get("build_ready") not in {"pass-for-s4", "blocked-ddd-architecture-drift", "pass-after-ddd-recovery"}:
        failures.append("build_ready must be pass-for-s4, blocked-ddd-architecture-drift, or pass-after-ddd-recovery")
    if gates.get("quality_gate") not in {"pass-for-s4", "blocked-ddd-architecture-drift", "pass-after-ddd-recovery"}:
        failures.append("quality_gate must be pass-for-s4, blocked-ddd-architecture-drift, or pass-after-ddd-recovery")
    if gates.get("quality_gate") == "blocked-ddd-architecture-drift" and not (
        ROOT / "docs/evidence/ddd-architecture-drift-review-2026-05-11.md"
    ).exists():
        failures.append("blocked DDD architecture drift must have evidence doc")
    if gates.get("quality_gate") == "blocked-ddd-architecture-drift" and not (
        ROOT / "docs/evidence/full-code-compliance-audit-2026-05-11.md"
    ).exists():
        failures.append("blocked DDD architecture drift must have full code compliance audit doc")

    for doc in [
        "docs/00-index.md",
        "docs/01-document-system.md",
        "docs/30-project-harness.md",
        "docs/31-context-loading.md",
        "docs/32-control-surface.md",
        "docs/33-team-topology.md",
        "docs/40-architecture-and-tech-spec.md",
        "docs/45-module-and-service-design.md",
        "docs/45-details/00-index.md",
        "docs/45-details/01-backend-code-organization.md",
        "docs/45-details/02-platform-infrastructure-design.md",
        "docs/45-details/03-frontend-console-design-system.md",
        "docs/45-details/04-provider-model-gateway-design.md",
        "docs/50-delivery-plan.md",
        "docs/60-implementation-notes.md",
        "docs/evidence/gate-verdicts.md",
        "docs/evidence/s1-s2-implementation-evidence-2026-05-10.md",
        "docs/evidence/s3-auth-slice-evidence-2026-05-10.md",
        "docs/evidence/s4-provider-catalog-evidence-2026-05-10.md",
    ]:
        require_metadata(failures, doc, status="current", review="agent-reviewed", approval="agent-approved")
    for doc in [
        "docs/evidence/ddd-architecture-drift-review-2026-05-11.md",
        "docs/evidence/full-code-compliance-audit-2026-05-11.md",
    ]:
        require_metadata(failures, doc, status="current", review="agent-reviewed", approval="agent-approved")

    gate_verdicts = read_text("docs/evidence/gate-verdicts.md")
    review_report = read_text("docs/evidence/overall-design-review-2026-05-10.md")
    if (
        "controlled-agent-flow-for-s5-model-gateway" not in gate_verdicts
        and "blocked-ddd-domain-recovery-before-s5" not in gate_verdicts
        and "controlled-agent-flow-for-s5-model-gateway-after-ddd-recovery" not in gate_verdicts
    ):
        failures.append("gate verdicts must use controlled-agent-flow-for-s5-model-gateway, blocked-ddd-domain-recovery-before-s5, or controlled-agent-flow-for-s5-model-gateway-after-ddd-recovery")
    if "controlled-agent-flow-for-s5-model-gateway" not in review_report:
        failures.append("overall design review must use controlled-agent-flow-for-s5-model-gateway")
    delivery_plan = read_text("docs/50-delivery-plan.md")
    if "S1/S2/S2.1/S3/S4 已完成" not in delivery_plan:
        failures.append("delivery plan must record completed S4 evidence")
    for decision in ["私有化优先", "Vue 3 + TypeScript + Vite", "tenant_id", "id BIGINT"]:
        if decision not in delivery_plan:
            failures.append(f"delivery plan must record confirmed S3 decision: {decision}")
    if "S4.5 Frontend Console Design System Hardening" not in delivery_plan:
        failures.append("delivery plan must record frontend console design hardening slice")
    frontend_design = read_text("docs/45-details/03-frontend-console-design-system.md")
    for required_text in ["企业级控制台", "设计 Token", "S5.1 前端入口约束", "pnpm --dir frontend/console-web test"]:
        if required_text not in frontend_design:
            failures.append(f"frontend console design system missing required guard: {required_text}")
    team_doc = read_text("docs/33-team-topology.md")
    for role in ["架构师", "产品工程师", "资深后端研发", "资深前端工程师", "UI 工程师"]:
        if role not in team_doc:
            failures.append(f"team topology missing role: {role}")
    if "S1、S2、S3 必须串行" not in team_doc:
        failures.append("team topology must preserve serial S1/S2/S3 rule")
    team_evidence = read_text("docs/evidence/team-startup-review-2026-05-10.md")
    if "固定团队已按 5 个角色启动" not in team_evidence:
        failures.append("team startup evidence must record fixed 5-role startup")
    if "S1/S2/S2.1/S3/S4 已完成" not in team_evidence:
        failures.append("team startup evidence must preserve S4 completion update")

    read_if = context.get("token_budget", {}).get("read_if", {})
    backend_reads = read_if.get("backend_module_development", [])
    if "docs/45-details/02-platform-infrastructure-design.md" in backend_reads:
        failures.append("backend_module_development must not load platform infrastructure detail by default")
    if "docs/45-details/02-platform-infrastructure-design.md" not in read_if.get(
        "platform_infrastructure_development", []
    ):
        failures.append("platform_infrastructure_development must load platform infrastructure detail")

    stage_docs = set(context.get("doc_health", {}).get("stage_docs_present", []))
    for doc in REQUIRED_DOCS:
        if doc.startswith("docs/facts/") or doc.startswith("docs/evidence/"):
            continue
        if doc not in stage_docs:
            failures.append(f"context doc_health missing stage doc: {doc}")

    index_text = read_text("docs/00-index.md")
    for doc in REQUIRED_DOCS:
        if doc.startswith("docs/_generated/"):
            continue
        if doc not in index_text and not doc.startswith("docs/evidence/overall-design-review"):
            warnings.append(f"00-index does not directly mention {doc}")

    for doc in SCAN_DOCS:
        text = read_text(doc)
        for pattern, reason in STALE_PATTERNS:
            if pattern in text:
                failures.append(f"{reason}: {doc} contains {pattern!r}")

    command_catalog = read_text("docs/facts/command-catalog.md")
    if "scripts/harness_doc_guard.py" not in command_catalog:
        failures.append("command catalog must include scripts/harness_doc_guard.py")
    if "frontend/console-web" not in command_catalog:
        failures.append("command catalog must include frontend/console-web commands")
    if "backend/infrastructure/ai-platform-bom" not in read_text("docs/45-details/01-backend-code-organization.md"):
        failures.append("backend code organization must document module type directories")
    if "docs/45-details/03-frontend-console-design-system.md" not in read_text("docs/31-context-loading.md"):
        failures.append("context loading must route frontend console design tasks to the frontend design system")
    if "frontend/console-web/src/styles.css" not in read_text("docs/60-implementation-notes.md"):
        failures.append("implementation notes must record frontend design hardening write set")
    if "backend/business/ai-platform-modules/model" not in read_text("docs/45-details/04-provider-model-gateway-design.md"):
        failures.append("provider model gateway detail must document model business module path")
    provider_gateway_detail = read_text("docs/45-details/04-provider-model-gateway-design.md")
    for required_text in ["AiGatewayClientFactory", "AiProviderType", "id BIGINT PRIMARY KEY"]:
        if required_text not in provider_gateway_detail:
            failures.append(f"provider model gateway detail missing required architecture guard: {required_text}")

    for path in (ROOT / "docs").rglob("*.md"):
        if len(path.name) > 64:
            failures.append(f"overlong doc filename: {rel(path)}")

    if failures:
        for item in failures:
            print(f"FAIL: {item}")
        for item in warnings:
            print(f"WARN: {item}")
        return 1

    print("PASS: document guard checks passed")
    for item in warnings:
        print(f"WARN: {item}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
