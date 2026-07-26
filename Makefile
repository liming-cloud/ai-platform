.PHONY: project-status project-verify resume-context delivery-contract backend-context-modules backend-test frontend-lint frontend-audit frontend-test frontend-build frontend-e2e-list deploy-env deploy-config deploy-up deploy-status deploy-initialize deploy-smoke deploy-down deploy-reset validate

BACKEND_JAVA_HOME ?= $(shell /usr/libexec/java_home -v 21 2>/dev/null)
BACKEND_ENV := $(if $(BACKEND_JAVA_HOME),JAVA_HOME=$(BACKEND_JAVA_HOME),)
DEPLOY_ENV ?= deploy/.env
DEPLOY_PROJECT ?= ai-platform-local
DEPLOY_SOURCE_REVISION ?= development
DEPLOY_COMPOSE := docker compose -p $(DEPLOY_PROJECT) --env-file $(DEPLOY_ENV) -f deploy/docker-compose.yml
DEPLOY_COMPOSE_ENV := \
	AIP_SOURCE_REVISION=$(DEPLOY_SOURCE_REVISION) \
	$(if $(DEPLOY_BIND_ADDRESS),AIP_BIND_ADDRESS=$(DEPLOY_BIND_ADDRESS)) \
	$(if $(DEPLOY_REDIS_PORT),AIP_REDIS_PORT=$(DEPLOY_REDIS_PORT)) \
	$(if $(DEPLOY_MINIO_API_PORT),AIP_MINIO_API_PORT=$(DEPLOY_MINIO_API_PORT)) \
	$(if $(DEPLOY_ROCKETMQ_NAMESRV_PORT),AIP_ROCKETMQ_NAMESRV_PORT=$(DEPLOY_ROCKETMQ_NAMESRV_PORT)) \
	$(if $(DEPLOY_ROCKETMQ_BROKER_PORT),AIP_ROCKETMQ_BROKER_PORT=$(DEPLOY_ROCKETMQ_BROKER_PORT)) \
	$(if $(DEPLOY_ROCKETMQ_FAST_PORT),AIP_ROCKETMQ_FAST_PORT=$(DEPLOY_ROCKETMQ_FAST_PORT)) \
	$(if $(DEPLOY_BACKEND_PORT),AIP_BACKEND_PORT=$(DEPLOY_BACKEND_PORT)) \
	$(if $(DEPLOY_FRONTEND_PORT),AIP_FRONTEND_PORT=$(DEPLOY_FRONTEND_PORT))

project-status:
	python3 scripts/project_governance.py status

project-verify:
	python3 -m unittest scripts.quality.test_project_governance
	python3 scripts/project_governance.py verify

resume-context:
	python3 scripts/project_governance.py resume

delivery-contract:
	python3 scripts/quality/test_delivery_cleanup.py
	sh -n deploy/bin/init-env.sh deploy/bin/upgrade-env.sh deploy/bin/validate-env.sh deploy/postgres/initialize.sh

backend-context-modules:
	python3 scripts/quality/test_validate_backend_context_modules.py
	python3 scripts/quality/validate_backend_context_modules.py

backend-test: backend-context-modules
	cd backend && $(BACKEND_ENV) mvn test

frontend-lint:
	cd frontend/console-web && npm run lint

frontend-audit:
	cd frontend/console-web && npm audit --omit=dev --audit-level=high

frontend-test:
	cd frontend/console-web && npm test

frontend-build:
	cd frontend/console-web && npm run build

frontend-e2e-list:
	cd frontend/console-web && npm run e2e:list

deploy-env:
	@test -f $(DEPLOY_ENV) || sh deploy/bin/init-env.sh $(DEPLOY_ENV)

deploy-config: deploy-env
	@set -a; . "$(DEPLOY_ENV)"; set +a; \
	  $(DEPLOY_COMPOSE_ENV) sh deploy/bin/validate-env.sh
	$(DEPLOY_COMPOSE_ENV) $(DEPLOY_COMPOSE) config --quiet

deploy-up: deploy-config
	$(DEPLOY_COMPOSE_ENV) $(DEPLOY_COMPOSE) up -d --build --wait --wait-timeout 420

deploy-status:
	$(DEPLOY_COMPOSE_ENV) $(DEPLOY_COMPOSE) ps --all

deploy-initialize:
	$(DEPLOY_COMPOSE_ENV) $(DEPLOY_COMPOSE) run --rm database-initialize

deploy-smoke:
	@set -a; . "$(DEPLOY_ENV)"; set +a; \
	  bind_address=$${DEPLOY_BIND_ADDRESS:-$${AIP_BIND_ADDRESS:-127.0.0.1}}; \
	  backend_port=$${DEPLOY_BACKEND_PORT:-$${AIP_BACKEND_PORT:-8080}}; \
	  frontend_port=$${DEPLOY_FRONTEND_PORT:-$${AIP_FRONTEND_PORT:-5173}}; \
	  test "$$bind_address" = "127.0.0.1" || { echo "deploy smoke only accepts the loopback baseline" >&2; exit 1; }; \
	  curl --fail --silent --show-error "http://$$bind_address:$$backend_port/api/v1/health" >/dev/null; \
	  curl --fail --silent --show-error "http://$$bind_address:$$backend_port/actuator/health/readiness" >/dev/null; \
	  curl --fail --silent --show-error "http://$$bind_address:$$frontend_port/" >/dev/null

deploy-down:
	$(DEPLOY_COMPOSE_ENV) $(DEPLOY_COMPOSE) down --remove-orphans

deploy-reset:
	@test "$(ALLOW_DEPLOY_RESET)" = "yes" || { echo "set ALLOW_DEPLOY_RESET=yes to delete only project $(DEPLOY_PROJECT) volumes" >&2; exit 1; }
	$(DEPLOY_COMPOSE_ENV) $(DEPLOY_COMPOSE) down --volumes --remove-orphans

validate: project-verify delivery-contract backend-test frontend-lint frontend-audit frontend-test frontend-build
