# WP-0012：查询反馈前端入口证据

## 范围

本证据绑定提交 `894324933`。本切片为 query-web 中已经实现的受控反馈 HTTP 接口提供最终用户交互；不新增诊断、草稿测试、自由文本、浏览器身份参数或浏览器持久化状态。

## 交互与安全边界

- 只有终态 `ANSWERED` 或 `REFUSED` 查询显示反馈区；前端不把连接中断或未知失败误作可评价结果。
- 页面只提供四项固定选择：`HELPFUL`、`INCORRECT`、`INSUFFICIENT`、`IRRELEVANT`。没有文本框，不提交问题、回答、引用、租户、成员、权限或令牌。
- `QueryClient` 只在当前页面内存令牌上调用 `POST /api/v1/query-runs/{queryRunId}/feedback`，JSON 仅含 `outcome` 与 `reason`。
- 反馈状态仅存在查询工作流内存中；新问题与退出登录都会清除。重复响应稳定显示“已经提交”，不把服务器冲突暴露为内部错误。
- 无认证响应沿用既有失效会话逻辑：清除内存令牌并回到会话失效状态。

## TDD 红绿记录

红灯：先在 `query-client.test.ts` 调用尚不存在的 `recordQueryFeedback`，执行：

```bash
npm --prefix frontend test -- apps/query-web/src/api/query-client.test.ts
```

结果：失败，`TypeError: client.recordQueryFeedback is not a function`。

绿灯：实现客户端方法后同一命令通过，验证 URL、`POST`、内存 `Authorization` 头和仅含两个受控枚举的 JSON。

红灯：再为工作流增加“提交中、已记录、下一次查询清除”的状态回归，执行：

```bash
npm --prefix frontend test -- apps/query-web/src/features/query/query-workflow.test.ts
```

结果：失败，状态机尚未处理反馈事件。实现状态机与页面反馈面板后通过。

## 验证

```bash
npm --prefix frontend test -- \
  apps/query-web/src/api/query-client.test.ts \
  apps/query-web/src/features/query/query-workflow.test.ts
npm --prefix frontend run build --workspace @foundation/query-web
```

结果：两份 Vitest 文件共 12 项通过；TypeScript 检查与 Vite 生产构建通过。

## 限制

这是前端自动回归和生产构建证据，不替代 M4 最终的真实角色浏览器验收。后者仍须在草稿测试与诊断纵向切片完成、可启动的全量 M4 环境中一并执行。
