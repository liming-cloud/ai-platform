# 命令目录

Stage: facts  
Category: facts  
Status: current  
Review Status: agent-reviewed  
Reviewers: engineering lead  
Approval: agent-approved  
Inputs: scripts, deploy  
Outputs: 可复用命令入口  
Depends On: `docs/31-context-loading.md`, `docs/facts/environment-readiness.md`  
Load When: always-entry/before-implementation/quality  
Next: 前端控制台设计系统加固和 S5.1 统一模型调用实施前复用本命令目录

## Harness 命令

```bash
python3 scripts/harness_repo_preflight.py
python3 scripts/generate_context_pack.py
python3 scripts/harness_doc_guard.py
```

文档或控制面变更后必须按顺序运行：

```bash
python3 scripts/generate_context_pack.py
python3 scripts/harness_doc_guard.py
git diff --check
```

## 研发环境命令

```bash
cp deploy/.env.example deploy/.env
deploy/scripts/up.sh
deploy/scripts/healthcheck.sh
deploy/scripts/down.sh
```

## 后端命令

后端 Maven 多模块和 S3 鉴权纵切已生成。完整测试命令为：

```bash
mvn -f backend/pom.xml test
mvn -f backend/pom.xml verify
```

后端本机启动采用先打包再运行可执行 jar。不要使用父 reactor 直接执行 `spring-boot:run` 作为验收命令，因为 Maven 会在父模块尝试解析 main class。

```bash
mvn -f backend/pom.xml -pl apps/ai-platform-boot -am -DskipTests package
java -jar backend/apps/ai-platform-boot/target/ai-platform-boot-0.1.0-SNAPSHOT.jar
```

S3 后端 API smoke 使用真实 PostgreSQL/Redis：

```bash
node <<'NODE'
const base = 'http://127.0.0.1:8080';
async function request(path, options = {}) {
  const response = await fetch(base + path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
  });
  const body = await response.json();
  return { status: response.status, body };
}
(async () => {
  const login = await request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ tenantCode: 'demo', username: 'admin', password: 'AiPlatform2026!' })
  });
  const authHeaders = { [login.body.data.tokenName]: login.body.data.tokenValue };
  console.log(await request('/api/auth/me', { headers: authHeaders }));
  console.log(await request('/api/tenants/current', { headers: authHeaders }));
  console.log(await request('/api/tenants/tenant-other', { headers: authHeaders }));
  console.log(await request('/api/auth/logout', { method: 'POST', headers: authHeaders }));
})();
NODE
```

S2/S3/S4 结果：后端 reactor 构建成功，`BackendMavenBaselineTest` 9/9 通过，`AuthApiSmokeTest` 2/2 通过。

后端 Maven 坐标必须固定为：

- `groupId`: `org.fujay`
- `artifactId`: 以 `ai-` 开头，例如 `ai-platform-parent`、`ai-platform-bom`、`ai-platform-boot`、`ai-platform-shared`、`ai-platform-tenant`

## 前端命令

前端控制台位于 `frontend/console-web`，技术栈为 Vue 3 + TypeScript + Vite。

```bash
cd frontend/console-web
pnpm install
pnpm test
pnpm build
pnpm dev
```

前端设计系统或样式变更必须同时复核：

```bash
pnpm --dir frontend/console-web test
pnpm --dir frontend/console-web build
```

浏览器 smoke 需要覆盖登录后平台管理、租户平台菜单、表单保存、表格展示、错误消息、桌面宽度和移动宽度无溢出。

## 端到端完成验证

S3 已完成的端到端验证覆盖：

- `deploy` 真实依赖已启动。
- 后端启动。
- 前端启动。
- 页面正式操作无报错。
- 当前切片功能正常。

S4 页面操作验证覆盖模型连接测试、租户禁用/启用、角色权限保存和 403。最新证据见 `docs/evidence/s4-provider-catalog-evidence-2026-05-10.md`。
