# 60 实施记录

Stage: implementation  
Category: stage-main  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, product engineer, senior backend engineer, senior frontend engineer, UI engineer  
Approval: agent-approved  
Inputs: `docs/50-delivery-plan.md`, `docs/facts/environment-readiness.md`, `backend/pom.xml`  
Outputs: S1/S2/S3/S4/S4.5 实施证据、当前状态和下一步  
Depends On: `docs/32-control-surface.md`, `docs/50-delivery-plan.md`  
Load When: implementation/quality/S5-prep  
Next: 先完成前端控制台企业级设计系统加固，再启动 S5.1 统一模型调用、trace 和 usage 基线

## 当前结论

S1/S2/S2.1/S3/S4 已完成，Build Ready 对 Provider Catalog 和 RBAC/menu 闭环为 `pass-for-s4`。当前追加 S4.5 Frontend Console Design System Hardening，用于修复现有控制台“可用但不够企业级”的设计系统缺口。

证据：真实研发依赖、后端 Maven 多模块、模块类型目录、Spring Boot 鉴权 API、Vue 3 控制台、Provider Catalog、平台模型基础配置、平台模型选项目录、租户模型配置、RBAC/menu、前后端同时启动和页面操作均已验证。后续进入 S5.1 前，必须继续沿用真实环境完成标准，不得把未验证页面或 mock 数据作为完成态；新增前端页面还必须满足 `docs/45-details/03-frontend-console-design-system.md`。

## S4.5 Frontend Console Design System Hardening

写集：

- `docs/45-details/03-frontend-console-design-system.md`
- `docs/00-index.md`
- `docs/31-context-loading.md`
- `docs/32-control-surface.md`
- `docs/33-team-topology.md`
- `docs/40-architecture-and-tech-spec.md`
- `docs/45-module-and-service-design.md`
- `docs/50-delivery-plan.md`
- `docs/60-implementation-notes.md`
- `frontend/console-web/src/App.vue`
- `frontend/console-web/src/styles.css`
- `scripts/generate_context_pack.py`
- `scripts/harness_doc_guard.py`

设计结论：

- 前端控制台以企业运维/配置工作台为定位，不做营销型页面或展示大屏。
- S5.1 新增前端调用入口前，必须先将现有页面沉淀为统一 token、应用壳、导航、表单、表格、按钮、消息和状态规范。
- 平台管理和租户平台继续共用 RBAC 菜单事实源，前端样式和按钮禁用只做体验增强，不替代后端权限。
- 本次只做设计系统和现有页面视觉加固，不新增知识库、tool、数据库知识源、完整观测大屏或模型调用假页面。
- 用户确认前端应参考 OpenAI 风格和 macOS 风格后，三角色将方向收敛为 `A2: OpenAI 式克制 + macOS 式桌面质感`，概念图为 `docs/assets/frontend-design/a2-openai-macos-console-concept.png`。

实现结果：

- 新增前端控制台设计系统详细设计，并纳入 `00/31/32/33/40/45/50` 和 gate evidence。
- `frontend/console-web/src/App.vue` 按 A2 概念图重组应用壳、macOS 式 sidebar、toolbar、租户上下文、用户区、摘要、guardrail、模型配置表单和 provider inventory。
- `frontend/console-web/src/styles.css` 改为 CSS token 驱动，覆盖应用壳、RBAC 菜单、toolbar、摘要、表单、表格、按钮、消息、权限矩阵、status badge 和响应式规则。
- 用户复核指出实现仍未达到主流企业平台审美和自适应验收标准后，本轮将 A2 升级为 `A2+` 验收：产品工程师负责首屏真实任务验收，UI 工程师负责主流 SaaS 控制台质感，资深前端工程师负责 1440/1280/1024/390 等断点稳定性。
- `frontend/console-web/src/styles.css` 补强 `--sidebar-width`、`--content-max`、`--page-gutter` 等稳定布局 token，桌面控制台保持内容最大宽和统一 gutter，移动端菜单改为紧凑横向滚动导航，避免小屏首屏被纵向菜单占满。
- `frontend/console-web/src/App.vue` 为 RBAC 菜单补充按菜单类型区分的导航图标 class，并将实现文案统一回中文企业后台表达，降低 demo 感。
- 现有 `App.vue` 的真实 API contract、RBAC 菜单来源和测试断言未改变。
- `scripts/generate_context_pack.py` 和 `scripts/harness_doc_guard.py` 已识别前端设计系统文档。

