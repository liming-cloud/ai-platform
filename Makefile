.PHONY: project-status project-verify resume-context validate

project-status:
	python3 scripts/project_governance.py status

project-verify:
	python3 -m unittest scripts.quality.test_project_governance
	python3 scripts/project_governance.py verify

resume-context:
	python3 scripts/project_governance.py resume

validate: project-verify
