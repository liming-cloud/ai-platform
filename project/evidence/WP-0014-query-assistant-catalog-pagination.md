# WP-0014 统一问答助手目录分页

## 结果

`/ask` 的已发布助手选择不再固定读取第一页。统一控制台路由拥有 `assistantPage`，刷新、深链接和前进后退均会恢复该页；`QueryWorkspace` 只请求当前服务器页，并以总数、前后页事实提供明确翻页。跨页时仅替换当前页助手摘要与选择，不把未读取助手、会话、令牌或完整目录保留在浏览器。

查询运行事件仍按既有事件序号协议恢复，未伪装为普通页码分页；既有 `browser-session` HttpOnly cookie 和服务端助手查询授权均未改变。

## 红绿与验证

- 红灯：`cd frontend && npm test -- --run apps/console-web/src/session/console-route.test.ts packages/query-workspace/src/query-client.test.ts` 实际 2 项失败：`/ask` 路由缺少 `assistantPage`，且把页码 `3` 当成 `AbortSignal`，无法生成第三页 HTTP 请求。
- 绿灯：同一命令 21 项通过；路由往返固定为 `/ask?assistantPage=3`，查询客户端以同源 cookie 请求 `/query-assistants?pageNumber=3&pageSize=20`。
- `cd frontend && npm test && npm run build`：81 项前端测试和唯一控制台生产构建通过。
- `cd frontend && npm run typecheck && npm run lint`：通过；未出现 Effect 内同步状态更新或前端产品门禁豁免。
- `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml config --quiet && docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml build console-web`：Compose 解析和唯一控制台镜像构建通过。
- `make project-verify && make validate`：项目治理、前后端门禁和全模块回归通过。Spring/MyBatis 输出为既有测试夹具预期警告，不影响命令退出状态。

## 设计质量判断

- 正确性：路由测试和同源 HTTP 客户端测试证明页码恢复与服务器请求；后端既有 `QueryAssistantController` 保持 token 派生上下文、`PageQuery` 和授权边界。
- 复杂度：页码只在现有 `ConsoleRoute` 与 `QueryWorkspace` 两个真实所有者间流动，没有新增导航、会话或分页层。
- 冗余：复用现有 `QueryAssistantPage` 的总数和前后页事实，未复制后端分页契约。
- 性能：每次最多读取已有每页 20 个助手摘要，不再把目录总量载入浏览器。
- 架构：代码图谱的 `PageResult` 影响分析定位了 `QueryClient` 这一遗漏调用点；查询事件序号保持原流式例外语义。
- 测试质量：红灯来自公共路由和 HTTP seam，绿灯覆盖 URL、请求参数、类型、lint、生产构建和全量门禁。

## 浏览器边界

此前记录的正式浏览器 HTTPS 验收仍受未受信任本地证书限制。本切片不安装 System keychain CA、不降级 HTTP、也不忽略证书错误；正式角色浏览器任务待受信任部署环境续证。