验证命令：

```bash
pnpm --dir frontend/console-web test
pnpm --dir frontend/console-web build
deploy/scripts/healthcheck.sh
python3 scripts/generate_context_pack.py
python3 scripts/harness_doc_guard.py
git diff --check
```

验证结果：

- `App.spec.ts`：2 个测试通过。
- `vue-tsc --noEmit && vite build` 通过，生成 `dist/index.html`、CSS 和 JS bundle。
- `deploy/scripts/healthcheck.sh` 通过，PostgreSQL、Redis、MinIO 均 healthy。
- `generate_context_pack.py` 已重写 `docs/_generated/repo-preflight.json` 和 `docs/_generated/context-pack.json`。
- `harness_doc_guard.py` 通过，确认前端设计系统进入上下文加载和交付计划。
- `git diff --check` 通过。

页面 smoke：

- 本机 `8080` 后端、`5173` 前端和 Docker Compose 依赖已处于运行状态。
- Chrome headless 真实登录 `demo / admin / AiPlatform2026!` 后进入控制台，当前页面标题为 `模型配置`，授权菜单数量为 `6`。
- 桌面视口 `1440x1000`：`scrollWidth=1440`，无页面横向溢出，API 错误和 console error 均为空。
- 移动视口 `390x900`：`scrollWidth=390`，无页面横向溢出，当前菜单仍为 `模型配置`。
- A2 截图证据保存在 `.codex-runtime/frontend-console-a2-desktop.png` 和 `.codex-runtime/frontend-console-a2-mobile.png`。
- A2+ 多分辨率截图证据保存在 `.codex-runtime/frontend-console-a2plus-1440.png`、`.codex-runtime/frontend-console-a2plus-1024.png` 和 `.codex-runtime/frontend-console-a2plus-390.png`。

## S1 Dev Environment

写集：

- `deploy/docker-compose.yml`
- `deploy/.env.example`
- `deploy/scripts/up.sh`
- `deploy/scripts/down.sh`
- `deploy/scripts/healthcheck.sh`
- `docs/facts/environment-readiness.md`

验证命令：

```bash
docker --version
docker compose version
cp deploy/.env.example deploy/.env
deploy/scripts/up.sh
deploy/scripts/healthcheck.sh
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
```

验证结果：

- Docker `29.2.1`，Docker Compose `v5.1.0`。
- PostgreSQL、Redis、MinIO 均 healthy。
- PostgreSQL `pg_isready` 通过。
- Redis `PONG` 通过。
- MinIO `mc ready local` 通过。

## S2 Backend Maven Baseline

写集：

- `backend/pom.xml`
- `backend/infrastructure/ai-platform-bom/pom.xml`
- `backend/quality/ai-platform-architecture-tests/**`
- `backend/apps/ai-platform-boot/**`
- `backend/infrastructure/ai-platform-shared/**`
- `backend/infrastructure/ai-platform-ai/**`
- `backend/infrastructure/ai-platform-cache/**`
- `backend/infrastructure/ai-platform-security/**`
- `backend/infrastructure/ai-platform-persistence/**`
- `backend/infrastructure/ai-platform-storage/**`
- `backend/infrastructure/ai-platform-telemetry/**`
- `backend/business/ai-platform-modules/tenant/**`

TDD 记录：

- Red：`BackendMavenBaselineTest` 先失败，失败点为必需模块未声明、基础设施模块不存在。
- Green：补齐 Maven 多模块、BOM、平台基础设施骨架、shared kernel、tenant 模块和结构测试后通过。

验证命令：

```bash
mvn -f backend/pom.xml test
```

验证结果：

- 12 个 Maven reactor 模块全部 `SUCCESS`。
- `BackendMavenBaselineTest`：Tests run `4`，Failures `0`，Errors `0`。
- 第三方依赖版本由 `ai-platform-bom` 集中管理。
- 后端 `groupId` 固定为 `org.fujay`，所有后端模块 `artifactId` 均以 `ai-` 开头。

