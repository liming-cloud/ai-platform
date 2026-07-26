.PHONY: project-status project-verify resume-context backend-test frontend-test demo-fixtures-verify deploy-config deploy-up deploy-smoke acceptance-knowledge-space acceptance-model-provider acceptance-model-deployment acceptance-index-search acceptance-assistant acceptance-trusted-query acceptance-query-web acceptance-query-quota acceptance-query-operations performance-index-search deploy-down validate

project-status:
	python3 scripts/project_governance.py status

project-verify:
	python3 -m unittest scripts.quality.test_project_governance
	python3 scripts/project_governance.py verify

resume-context:
	python3 scripts/project_governance.py resume

backend-test:
	./mvnw -q clean test

frontend-test:
	cd frontend && npm run typecheck && npm run lint && npm run test && npm run build

demo-fixtures-verify:
	python3 -m unittest scripts.quality.test_demo_fixtures
	python3 scripts/quality/demo_fixtures.py

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

performance-index-search:
	bash scripts/development/performance-index-search.sh

deploy-down:
	docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml down

validate: project-verify backend-test frontend-test demo-fixtures-verify
