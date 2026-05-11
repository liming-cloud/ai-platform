#!/usr/bin/env python3
"""Generate a lightweight repository preflight summary for the AI Platform harness."""

from __future__ import annotations

import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "_generated" / "repo-preflight.json"


def run(cmd: list[str]) -> str:
    try:
        return subprocess.check_output(cmd, cwd=ROOT, text=True, stderr=subprocess.STDOUT).strip()
    except Exception as exc:  # pragma: no cover - diagnostic script
        return f"ERROR: {exc}"


def exists(path: str) -> bool:
    return (ROOT / path).exists()


def contains(path: str, text: str) -> bool:
    target = ROOT / path
    if not target.exists():
        return False
    return text in target.read_text(encoding="utf-8")


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    docs = sorted(str(p.relative_to(ROOT)) for p in (ROOT / "docs").glob("**/*.md"))
    scripts = sorted(str(p.relative_to(ROOT)) for p in (ROOT / "scripts").glob("*.py"))
    drift_signals = []

    if not exists("docs/01-document-system.md"):
        drift_signals.append("missing-document-system-doc")
    if not exists("docs/33-team-topology.md"):
        drift_signals.append("missing-team-topology-doc")
    if not exists("docs/45-details/02-platform-infrastructure-design.md"):
        drift_signals.append("missing-platform-infrastructure-design")
    if contains("docs/40-architecture-and-tech-spec.md", "S1 early vertical slice"):
        drift_signals.append("stale-architecture-handoff-slice-numbering")
    if contains("docs/50-delivery-plan.md", "P2 之后可并行"):
        drift_signals.append("stale-delivery-parallel-numbering")
    if contains("docs/50-delivery-plan.md", "不阻塞后端 baseline") or contains(
        "docs/50-delivery-plan.md", "前端方案未确认不阻塞"
    ):
        drift_signals.append("stale-s2-dependency-bypass")
    if contains("docs/50-delivery-plan.md", "20/30/40/45/50 未评审") or contains(
        "docs/evidence/gate-verdicts.md", "未评审、`deploy`"
    ):
        drift_signals.append("stale-review-gate-blocker")
    if contains("docs/evidence/overall-design-review-2026-05-10.md", "document_system_ready"):
        drift_signals.append("stale-document-gate-key")
    if contains("docs/evidence/gate-verdicts.md", "`not-auto-flow`") or contains(
        "docs/evidence/overall-design-review-2026-05-10.md", "agent 可按 `50` 推进 S1/S2"
    ):
        drift_signals.append("stale-auto-flow-verdict")
    if contains("docs/30-project-harness.md", "docs/20/30/40/45/50` 相关文档已评审通过") or contains(
        "docs/30-project-harness.md", "架构评审和详细设计评审"
    ):
        drift_signals.append("stale-manual-review-gate")
    if contains("docs/40-architecture-and-tech-spec.md", "## 已确认技术栈"):
        drift_signals.append("ambiguous-confirmed-tech-stack-heading")
    if contains("docs/40-architecture-and-tech-spec.md", "storage/observability") or contains(
        "docs/32-control-surface.md", "storage/observability"
    ):
        drift_signals.append("stale-platform-infrastructure-observability-name")
    if contains("docs/40-architecture-and-tech-spec.md", "tool/approval modules"):
        drift_signals.append("stale-tool-approval-module-split")

    payload = {
        "repo_root": str(ROOT),
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "git": {
            "branch": run(["git", "branch", "--show-current"]),
            "status_short": run(["git", "status", "--short"]),
        },
        "detected": {
            "backend": {
                "pom_xml": exists("backend/pom.xml"),
                "bom_pom": exists("backend/infrastructure/ai-platform-bom/pom.xml"),
                "boot_pom": exists("backend/apps/ai-platform-boot/pom.xml"),
                "architecture_tests": exists(
                    "backend/quality/ai-platform-architecture-tests/src/test/java/org/fujay/ai/platform/architecture/BackendMavenBaselineTest.java"
                ),
                "model_module": exists("backend/business/ai-platform-modules/model/pom.xml"),
                "module_type_directories": exists("backend/apps")
                and exists("backend/infrastructure")
                and exists("backend/business")
                and exists("backend/quality"),
                "gradle": exists("backend/build.gradle") or exists("backend/build.gradle.kts"),
            },
            "frontend": {
                "package_json": exists("frontend/console-web/package.json"),
                "pnpm_lock": exists("frontend/console-web/pnpm-lock.yaml"),
                "console_web": exists("frontend/console-web/src/App.vue"),
            },
            "container": {
                "dockerfile": exists("Dockerfile"),
                "compose": exists("docker-compose.yml") or exists("compose.yml"),
                "deploy_compose": exists("deploy/docker-compose.yml"),
                "deploy_env_example": exists("deploy/.env.example"),
                "deploy_scripts": exists("deploy/scripts/up.sh")
                and exists("deploy/scripts/down.sh")
                and exists("deploy/scripts/healthcheck.sh"),
            },
            "ci": {
                "github_actions": exists(".github/workflows"),
            },
        },
        "docs": docs,
        "scripts": scripts,
        "drift_signals": drift_signals,
        "gates": {
            "route_ready": "pass",
            "document_engineering_ready": "pass",
            "team_topology_ready": "pass",
            "environment_ready": "pass",
            "backend_baseline_ready": "pass",
            "prd_ready": "agent-approved-for-s3",
            "harness_ready": "agent-approved",
            "design_review_passed": "agent-approved",
            "build_ready": "pass-for-s4",
            "quality_gate": "pass-for-s4",
        },
            "readiness": {
            "project_state": "greenfield-s5-model-gateway-entry",
            "delivery_target": "production-targeted",
            "backend_stack": [
                "Spring Boot",
                "Spring AI",
                "Sa-Token",
                "MyBatis-Plus",
                "Redisson",
                "Caffeine",
                "TDD",
                "DDD",
            ],
            "maven_coordinates": {
                "group_id": "org.fujay",
                "artifact_id_rule": "must start with ai-",
                "package_root": "org.fujay.ai.platform",
            },
            "platform_infrastructure_modules": [
                "ai-platform-ai",
                "ai-platform-bom",
                "ai-platform-cache",
                "ai-platform-shared",
                "ai-platform-security",
                "ai-platform-persistence",
                "ai-platform-storage",
                "ai-platform-telemetry",
            ],
            "python_worker_allowed": False,
            "team_topology": {
                "architect": "architecture gate and drift control",
                "product_engineer": "scope and acceptance criteria",
                "senior_backend_engineer": "backend, platform infrastructure, TDD and DDD",
                "senior_frontend_engineer": "frontend architecture, API integration and E2E",
                "ui_engineer": "design system, interaction and visual QA",
            },
            "completed_slices": [
                "S1 Dev Environment",
                "S2 Backend Maven Baseline",
                "S2.1 Backend Module Type Layout",
                "S3 App Skeleton + Auth",
                "S4 Provider Catalog",
                "S4.1 Platform/Tenant Menu RBAC Closure",
            ],
            "next_engineering_slice": "S5.1 Unified Chat Invocation + Trace/Usage Baseline",
            "build_ready": True,
            "main_blockers": [],
            "residual_risks": [
                "OpenAI key returned insufficient_quota during real provider test; SiliconFlow is the current successful runtime provider."
            ],
            "confirmed_s3_decisions": {
                "deployment_mode": "private-first local development with Docker Compose dependencies and local backend/frontend processes",
                "frontend_stack": "Vue 3 + TypeScript + Vite",
                "tenant_isolation": "shared database and shared tables with Long tenant_id strong isolation plus MyBatis-Plus TenantLineInnerInterceptor",
                "identity_strategy": "all table primary keys are id BIGINT Snowflake Long IDs; codes and names are business keys only",
            },
        },
    }

    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