## S2.1 Backend Module Type Layout

写集：

- `backend/pom.xml`
- `backend/apps/**`
- `backend/infrastructure/**`
- `backend/business/**`
- `backend/quality/**`

TDD 记录：

- Red：`BackendMavenBaselineTest` 先失败，失败点为 Maven modules 仍平铺在 `backend/` 下，缺少类型目录。
- Green：按 `apps/infrastructure/business/quality` 迁移目录，修正 Maven module path 和 parent `relativePath` 后通过。
- Follow-up：用户将 BOM 与 shared 归入 `backend/infrastructure`，已同步结构测试、脚本和文档事实源；当前后端类型目录收敛为 `apps / infrastructure / business / quality`。
- Follow-up：root reactor 顺序已调整为 BOM -> shared -> infrastructure -> business -> boot -> quality，便于人工审核和结构测试锁定。

额外修正：

- Java 用户可见响应和异常描述改为中文。
- architecture test 断言描述改为中文。

验证结果：

- `mvn -f backend/pom.xml test`：Tests run `5`，Failures `0`，Errors `0`。

## 后续限制

S4 之前不得新增知识库、tool、完整观测大屏或非 Provider Catalog 范围的业务功能页面。

## S4 Provider Catalog

范围：

- `backend/business/ai-platform-modules/model/**`
- `backend/infrastructure/ai-platform-ai/**`
- `backend/infrastructure/ai-platform-security/SecretCipher.java`
- `backend/apps/ai-platform-boot/src/main/resources/db/migration/V2__s4_model_provider_catalog.sql`
- `frontend/console-web/**`
- `docs/45-details/04-provider-model-gateway-design.md`

设计结论：

- API Key 是 Provider 配置项，必须通过 model/provider 配置功能入库。
- 数据库只保存 `api_key_ciphertext` 和 `api_key_last4`，响应、日志、trace、cache 不得出现明文。
- `deploy/.env` 只作为本地 bootstrap 输入或部署密钥来源，不作为长期产品事实源。
- 数据库主键统一为 `id BIGINT PRIMARY KEY`，由雪花 Long ID 生成；`tenant_id`、`provider_id`、`model_id` 是 Long 关联键，`tenant_code`、`provider_name`、`model_code` 不得作为主键。
- `AiGatewayFacade` 只做门面分发，具体供应商调用通过 `AiProviderType`、`AiGatewayClientFactory`、`AiGatewayClient` 扩展，OpenAI 逻辑位于 OpenAI client adapter。
- OpenAI 首期 `o3` 只能作为 `CHAT` 能力模型；`EMBEDDING` 必须使用 embedding 能力型号。
- 模型参数按能力类型校验，OpenAI 首期不支持 `topK`。

TDD 记录：

- Red：`ModelProviderApplicationServiceTest.rejectsO3AsEmbeddingModelBeforeProviderCall` 先失败，暴露 record compact constructor 中使用字段而非构造参数导致能力校验未生效。
- Green：改为显式传入 `ProviderType` 做静态校验，`o3` 作为 `EMBEDDING` 被拒绝。

已通过验证：

```bash
mvn -f backend/pom.xml -pl business/ai-platform-modules/model -am test
pnpm --dir frontend/console-web test
mvn -f backend/pom.xml test
pnpm --dir frontend/console-web build
python3 scripts/generate_context_pack.py && python3 scripts/harness_doc_guard.py
python3 -m py_compile scripts/harness_repo_preflight.py scripts/generate_context_pack.py scripts/harness_doc_guard.py
deploy/scripts/healthcheck.sh
```

当前真实调用结论：

- 已使用 `deploy/.env` 中的 OpenAI Key 通过 Provider Catalog API 保存 OpenAI provider，API Key 进入数据库密文字段。
- `POST /api/model-providers/test` 已调用真实 OpenAI `o3`，供应商返回 `429 insufficient_quota`。
- 平台已把失败归一为 `MODEL-502-PROVIDER`，并写入 `model_invocation_record`。
- SiliconFlow provider 已配置到数据库，`POST /api/model-providers/test` 使用 `tencent/Hunyuan-MT-7B` 真实调用成功。
- OpenAI 额度失败不是代码或配置边界问题；S4 以 SiliconFlow 真实成功调用作为 provider runtime 证据，OpenAI 作为账号额度残余风险保留。

