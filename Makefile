.PHONY: project-status project-verify resume-context backend-test frontend-test deploy-config deploy-up deploy-smoke deploy-down validate

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

deploy-config:
	docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml config --quiet

deploy-up:
	docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml up --detach --build

deploy-smoke:
	bash scripts/development/deploy-smoke.sh

deploy-down:
	docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml down

validate: project-verify backend-test frontend-test
