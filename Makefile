.PHONY: project-status project-verify resume-context architecture-guard backend-test frontend-test demo-fixtures-verify recovery-operations-test recovery-demo capacity-resilience-operations-test capacity-resilience-demo delivery-operations-test delivery-demo upgrade-demo deploy-config deploy-up deploy-smoke acceptance-knowledge-space acceptance-model-provider acceptance-model-deployment acceptance-index-search acceptance-assistant acceptance-trusted-query acceptance-query-web acceptance-query-quota acceptance-query-operations evaluation-demo performance-index-search performance-query-candidate-pool deploy-down validate

project-status:
	python3 scripts/project_governance.py status

project-verify:
	python3 -m unittest scripts.quality.test_project_governance
	python3 scripts/project_governance.py verify

resume-context:
	python3 scripts/project_governance.py resume

architecture-guard:
	python3 -m unittest scripts.quality.test_backend_architecture_guard
	python3 scripts/quality/backend_architecture_guard.py verify

backend-test:
	./mvnw -q clean test

frontend-test:
	cd frontend && npm run typecheck && npm run lint && npm run test && npm run build

demo-fixtures-verify:
	python3 -m unittest scripts.quality.test_demo_fixtures scripts.quality.test_demo_evaluation
	python3 scripts/quality/demo_fixtures.py

recovery-operations-test:
	python3 -m unittest scripts.quality.test_recovery_operations

recovery-demo:
	bash scripts/operations/demo-recovery-drill.sh

capacity-resilience-operations-test:
	python3 -m unittest scripts.quality.test_capacity_resilience_operations

capacity-resilience-demo:
	bash scripts/operations/demo-capacity-resilience-drill.sh

delivery-operations-test:
	python3 -m unittest scripts.quality.test_delivery_operations

delivery-demo:
	bash scripts/operations/demo-delivery-drill.sh

upgrade-demo:
	bash scripts/operations/demo-upgrade-drill.sh

deploy-config:
	docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml config --quiet

deploy-up:
	docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml up --detach --build

deploy-smoke:
	bash scripts/development/deploy-smoke.sh

acceptance-knowledge-space:
	bash scripts/development/acceptance-knowledge-space.sh

acceptance-model-provider:
	bash scripts/development/acceptance-model-provider.sh

acceptance-model-deployment:
	bash scripts/development/acceptance-model-deployment.sh

acceptance-index-search: acceptance-knowledge-space
	bash scripts/development/acceptance-index-search.sh

acceptance-assistant: acceptance-model-deployment acceptance-index-search
	bash scripts/development/acceptance-assistant.sh

acceptance-trusted-query: acceptance-assistant
	bash scripts/development/acceptance-trusted-query.sh

acceptance-query-web: acceptance-trusted-query
	bash scripts/development/acceptance-query-web.sh

acceptance-query-quota: acceptance-trusted-query
	bash scripts/development/acceptance-query-quota.sh

acceptance-query-operations: acceptance-trusted-query
	bash scripts/development/acceptance-query-operations.sh

evaluation-demo: acceptance-trusted-query
	bash scripts/development/evaluation-demo.sh

performance-index-search:
	bash scripts/development/performance-index-search.sh

performance-query-candidate-pool:
	bash scripts/development/performance-query-candidate-pool.sh

deploy-down:
	docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml down

validate: project-verify architecture-guard backend-test frontend-test demo-fixtures-verify recovery-operations-test capacity-resilience-operations-test delivery-operations-test