## S4.1 平台管理与租户平台菜单闭环

范围：

- `backend/business/ai-platform-modules/tenant/**`
- `backend/business/ai-platform-modules/model/**`
- `backend/apps/ai-platform-boot/src/main/resources/db/migration/V3__s5_rbac_menu_and_platform_model_catalog.sql`
- `frontend/console-web/**`
- `docs/32-control-surface.md`
- `docs/40-architecture-and-tech-spec.md`
- `docs/45-module-and-service-design.md`
- `docs/45-details/04-provider-model-gateway-design.md`
- `docs/50-delivery-plan.md`

实现结论：

- IAM 增加角色、权限、菜单、用户角色、角色权限、角色菜单表，统一使用 `id BIGINT PRIMARY KEY` 和 Long 关联键。
- `/api/auth/me` 返回当前用户角色、权限和菜单，前端控制台只根据该接口渲染平台管理和租户平台菜单。
- 平台管理已提供租户账户列表和启用/禁用 API，并通过 Sa-Token 权限注解保护。
- 平台管理已提供角色权限配置 API：`GET /api/admin/roles` 查询角色、权限点和菜单，`PUT /api/admin/roles/{roleId}/bindings` 保存角色权限和菜单绑定。
- Provider Catalog 拆分为平台模型选项目录和租户模型配置：provider type、baseUrl、modelCode、capabilityType 和默认参数来自平台目录；租户只保存自己的密钥、预算和模型启用配置。
- 前端增加左侧菜单、平台租户页、角色权限配置页和租户模型配置页；平台租户页可启用/禁用租户，角色权限页可保存权限点和菜单绑定，模型配置页使用 `GET /api/model-providers/options` 下拉选择并保存到租户配置。

TDD 记录：

- Backend Red：`AuthApiSmokeTest` 先断言当前用户必须返回平台管理员角色、权限、菜单，平台租户管理、角色权限配置和模型选项目录 API 必须可用。
- Backend Green：补齐 RBAC/menu migration、Sa-Token permission bridge、平台租户管理接口、角色绑定接口和模型选项目录查询后通过。
- Frontend Red：`App.spec.ts` 先断言页面必须渲染平台管理、租户平台菜单，租户页必须能禁用租户，角色权限页必须能保存绑定，并从模型选项目录生成保存请求。
- Frontend Green：补齐菜单驱动控制台、租户模型配置下拉、平台租户页启停按钮、角色权限保存页和权限错误展示后通过。

已通过验证：

```bash
mvn -f backend/pom.xml test
pnpm --dir frontend/console-web test
pnpm --dir frontend/console-web build
python3 scripts/generate_context_pack.py
python3 scripts/harness_doc_guard.py
deploy/scripts/healthcheck.sh
git diff --check
```

真实运行验证：

- 后端重新打包并以 `java -jar backend/apps/ai-platform-boot/target/ai-platform-boot-0.1.0-SNAPSHOT.jar` 启动，监听 `8080`。
- Flyway 在真实 `public` schema 从 V2 迁移到 V3，RBAC/menu 和平台模型选项目录表已应用。
- 前端 Vite 仍监听 `5173`，真实页面可进入模型配置和租户管理菜单。
- `POST /api/auth/login` 使用 `demo / admin / AiPlatform2026!` 登录成功。
- `GET /api/auth/me` 返回 `PLATFORM_ADMIN`、`platform:tenant:write`、`model:tenant-config:write`、`platform.tenants`、`tenant.models`。
- `PATCH /api/admin/tenants/{tenantId}/status` 完成 `DISABLED -> ENABLED` 回切，最终租户状态为 `ENABLED`。
- `GET /api/admin/roles` 返回角色、权限点和菜单；`PUT /api/admin/roles/{roleId}/bindings` 可保存角色权限和菜单绑定。
- `GET /api/model-providers/options` 返回 `OPENAI`、`OPENAI_COMPATIBLE`、`o3`、`tencent/Hunyuan-MT-7B`。
- `GET /api/tenants/1` 返回 HTTP 403、`COMMON-403`、`没有权限访问该资源`。

