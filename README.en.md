# AI Platform

Planning repository for an enterprise multi-tenant AI platform.

The project is currently in S4 preparation after S1/S2/S2.1/S3 completion. The S3 runnable auth slice is build-ready. The delivery target is `production-targeted` because the platform must support enterprise tenants, model governance, knowledge bases, tool execution, monitoring, audit, and cost control.

## Product Positioning

AI Platform is intended to become an enterprise AI control plane and runtime platform:

- Connect multiple model providers, model types, and model versions.
- Manage enterprise knowledge bases from documents, websites, object storage, and databases.
- Provide knowledge-grounded Q&A, configurable AI applications, tool execution, and enterprise system integration.
- Monitor model usage, knowledge usage, tool runs, latency, errors, quality, cost, and user feedback.
- Support tenant isolation, RBAC, audit, secret management, quotas, and governance.

## Planning Documents

Start from [docs/00-index.md](docs/00-index.md).

Core documents:

- [Competitive analysis and discovery brief](docs/10-discovery-brief.md)
- [PRD and scope](docs/20-prd-and-scope.md)
- [Project harness](docs/30-project-harness.md)
- [Context loading rules](docs/31-context-loading.md)
- [Control surface and gates](docs/32-control-surface.md)
- [Architecture and technical spec](docs/40-architecture-and-tech-spec.md)
- [Module and service design](docs/45-module-and-service-design.md)
- [Delivery plan](docs/50-delivery-plan.md)
- [Implementation notes](docs/60-implementation-notes.md)

Facts and evidence:

- [Repository preflight facts](docs/facts/repo-preflight.md)
- [Environment readiness](docs/facts/environment-readiness.md)
- [Command catalog](docs/facts/command-catalog.md)
- [Market and competitor sources](docs/facts/market-sources.md)
- [Official framework research](docs/facts/framework-official-research.md)
- [Official cache research](docs/facts/cache-official-research.md)
- [Gate verdicts](docs/evidence/gate-verdicts.md)
- [Deviation log](docs/evidence/deviation-log.md)
- [S1/S2 implementation evidence](docs/evidence/s1-s2-implementation-evidence-2026-05-10.md)
- [S3 auth slice implementation evidence](docs/evidence/s3-auth-slice-evidence-2026-05-10.md)

## Current Verdict

- The S3 runnable auth slice is build-ready.
- S1 real development dependencies under `deploy/` passed health checks.
- S2 backend Maven modules, BOM, and platform infrastructure skeleton passed `mvn -f backend/pom.xml test`.
- S3 backend Spring Boot, real PostgreSQL/Redis/MinIO, Vue 3 console, and browser UI smoke have passed.
- Backend technology is now fixed as Spring Boot + Spring AI + Sa-Token + MyBatis-Plus + Redisson + Caffeine, using TDD + DDD and no Python worker.
- The next slice is S4 Provider Catalog; deployment mode, frontend stack, and tenant isolation level are confirmed.

## Local Helper Scripts

```bash
python3 scripts/harness_repo_preflight.py
python3 scripts/generate_context_pack.py
deploy/scripts/up.sh
deploy/scripts/healthcheck.sh
mvn -f backend/pom.xml test
pnpm --dir frontend/console-web test
pnpm --dir frontend/console-web build
```

The scripts generate `docs/_generated/repo-preflight.json` and `docs/_generated/context-pack.json` for later implementation agents.
