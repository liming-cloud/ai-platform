# WP-0014 全 SPA 界面重构设计评审

## 评审范围

- `docs/design/m6-full-spa-ui-redesign.md`
- `frontend/apps/console-web/src/session/BrowserSessionGate.tsx`
- `frontend/apps/console-web/src/styles.css`
- `frontend/apps/console-web/src/session/BrowserSessionGate.ui.test.ts`
- ADR-0067、ADR-0068 与 `docs/design/m6-openai-style-ui-brief.md`

评审提交基线为 `12ad62d46`，当前工作区只包含 WP-0014 的登录原型、设计记录和浏览器截图。没有修改公共 API、数据库迁移、权限码、角色模板或生产配置。

## 结论

设计可以进入共享壳实现，没有未关闭的设计阻断项。该结论只批准 WP-0014 内的前端实现，不代表 M6 自动验收或人工接受。

## 已关闭问题

1. 初版登录原型的品牌陈述最大字号达到 72px，与 M6 禁止超大营销标题的约束冲突。实现已收敛为最大 52px，登录任务标题仍为 28px。
2. 恢复会话原先脱离登录框架，可能形成布局跳变。`LoginFrame` 现在同时承载恢复骨架和匿名登录。
3. 只改 CSS 会保留重复任务导航和账户身份泛化。实施契约现把变化限制在 tokens、`ProductShell` 和 `ConsoleApp` 局部 `TaskNavigation`。
4. 建立新组件库会与 Fluent UI 形成第二套系统。设计明确拒绝无调用方的通用 Card、Table 和 Form 包装器。

## 实施门禁

- `ProductShellProps` 不增加业务数据或授权参数。
- 账户标签只由现有 `displayRoles` 映射为用户语言。
- 任务 Tab 只改变现有 URL 路由，不持有客户端或业务数据。
- 任何需要新增公共 API、数据库投影或权限语义的发现都必须停止并重新路由。
- Computer Use 仍因 Mac 锁屏不可用；Chrome viewport override 未改变物理视口。1024×768、390×844、200% 缩放和减少动态效果继续是实现后的人工门禁，不能由 CSS 测试替代。

## 已执行证据

```bash
npm --prefix frontend test -- \
  apps/console-web/src/session/BrowserSessionGate.ui.test.ts \
  apps/console-web/src/spa-information-boundary.test.ts
npm --prefix frontend run typecheck
npm --prefix frontend run lint
npm --prefix frontend run build --workspace @foundation/console-web
git diff --check
```

- 视觉结构测试先出现 2 项预期红灯，实施后与信息边界共 9 项通过。
- typecheck、lint、生产构建与 `git diff --check` 通过。
- 1920×873 Chrome 设计原型对比截图为 `project/evidence/artifacts/WP-0014-login-prototype-1920.png`；该图早于标题字号收敛，只用于说明方向，不作为最终实现或人工验收证据。

## 边界

- 本评审不接受全 SPA 已完成。
- 本评审不关闭 1024×768、390×844、200% 缩放、键盘或减少动态效果证据。
- 本评审不进入 M7，`M6` 与 `WP-0014` 继续进行中。