## S4.4 完整 IAM 平台管理闭环

范围：

- `backend/business/ai-platform-modules/tenant/**`
- `backend/apps/ai-platform-boot/src/test/java/org/fujay/ai/platform/boot/AuthApiSmokeTest.java`
- `frontend/console-web/src/App.vue`
- `frontend/console-web/src/App.spec.ts`
- `frontend/console-web/src/styles.css`
- `docs/facts/iam-rbac-official-research.md`
- `docs/32-control-surface.md`
- `docs/45-module-and-service-design.md`
- `docs/50-delivery-plan.md`

实现结论：

- 平台管理补齐租户创建、租户更新、租户启停；创建租户会初始化管理员、默认角色、默认权限、默认菜单。
- IAM 管理补齐角色、权限点、菜单的保存 API：`POST /api/admin/roles`、`POST /api/admin/permissions`、`POST /api/admin/menus`。
- 默认权限目录增加 `rbac:permission:read/write`、`rbac:menu:write`；默认菜单增加 `platform.roles`、`platform.permissions`、`platform.menus`。
- 角色绑定接口继续作为角色授权入口，保存 `permissionCodes` 和 `menuCodes` 后 Sa-Token permission bridge 实时生效。
- 前端左侧菜单仍以 `/api/auth/me` 为唯一事实源，支持平台管理下的租户管理、角色管理、权限管理、菜单管理和模型基础配置。
- 前端页面内写按钮按 permission code 禁用；后端接口仍由 Sa-Token 权限注解保护。

TDD 记录：

- Backend Red：`AuthApiSmokeTest` 先要求租户创建/更新、租户管理员登录、权限点保存、菜单保存、角色保存和角色绑定；现有代码缺少对应 API。
- Backend Green：补齐 application command、controller、repository upsert、默认权限/菜单和审计后通过。
- Frontend Red：`App.spec.ts` 先要求登录后渲染角色/权限/菜单管理入口，并能提交租户、权限点、菜单、角色和角色绑定表单；现有页面缺少这些表单。
- Frontend Green：补齐菜单树渲染、平台管理表单、角色授权复选框和状态展示后通过。

已通过验证：

```bash
mvn -f backend/pom.xml -pl apps/ai-platform-boot -am -Dtest=AuthApiSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test
npm test -- --run App.spec.ts
npm run build
```

## S4.2 真实权限、CORS 与页面闭环

范围：

- `backend/infrastructure/ai-platform-security/**`
- `backend/apps/ai-platform-boot/src/main/java/org/fujay/ai/platform/boot/config/WebCorsConfiguration.java`
- `backend/apps/ai-platform-boot/src/test/java/org/fujay/ai/platform/boot/AuthApiSmokeTest.java`
- `frontend/console-web/src/App.vue`
- `frontend/console-web/src/App.spec.ts`
- `frontend/console-web/src/styles.css`
- `docs/evidence/s4-provider-catalog-evidence-2026-05-10.md`

TDD 记录：

- Backend Red：`AuthApiSmokeTest` 增加 RBAC 负向断言后先失败，移除 `rbac:role:read` 后 `GET /api/admin/roles` 仍返回 200，暴露 `@SaCheckPermission` 没有接入 MVC 拦截链。
- Backend Green：`ai-platform-security` 增加 Sa-Token MVC 拦截配置，并补充 `spring-webmvc` 编译依赖；负向权限测试转绿。
- Browser Red：真实页面租户状态操作返回 `Invalid CORS request`。
- Backend Green：CORS 允许 `PATCH`，Sa-Token 拦截器跳过 `OPTIONS` 预检；新增 CORS preflight 测试后转绿。
- Frontend Red：模型页当前选中 SiliconFlow 模型时，连接测试提示仍指向列表第一个 OpenAI 配置。
- Frontend Green：连接测试目标改为当前已保存的选中聊天模型；未保存时禁用测试并提示先保存当前配置。

已通过验证：

```bash
mvn -f backend/pom.xml -pl apps/ai-platform-boot -am -Dtest=AuthApiSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml test
pnpm --dir frontend/console-web test
pnpm --dir frontend/console-web build
deploy/scripts/healthcheck.sh
```

