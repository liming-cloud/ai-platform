# S3 鉴权纵切实施证据 2026-05-10

Stage: evidence  
Category: implementation-evidence  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, product engineer, senior backend engineer, senior frontend engineer, UI engineer  
Approval: agent-approved  
Inputs: `backend`, `frontend/console-web`, `deploy`, `docs/50-delivery-plan.md`  
Outputs: S3 前后端真实运行证据  
Depends On: `docs/60-implementation-notes.md`  
Load When: implementation/quality/S4-history  
Next: 已由 S4 证据承接，当前后续为 S5.1 统一模型调用

## Team Verdict

S3 App Skeleton + Auth 已完成，Build Ready 对首个可运行纵切为 pass。

| 角色 | 结论 | 证据 |
| --- | --- | --- |
| 架构师 | pass | 未扩展模型、知识库、tool；鉴权、租户隔离和基础设施边界按 S3 范围落地 |
| 产品工程师 | pass | 验收只覆盖登录、受保护页面、当前租户、403、登出，不扩散业务范围 |
| 资深后端研发 | pass | `AuthApiSmokeTest`、`mvn -f backend/pom.xml test`、真实 API smoke 通过 |
| 资深前端工程师 | pass | Vue 3 + TypeScript + Vite 控制台测试、构建、真实页面 smoke 通过 |
| UI 工程师 | pass | 控制台首屏为企业操作界面，无营销页和假数据大屏 |

## Runtime Environment

依赖模式：私有化优先，本地 Docker Compose 依赖 + 前后端本机进程。

已验证命令：

```bash
deploy/scripts/healthcheck.sh
```

结果：

- `ai-platform-postgres` healthy。
- `ai-platform-redis` healthy。
- `ai-platform-minio` healthy。

## Backend Evidence

TDD Red：

```text
AuthApiSmokeTest expected POST /api/auth/login to return 200, actual 404.
```

Green 命令：

```bash
mvn -f backend/pom.xml -pl apps/ai-platform-boot -am -Dtest=AuthApiSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml test
mvn -f backend/pom.xml -pl apps/ai-platform-boot -am -DskipTests package
java -jar backend/apps/ai-platform-boot/target/ai-platform-boot-0.1.0-SNAPSHOT.jar
```

Green 结果：

```text
AuthApiSmokeTest: Tests run 1, Failures 0, Errors 0.
BackendMavenBaselineTest: Tests run 7, Failures 0, Errors 0.
BUILD SUCCESS.
Tomcat started on port 8080.
```

真实 API smoke：

```text
GET /actuator/health -> 200, status=UP
POST /api/auth/login -> 200, code=0, message=成功, tokenName=satoken
GET /api/auth/me -> 200, tenantId=tenant-demo, displayName=平台管理员
GET /api/tenants/current -> 200, tenantName=演示租户
GET /api/tenants/tenant-other -> 403, code=COMMON-403, message=没有权限访问该资源
POST /api/auth/logout -> 200, code=0, message=成功
GET /api/auth/me after logout -> 401, code=COMMON-401, message=请先登录
```

## Frontend Evidence

TDD Red：

```text
App.spec.ts failed because App.vue and login/tenant-console behavior did not exist.
```

Green 命令：

```bash
pnpm --dir frontend/console-web test
pnpm --dir frontend/console-web build
pnpm --dir frontend/console-web dev
```

Green 结果：

```text
App.spec.ts: 1 passed.
vue-tsc --noEmit && vite build passed.
Vite ready at http://127.0.0.1:5173/.
```

页面 smoke：

```text
系统 Chrome headless DevTools 协议加载 http://127.0.0.1:5173/.
输入 demo / admin / AiPlatform2026! 并提交登录。
登录后页面显示 平台管理员 和 演示租户。
点击 校验 403 后页面显示 没有权限访问该资源。
点击 登出 后页面回到 企业 AI 平台 登录页。
unexpectedHttpErrors: []
loadingFailures: []
runtimeErrors: []
```

## Scope Guard

S3 未实现以下后续范围：

- 模型供应商配置和调用。
- 知识库、文档上传、embedding、检索和问答。
- 企业 tool runtime、审批流和工具执行。
- usage/eval/feedback 完整大屏。

这些范围从 S4 开始按 `docs/50-delivery-plan.md` 独立切片推进。

## Known Warnings

后端本机启动在 macOS 下出现 Redisson Netty DNS 原生库缺失警告和 Commons Logging 发现提示，当前未影响 S3 功能和测试通过。后续进入生产化硬化时再按依赖树收敛。
