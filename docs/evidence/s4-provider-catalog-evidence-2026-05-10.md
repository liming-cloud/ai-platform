# S4 Provider Catalog 证据

Stage: evidence  
Category: evidence  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, product engineer, senior backend engineer, senior frontend engineer, UI engineer  
Approval: agent-approved  
Inputs: `docs/50-delivery-plan.md`, `docs/60-implementation-notes.md`, backend/frontend runtime  
Outputs: S4 运行证据、质量门禁结论、S5 准入边界  
Depends On: `docs/evidence/s3-auth-slice-evidence-2026-05-10.md`  
Load When: implementation/quality/S5-prep  
Next: 启动 S5.1 统一模型调用、trace 和 usage 基线

## Verdict

`pass-for-s4`。

S4 已形成平台模型基础配置、平台模型选项目录、租户模型配置、API Key 加密入库、Spring AI adapter/factory、Sa-Token RBAC/menu 闭环和 Vue 控制台页面。S4.4 已补齐平台管理端的租户、角色、权限点、菜单完整管理闭环。平台管理员可维护 provider type、model option、租户生命周期和 IAM 目录，租户只能从启用目录项下拉选择。真实供应商调用使用 SiliconFlow 免费模型通过；OpenAI `o3` 调用因账号额度返回 `429 insufficient_quota`，已按供应商失败归一处理，不作为代码阻塞。

## Runtime Evidence

- 后端：`java -jar backend/apps/ai-platform-boot/target/ai-platform-boot-0.1.0-SNAPSHOT.jar`，监听 `8080`。
- 前端：`pnpm --dir frontend/console-web dev`，监听 `5173`。
- 依赖：PostgreSQL、Redis、MinIO 均 healthy。
- 登录账号：`demo / admin / AiPlatform2026!`。
- 平台模型基础配置：`/api/admin/model-catalog` 可读取目录；保存 provider type 和 model option 后，租户 `/api/model-providers/options` 可读取新增启用模型。
- SiliconFlow provider test：`SiliconFlow / tencent/Hunyuan-MT-7B` 真实调用成功，页面显示连接成功。
- API RBAC 负向：移除 `rbac:role:read` 后 `GET /api/admin/roles` 返回 HTTP 403、`COMMON-403`、`没有权限访问该资源`；随后恢复权限并验证可读。
- IAM API smoke：`POST /api/admin/tenants`、`PUT /api/admin/tenants/{tenantId}`、`POST /api/admin/roles`、`POST /api/admin/permissions`、`POST /api/admin/menus`、`PUT /api/admin/roles/{roleId}/bindings` 均通过 `AuthApiSmokeTest`。
- 页面 smoke：模型连接测试、租户创建/禁用/启用回切、角色保存、权限点保存、菜单保存、角色权限菜单绑定、`校验 403` 均在前端测试中覆盖；真实页面入口来自 `/api/auth/me`。
- 页面错误：浏览器 error log 数量为 0。

## TDD Evidence

- Backend Red：`AuthApiSmokeTest` 增加 RBAC 负向断言后先失败，原因是 `@SaCheckPermission` 注解未进入 MVC 拦截链，接口仍返回 200。
- Backend Green：`ai-platform-security` 增加 Sa-Token MVC 拦截配置，并补充 `spring-webmvc` 编译依赖；负向权限测试转绿。
- Browser Red：真实页面租户状态操作返回 `Invalid CORS request`。
- Backend Green：CORS 允许 `PATCH`，Sa-Token 拦截器跳过 `OPTIONS` 预检；新增 CORS preflight 测试后转绿。
- Frontend Red：模型页当前选中 SiliconFlow 模型时，连接测试提示仍指向列表第一个 OpenAI 配置。
- Frontend Green：连接测试目标改为当前已保存的选中聊天模型；未保存时禁用测试并提示先保存当前配置。
- Backend Red：平台目录保存能力测试先失败，暴露平台管理只能读取 bootstrap 目录，不能维护租户可选模型基础配置。
- Backend Green：补齐 `/api/admin/model-catalog`、`model:platform-catalog:read/write`、平台目录 upsert 和租户 options 联动后通过。
- Frontend Red：平台模型基础配置菜单保存新模型后，租户模型配置页仍无法选择新增模型。
- Frontend Green：补齐平台模型基础配置页面和租户下拉联动后通过。
- Backend Red：完整 IAM 测试先要求租户创建/更新、租户管理员登录、角色/权限点/菜单保存和绑定；现有实现只有租户启停和角色绑定。
- Backend Green：补齐 Tenant & IAM command、controller、repository upsert、默认权限/菜单和审计后通过。
- Frontend Red：登录后的菜单测试先要求渲染租户管理、角色管理、权限管理、菜单管理并提交对应表单；现有页面缺少完整管理端。
- Frontend Green：补齐菜单树渲染、租户表单、角色表单、权限点表单、菜单表单和角色授权复选框后通过。

## Commands

```bash
deploy/scripts/healthcheck.sh
mvn -f backend/pom.xml test
mvn -f backend/pom.xml -pl business/ai-platform-modules/model -am -Dtest=ModelProviderApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml -pl apps/ai-platform-boot -am -Dtest=AuthApiSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm --dir frontend/console-web test
pnpm --dir frontend/console-web build
mvn -f backend/pom.xml -pl apps/ai-platform-boot -am package -DskipTests
python3 scripts/generate_context_pack.py
python3 scripts/harness_doc_guard.py
git diff --check
```

## S5 Boundary

S5 只允许推进统一模型调用最小闭环：模型选择、调用入口、trace、usage/cost 基础记录、quota/retry 的接口边界。不得在 S5.1 同时展开知识库、数据库知识源、tool runtime 或完整观测大屏。