真实运行验证：

- API：`demo / admin / AiPlatform2026!` 登录后移除 `rbac:role:read`，`GET /api/admin/roles` 返回 HTTP 403、`COMMON-403`；随后恢复完整权限并验证角色查询恢复。
- 页面：模型配置页显示 `将测试：SiliconFlow / tencent/Hunyuan-MT-7B`，点击连接测试后返回连接成功。
- 页面：租户管理页完成 `ENABLED -> DISABLED -> ENABLED` 回切，最终租户状态为 `已启用`。
- 页面：角色权限页点击保存后显示 `角色权限已保存`。
- 页面：点击 `校验 403` 后显示 `没有权限访问该资源`。
- 后端和前端当前仍分别监听 `8080` 和 `5173`。

## S4.3 平台模型基础配置闭环

范围：

- `backend/business/ai-platform-modules/model/**`
- `backend/business/ai-platform-modules/tenant/**`
- `backend/apps/ai-platform-boot/src/test/java/org/fujay/ai/platform/boot/AuthApiSmokeTest.java`
- `frontend/console-web/src/App.vue`
- `frontend/console-web/src/App.spec.ts`
- `frontend/console-web/src/styles.css`
- `docs/45-details/04-provider-model-gateway-design.md`
- `docs/50-delivery-plan.md`
- `docs/60-implementation-notes.md`

实现结论：

- 平台管理新增“模型基础配置”菜单 `platform.model-catalog`，由 `model:platform-catalog:read/write` 控制。
- 平台管理员可通过 `GET /api/admin/model-catalog` 读取 provider type 和 model option 全量目录。
- 平台管理员可通过 `POST /api/admin/model-catalog/provider-types` 保存供应商类型、显示名、默认 baseUrl、状态和排序。
- 平台管理员可通过 `POST /api/admin/model-catalog/model-options` 保存模型编码、显示名、能力类型、默认参数、状态和排序。
- 租户模型配置仍只消费 `GET /api/model-providers/options` 中启用的目录项；租户不能自由输入 provider type、baseUrl、modelCode 或 capabilityType。

TDD 记录：

- Backend Red：`ModelProviderApplicationServiceTest.platformCatalogConfigurationBecomesTenantSelectableOption` 先失败，缺少平台目录保存命令和仓储能力。
- Backend Green：补齐平台目录 command、application service、MyBatis-Plus repository upsert 和平台目录 controller 后通过。
- API Red：`AuthApiSmokeTest` 先断言登录用户必须拥有平台模型基础配置权限/菜单，并且保存平台模型选项后租户 options 可读取新模型。
- API Green：补齐默认权限、菜单和 `/api/admin/model-catalog/**` 后通过。
- Frontend Red：`App.spec.ts` 先断言平台菜单保存模型基础配置后，租户模型配置页必须出现新增模型选项。
- Frontend Green：补齐平台模型基础配置页面、保存交互和租户下拉联动后通过。

已通过验证：

```bash
mvn -f backend/pom.xml -pl business/ai-platform-modules/model -am -Dtest=ModelProviderApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml -pl apps/ai-platform-boot -am -Dtest=AuthApiSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm --dir frontend/console-web test
pnpm --dir frontend/console-web build
```

真实运行验证：

- 后端使用新 jar 在 `8080` 启动，`GET /actuator/health` 返回 `UP`。
- 前端 Vite 继续监听 `5173`。
- Chrome 页面刷新后 `/api/auth/me` 返回 6 个授权菜单，平台管理出现“模型基础配置”。
- 页面进入“模型基础配置”，点击“保存模型选项”后显示 `模型选项已保存`，目录状态新增 `Qwen2.5-7B 免费对话模型 / Qwen/Qwen2.5-7B-Instruct`。
- 页面切回租户“模型配置”，模型型号下拉出现并可选 `Qwen2.5-7B 免费对话模型`，选中后 baseUrl、modelCode、capabilityType、contextWindow、maxOutputTokens 等字段由平台目录带出。

S5.1 边界：

