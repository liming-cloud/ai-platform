# WP-0014 统一控制台查询入口退役

## 结果

ADR-0059 的统一人员 SPA 边界已落实：`ConsoleQueryWorkspace` 继续通过 `/ask` 进入 `console-web` 外壳；独立 `query-web` 应用、Compose 服务、`/query/` 反向代理、Docker 构建分支与旧验收命令均已退役。`query-runtime` 仍由控制台网关代理 `/api/v1/query-assistants` 与 `/api/v1/query-runs`，所以查询领域运行时、浏览器 HttpOnly 会话与设备/API 渠道均未删除或改变。

## 红绿与验证

- 红灯：`test_browser_session_deployment.py` 断言不存在 `query-web` 服务、公开 `/query/` 代理和可发布应用入口；旧 Compose、Nginx 与应用目录实际导致 3 项失败。
- 绿灯：6 项浏览器会话部署门禁通过，且同时覆盖普通与本地 HTTPS Nginx 配置。
- `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml config --quiet`：通过。
- `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml build console-web`：从干净 `npm ci` 依赖树构建统一控制台镜像通过，构建输入不再包含 `query-web`。
- `cd frontend && npm run typecheck && npm test && npm run build`：80 项前端测试、类型检查和唯一控制台生产构建通过。

## 边界

本切片只退役独立人员浏览器入口，不退役 `query-runtime`、`@foundation/query-workspace`、可信查询 HTTP 契约或 M7 设备渠道。历史证据保留当时双应用交付事实，不能被改写。正式 HTTPS 浏览器角色续证仍需要受信任部署证书，不能以 HTTP 或证书绕过替代。
