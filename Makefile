.PHONY: project-status project-verify resume-context backend-test frontend-test validate

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

validate: project-verify backend-test frontend-test
