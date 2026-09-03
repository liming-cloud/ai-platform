# WP-0016 视觉整改候选

## 结论与提交

2026-09-03 人工拒绝已登记并提交为 `ca8168307`。本轮只形成新的可丢弃原型候选，不推翻“不通过”结论，不关闭 WP-0016，不推进 WP-0017。

候选提交：待绑定本次原型提交。浏览器权限未重新授予，真实视觉与交互验收未执行；本轮新截图为 0。

## 实际改动

- 参数候选从 `4 / 2 / 6` 上调为 `7 / 5 / 7`，保持 Fluent UI 语义、绿色强调和三类产品外壳。
- 全 68 页沿用唯一页面清单。目标路径、任务合同和原型范围移入审查工具；产品模式不展示工程合同条。
- 详情事实去重，8 个向导使用场景字段，5 个高风险页提供具体对象、影响、恢复和审计内容。
- 调整表格行高、数字与操作列对齐、主次布局、响应式规则和 160/200/240ms 反馈；reduced-motion 关闭非必要动态效果。此处描述已实施规则，不宣称实际渲染通过。
- 普通用户登录和会话页移除 HttpOnly、权限码、模型上下文 token 限额等工程解释，保留短操作提示和必要安全告知。
- 生成后复核发现并修正 6 页范围错配、拒绝态对象状态及弹层、专属页头固定成功状态和残留说明。
- 未修改生产前端、后端、API、数据库或页面清单的范围；M7/M8 仍仅为前置原型。

## 设计运行与可复现验证

Local Codex 在既有项目中执行，OpenDesign 0.21.1 与场景快照保持锁定：

- Project：`249f81f2-6f1a-46ff-988c-da3cdedb35e7`。
- Run：`226b959a-efd9-483c-9ec8-1aee7cf8e1c6`，结果 `succeeded`。
- 入口：`docs/prototypes/wp-0016/index.html`；运行完成后另做上述定向回归修正。

命令：

```bash
python3 -m http.server 4176 --bind 127.0.0.1 --directory docs/prototypes/wp-0016
node --test docs/prototypes/wp-0016/scripts/remediation-contract.test.mjs
node docs/prototypes/wp-0016/scripts/verify-static.mjs
node --check docs/prototypes/wp-0016/prototype.js
node --check docs/prototypes/wp-0016/full-platform.js
make project-status
make project-verify
git diff --check
```

官方 lint 使用锁定应用内绝对 CLI 路径，命令同前份候选证据，目标为本目录 `index.html`，阈值 `--fail-on p1 --json`；最终 `p0=0 / p1=0 / p2=0`。

结果与证据：

- `artifacts/visual-remediation-red.json`：4,007 条检查中 200 条失败，先于生成实现；`visual-remediation-scope-red.json` 保留 M8 说明侵入正文的补充红灯。
- `artifacts/remediation-contract-red.tap`：生成后 3 项回归全部按预期失败，范围列表包含 6 页；`remediation-user-copy-red.tap`：新增普通用户文案检查 1 项失败，前 3 项已绿。
- `artifacts/remediation-contract-green.tap`：最终 4 项回归通过。
- `artifacts/static-verification.json`：4,028 条断言通过；61 个共享渲染页的 427 个状态场景、68 页的 1,428 个 HTTP 入口成功。三种视口在此仅为配置枚举，不是浏览器布局证据。
- `artifacts/verification.json`：`BLOCKED`，真实 DOM、布局、焦点、九条交互链路与截图未执行。

所有 artifact 路径相对 `docs/prototypes/wp-0016/`。旧截图仅属于历史候选，不用于本次验收。

## 工程助手与质量判断

Codex 环境使用已安装 engineering-assistant 的 `prototype`（含 UI reference）和 `tdd`（含 security-seams reference）；控制面为 Charter、STATE、WP-0016 与六份引用 ADR。图谱探索 `contractStrip`、`renderInventoryPage`、共享向导/详情/风险渲染，影响分析分别为 7 和 3 个符号，促使同步更新渲染与验证合同。无 `StageRunResult` 或活动 Runtime quality node，不构造形式化阶段结果。

- 正确性：4 项补充红绿与 4,028 条静态断言；真实浏览器正确性仍未验证。
- 复杂度：保留现有数据驱动页面模式，不新增生产框架或服务。
- 冗余：移除正文合同条、重复事实和通用关联字段。
- 性能：动效限于短反馈并提供 reduced-motion；真实渲染性能尚待浏览器授权，不能判绿。
- 架构：变更只在可丢弃原型与工作包证据；范围归属由清单分组决定，不以页面名前缀猜测。
- 测试质量：实际保存失败原因与同一测试的绿灯，明确区分源文件检查、HTTP 和真实 DOM；没有冒充后端授权测试。

## 唯一下一步

获得本地浏览器访问授权后，执行 WP-0016 的真实矩阵与截图、处理发现的问题，再请求用户复验。人工结论仍为“不通过，整改待复验”。