- 只允许推进统一模型调用入口、trace、usage/cost 基础记录和调用结果契约。
- 不得在 S5.1 同时展开知识库、数据库知识源、tool runtime 或完整观测大屏。

S3 前置条件和实现结论：

- 部署模式：私有化优先，本地 Docker Compose 依赖 + 前后端本机进程。
- 前端技术栈：Vue 3 + TypeScript + Vite。
- 租户隔离等级：共享库共享表 + `tenant_id` 强隔离 + MyBatis-Plus tenant interceptor。
- `deploy/scripts/healthcheck.sh` 仍然通过。
- `mvn -f backend/pom.xml test` 仍然通过。
- `frontend/console-web` 可启动、测试和构建。
- Spring Boot 后端通过可执行 jar 本机启动，连接真实 PostgreSQL、Redis、MinIO 依赖。
- 控制台页面登录、受保护租户上下文、跨租户 403、登出流程在真实后端上通过。

## S3 App Skeleton + Auth

计划范围：

- 后端 Spring Boot 可启动。
- PostgreSQL migration 可执行。
- Sa-Token + Redis-backed session 可用。
- MyBatis-Plus tenant interceptor 作为共享库共享表隔离基线。
- 管理员登录、登出、当前用户、当前租户、跨租户 403 可验证。
- Vue 3 + TypeScript + Vite 前端控制台可启动，页面操作走真实 API。

实际写集：

- `backend/apps/ai-platform-boot/**`
- `backend/infrastructure/ai-platform-shared/**`
- `backend/infrastructure/ai-platform-security/**`
- `backend/infrastructure/ai-platform-persistence/**`
- `backend/business/ai-platform-modules/tenant/**`
- `frontend/console-web/**`
- `docs/**`

TDD 记录：

- Backend Red：`AuthApiSmokeTest` 先失败，失败点为 `/api/auth/login` 返回 404。
- Backend Green：补齐 migration、Sa-Token session facade、tenant access application service、MyBatis-Plus repository、controller 和统一异常后通过。
- Frontend Red：`App.spec.ts` 先失败，失败点为缺少 `App.vue` 和控制台登录行为。
- Frontend Green：补齐 Vue 控制台、登录表单、受保护租户上下文、403 检查和登出后通过。

运行验证：

```bash
deploy/scripts/healthcheck.sh
mvn -f backend/pom.xml test
pnpm --dir frontend/console-web test
pnpm --dir frontend/console-web build
mvn -f backend/pom.xml -pl apps/ai-platform-boot -am -DskipTests package
java -jar backend/apps/ai-platform-boot/target/ai-platform-boot-0.1.0-SNAPSHOT.jar
pnpm --dir frontend/console-web dev
```

API smoke 结果：

- `GET /actuator/health` 返回 `UP`。
- `POST /api/auth/login` 返回 `code=0`、`message=成功`、`tokenName=satoken`。
- `GET /api/auth/me` 返回 `tenant-demo`、`平台管理员`、`platform:tenant:read`。
- `GET /api/tenants/current` 返回 `演示租户`。
- `GET /api/tenants/tenant-other` 返回 HTTP 403、`COMMON-403`、`没有权限访问该资源`。
- `POST /api/auth/logout` 成功后再次访问 `/api/auth/me` 返回 HTTP 401、`COMMON-401`、`请先登录`。

页面 smoke 结果：

- 系统 Chrome headless DevTools 协议真实加载 `http://127.0.0.1:5173/`。
- 页面输入租户、用户名、密码并提交登录。
- 登录后显示 `平台管理员` 和 `演示租户`。
- 点击 `校验 403` 后显示 `没有权限访问该资源`。
- 点击 `登出` 后回到登录页。
- runtime errors：0。
- loading failures：0。
- 非预期 HTTP 4xx/5xx：0。

偏差处理：

- 原计划命令 `mvn -f backend/pom.xml spring-boot:run -pl apps/ai-platform-boot -am` 会在父 reactor 上尝试解析 main class，不能作为验收启动命令。已改为 `package + java -jar`，并回写 `docs/facts/command-catalog.md`。
- headless Chrome 初次页面验证发现 `/favicon.ico` 404；已在前端补充 `public/favicon.svg` 和 `index.html` favicon 链接后重跑通过。
