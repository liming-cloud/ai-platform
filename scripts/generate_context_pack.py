#!/usr/bin/env python3
"""Generate the minimal context pack consumed by future implementation agents."""

from __future__ import annotations

import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "docs" / "_generated"
OUT = OUT_DIR / "context-pack.json"


def run_preflight() -> None:
    subprocess.check_call(["python3", "scripts/harness_repo_preflight.py"], cwd=ROOT)


def read_json(path: Path) -> dict:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    run_preflight()
    preflight = read_json(OUT_DIR / "repo-preflight.json")

    payload = {
        "repo_root": str(ROOT),
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "token_budget": {
            "recommended_tier": "tier-0",
            "must_read": [
                "docs/00-index.md",
                "docs/01-document-system.md",
                "docs/evidence/gate-verdicts.md",
            ],
            "read_if": {
                "document_system_review": [
                    "docs/01-document-system.md",
                    "docs/31-context-loading.md",
                    "docs/32-control-surface.md",
                    "docs/45-details/00-index.md",
                ],
                "product_review": [
                    "docs/10-discovery-brief.md",
                    "docs/20-prd-and-scope.md",
                    "docs/facts/market-sources.md",
                ],
                "architecture_review": [
                    "docs/20-prd-and-scope.md",
                    "docs/30-project-harness.md",
                    "docs/40-architecture-and-tech-spec.md",
                    "docs/45-module-and-service-design.md",
                    "docs/45-details/02-platform-infrastructure-design.md",
                ],
                "framework_integration": [
                    "docs/facts/framework-official-research.md",
                    "docs/32-control-surface.md",
                    "docs/40-architecture-and-tech-spec.md",
                    "docs/45-details/01-backend-code-organization.md",
                ],
                "iam_rbac_development": [
                    "docs/facts/iam-rbac-official-research.md",
                    "docs/32-control-surface.md",
                    "docs/45-module-and-service-design.md",
                    "docs/50-delivery-plan.md",
                ],
                "cache_design": [
                    "docs/facts/cache-official-research.md",
                    "docs/32-control-surface.md",
                    "docs/40-architecture-and-tech-spec.md",
                    "docs/45-details/01-backend-code-organization.md",
                ],
                "platform_infrastructure_development": [
                    "docs/45-details/00-index.md",
                    "docs/45-details/01-backend-code-organization.md",
                    "docs/45-details/02-platform-infrastructure-design.md",
                    "docs/50-delivery-plan.md",
                ],
                "provider_model_gateway_development": [
                    "docs/45-details/00-index.md",
                    "docs/45-details/01-backend-code-organization.md",
                    "docs/45-details/04-provider-model-gateway-design.md",
                    "docs/50-delivery-plan.md",
                ],
                "domain_recovery": [
                    "docs/evidence/ddd-architecture-drift-review-2026-05-11.md",
                    "docs/evidence/full-code-compliance-audit-2026-05-11.md",
                    "docs/45-details/01-backend-code-organization.md",
                    "docs/45-details/04-provider-model-gateway-design.md",
                    "docs/32-control-surface.md",
                ],
                "frontend_console_design": [
                    "docs/45-details/00-index.md",
                    "docs/45-details/03-frontend-console-design-system.md",
                    "docs/33-team-topology.md",
                    "docs/50-delivery-plan.md",
                ],
                "implementation_planning": [
                    "docs/31-context-loading.md",
                    "docs/32-control-surface.md",
                    "docs/33-team-topology.md",
                    "docs/50-delivery-plan.md",
                ],
                "team_coordination": [
                    "docs/33-team-topology.md",
                    "docs/50-delivery-plan.md",
                    "docs/32-control-surface.md",
                ],
                "implementation_notes": [
                    "docs/60-implementation-notes.md",
                    "docs/evidence/s1-s2-implementation-evidence-2026-05-10.md",
                    "docs/evidence/s3-auth-slice-evidence-2026-05-10.md",
                    "docs/evidence/s4-provider-catalog-evidence-2026-05-10.md",
                    "docs/facts/command-catalog.md",
                ],
                "environment_setup": [
                    "docs/facts/environment-readiness.md",
                    "docs/facts/command-catalog.md",
                    "deploy/README.md",
                    "deploy/docker-compose.yml",
                ],
                "backend_module_development": [
                    "docs/45-details/00-index.md",
                    "docs/45-details/01-backend-code-organization.md",
                    "docs/50-delivery-plan.md",
                ],
            },
        },
        "state": {
            "current_stage": "s5-model-gateway-controlled",
            "current_gate": "pass-after-ddd-recovery",
            "delivery_target": "production-targeted",
            "readiness_verdict": "pass-after-ddd-recovery",
            "completed_slices": [
                "S1 Dev Environment",
                "S2 Backend Maven Baseline",
                "S2.1 Backend Module Type Layout",
                "S3 App Skeleton + Auth",
                "S4 Provider Catalog",
                "S4.1 Platform/Tenant Menu RBAC Closure",
                "S4.2 Real Permission/CORS/UI Closure",
                "S4.3 Platform Model Catalog Admin Closure",
                "S4.4 Tenant/Role/Permission/Menu IAM Admin Closure",
                "S4.5 Frontend Console Design System Hardening",
                "S5.1 Unified Chat Invocation",
                "DDD Domain Cohesion Recovery",
            ],
            "current_slice": "S5 Model Gateway Controlled Continuation",
            "next_single_action": "Continue S5.2 only through domain-first design and TDD; keep scripts/harness_ddd_guard.py and BackendMavenBaselineTest green before adding quota, pricing, retry, or routing behavior.",
        },
        "technology_decisions": {
            "backend": [
                "Spring Boot",
                "Spring AI",
                "Sa-Token",
                "MyBatis-Plus",
                "Redisson",
                "Caffeine",
            ],
            "maven_coordinates": {
                "group_id": "org.fujay",
                "artifact_id_rule": "must start with ai-",
                "examples": [
                    "ai-platform-parent",
                    "ai-platform-bom",
                    "ai-platform-boot",
                    "ai-platform-shared",
                    "ai-platform-ai",
                    "ai-platform-cache",
                    "ai-platform-security",
                    "ai-platform-persistence",
                    "ai-platform-storage",
                    "ai-platform-telemetry",
                    "ai-platform-tenant",
                    "ai-platform-model",
                ],
                "module_type_directories": {
                    "apps": "runnable Spring Boot applications",
                    "infrastructure": "platform infrastructure integrations, BOM, and shared kernel",
                    "business": "bounded-context business modules",
                    "quality": "architecture and quality test modules"
                },
                "package_root": "org.fujay.ai.platform"
            },
            "confirmed_s3_decisions": {
                "deployment_mode": "private-first local development with Docker Compose dependencies and local backend/frontend processes",
                "frontend_stack": "Vue 3 + TypeScript + Vite",
                "frontend_design_system": "enterprise console tokens and layout rules in docs/45-details/03-frontend-console-design-system.md",
                "tenant_isolation": "shared database and shared tables with Long tenant_id strong isolation plus MyBatis-Plus tenant interceptor",
                "identity_strategy": "all table primary keys are id BIGINT Snowflake Long IDs; codes and names are business keys only"
            },
            "architecture_method": [
                "TDD",
                "DDD",
                "springboot-enterprise-ddd",
            ],
            "python_worker_allowed": False,
            "engineering_standards": [
                "Chinese code comments for non-obvious intent",
                "Chinese user-facing exception and response messages",
                "unified exception model",
                "unified API response",
            ],
        },
        "gates": {
            "route_ready": "pass",
            "document_engineering_ready": "pass",
            "team_topology_ready": "pass",
            "environment_ready": "pass",
            "backend_baseline_ready": "pass",
            "prd_ready": "agent-approved-for-s3",
            "harness_ready": "agent-approved",
            "design_review_passed": "agent-approved",
            "build_ready": "pass-after-ddd-recovery",
            "quality_gate": "pass-after-ddd-recovery",
        },
        "blockers": [],
        "residual_risks": [
            "OpenAI o3 account returned insufficient_quota; SiliconFlow tencent/Hunyuan-MT-7B is the current real successful provider test evidence."
        ],
        "team_topology": {
            "fixed_roles": [
                "architect",
                "product_engineer",
                "senior_backend_engineer",
                "senior_frontend_engineer",
                "ui_engineer",
            ],
            "source": "docs/33-team-topology.md",
            "current_activation": {
                "architect": "guard DDD domain cohesion and stop S5.2 expansion if architecture tests or DDD guard regress",
                "product_engineer": "keep S5.2 acceptance focused on quota, pricing, retry, and routing without scope creep",
                "senior_backend_engineer": "extend Model Gateway through aggregates, ports, and focused repository implementations",
                "senior_frontend_engineer": "connect frontend only to recovered backend contracts and preserve responsive enterprise console behavior",
                "ui_engineer": "guard enterprise console density, form/table states, responsive behavior, and compact operational visuals",
            },
        },
        "drift_signals": preflight.get("drift_signals", []),
        "doc_health": {
            "stage_docs_present": [
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
                "docs/50-delivery-plan.md",
                "docs/60-implementation-notes.md",
                "docs/45-details/00-index.md",
                "docs/45-details/01-backend-code-organization.md",
                "docs/45-details/02-platform-infrastructure-design.md",
                "docs/45-details/03-frontend-console-design-system.md",
                "docs/45-details/04-provider-model-gateway-design.md",
            ],
            "facts_present": [
                "docs/facts/repo-preflight.md",
                "docs/facts/environment-readiness.md",
                "docs/facts/command-catalog.md",
                "docs/facts/market-sources.md",
                "docs/facts/framework-official-research.md",
                "docs/facts/cache-official-research.md",
                "docs/facts/iam-rbac-official-research.md",
            ],
            "deploy_present": [
                "deploy/README.md",
                "deploy/.env.example",
                "deploy/docker-compose.yml",
                "deploy/scripts/up.sh",
                "deploy/scripts/down.sh",
                "deploy/scripts/healthcheck.sh",
            ],
            "evidence_present": [
                "docs/evidence/s1-s2-implementation-evidence-2026-05-10.md",
                "docs/evidence/s3-auth-slice-evidence-2026-05-10.md",
                "docs/evidence/s4-provider-catalog-evidence-2026-05-10.md",
                "docs/evidence/ddd-architecture-drift-review-2026-05-11.md",
                "docs/evidence/full-code-compliance-audit-2026-05-11.md",
            ],
            "future_docs_not_started": [
                "docs/70-quality-report.md",
                "docs/80-release-and-rollback.md",
                "docs/90-retrospective.md",
            ],
        },
        "runtime_detection": preflight.get("detected", {}),
        "recommended_next_action": "Proceed with controlled S5.2 model gateway hardening only while scripts/harness_ddd_guard.py, BackendMavenBaselineTest, backend tests, and frontend tests stay green.",
        "source_preflight": "docs/_generated/repo-preflight.json",
    }

    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
