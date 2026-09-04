# WP-0016 租户建立与配置易用性整改

## 范围与基线

- 基线 `c34a0ae72de316d7c37194a9275e024de7d1b3c1`，分支 `codex/trusted-rag-foundation-v1`；开始时工作区干净。
- 只修改 WP-0016 可丢弃原型、夹具、测试及控制面。保持 7/5/7，不修改生产前后端、API、数据库；人工拒绝继续有效，不推进 WP-0017。
- 用户要求缩短租户表单、开放配额编辑、修正文案和同排输入对齐。补充确认三项登录策略可编辑，隔离、权限校验和密码不回显始终开启；接受 ADR-0076。
- 状态：租户整改及最终浏览器复验已通过；初始实现提交 `0e7860c52`，布局、浏览器兼容修复和最终证据提交 `849c81ea6`。工作包仍进行中，不将自动检查等同于人工验收或整套原型完成。

## 设计与复现

- 建立租户分为租户信息、管理员和配额设置，单步呈现、前后保留、刷新恢复及取消清除；手机固定操作区避免往返滚动。
- 共用 `.field` 改为顶部对齐，帮助文字仍保留；助手创建改用“创建助手”“关联知识库”，不改变一个助手固定一个知识库的边界。
- 五项配额可在创建与租户详情保存，不能低于合成用量；事务式原型写入在存储失败时回滚。仅白名单非秘密字段进入草稿。
- 登录策略仅模拟配置保存；合成 8 小时 / 5 次 / 15 分钟不是生产默认值或已执行限流的证据。
- 红灯：`tenant-setup-red.tap` 0/5、`tenant-quota-state-red.tap` 0/8、`tenant-quotas-browser-red.tap` 0/3；新增安全配置及重复编码用例均先失败。初次 Chromium 沙箱启动失败不计作产品红灯。
- 首轮 UI 复核为 8/10，新增安全弹窗和重复编码即时拒绝仍需补齐；不作为最终通过结论。
- 后续已补齐安全弹窗与第一步重复编码拒绝、最终提交错误返回对应步骤；通过原生 fieldset 禁用暂不可用场景的整组配置控件。登录策略与配额共用弹窗绑定逻辑，保存后反馈并恢复触发按钮焦点。
- 租户“用量与配额”页改为只读已保存的资源配置和合成用量，删除可提权修改的展示入口；实际用量采集与服务端执行仍不属于本轮。

## 验证入口

以下产物路径相对 `docs/prototypes/wp-0016/`，命令从仓库根执行；浏览器使用已有 Playwright 1.62.1。不改写原始失败证据。

```bash
node --test docs/prototypes/wp-0016/scripts/tenant-quota-state.test.mjs docs/prototypes/wp-0016/scripts/tenant-security-state.test.mjs
NODE_PATH=/private/tmp/wp0016-playwright/node_modules node --test docs/prototypes/wp-0016/scripts/tenant-setup.browser.test.cjs
node docs/prototypes/wp-0016/scripts/verify-static.mjs
NODE_PATH=/private/tmp/wp0016-playwright/node_modules node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs
make project-status
make resume-context
make project-verify
git diff --check
```

| 检查 | 本轮结果 |
| --- | --- |
| Node 回归 | 49/49，`artifacts/tenant-remediation-node.tap`；包含全部既有 Node 用例及配额、安全、只读投影、暂不可用控件回归 |
| 静态与 URL | 4,036 断言、427 渲染、1,428 HTTP 场景，`artifacts/tenant-remediation-static.json`；已校验当前浏览器报告 |
| OpenDesign lint | P0/P1/P2 均 0，`artifacts/tenant-remediation-opendesign-lint.json` |
| 租户浏览器 | 10/10，`artifacts/tenant-remediation-browser.tap`；覆盖配额、登录策略、重复编码、存储失败和 390/1024/1440 |
| 问答与布局 | 问答 15/15、布局 13/13，见 `artifacts/tenant-remediation-query.tap` 与 `artifacts/tenant-remediation-layout.tap` |
| 全平台浏览器 | PASS；68 页、7 状态、3 视口，1,428/1,428 场景、9,662 项断言、85 张截图，浏览器异常与失败均为 0；`artifacts/tenant-remediation-final-browser-20260904/verification.json` |
| 项目治理 | `project-status`、`project-verify` 通过；14 项治理测试，恢复上下文 7,368 字节，低于 12 KiB |

Node 回归完整命令为 WP-0016 声明的五个既有 `.mjs` 测试加 `tenant-quota-state.test.mjs` 与 `tenant-security-state.test.mjs`。安全、配额与暂不可用用例均保留独立红绿日志。10 项浏览器脚本追加了配额步骤按钮可见性、无横向溢出和控制台错误捕获。

## OpenDesign 与工程能力

- 当前环境 Codex；使用已安装 engineering-assistant 插件，最小充分选择并完整加载 `prototype`（界面分支）、`tdd`、`decision-interview`、`code-review`，不启动 Runtime flow；未产生 StageRunResult / QualitySnapshot，结构校验不适用。
- 读取 Charter、STATE、WP-0016 及全部 7 个既有活动 ADR，新增 ADR-0076 后共 8 个。`lastVerifiedCommit` 是 HEAD 祖先，保留历史已验证基线。
- 代码图谱探索租户渲染、创建、功能绑定与共享字段，查看 `renderTenantCreate`、`renderTenantDetail`、`bindTenantFeatures`、`createTenant`；深度 2 影响：`renderTenantCreate` 4 个、`createPrototypeStore` 3 个、`renderField` 4 个原型符号。依据结果限定在原型状态与共享渲染，不扩散到生产服务。
- 复用 OpenDesign 0.21.1 同一项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`；通过 Local Codex 运行 `1ea2598d-4e99-4d78-9fd0-972529ac6cb7` 精修页面，项目直接指向仓库原型；运行终态 `succeeded`、退出码 0。生成运行只负责 UI，根任务负责状态、测试、控制面及用户追加确认后的安全配置整合；成功终态不代表浏览器或人工通过。
- OpenDesign 内部浏览器返回用户已保存的本地地址访问限制后暂停；用户随后明确授权独立 Chromium，只访问 `127.0.0.1:4176` 且不读取 Chrome 数据，最终复验按该边界完成。
- 补充图谱跟踪 `primaryAction`、`contentFor`、`render`、`decorateExistingPage`；`primaryAction` 深度 2 影响 5 个原型符号，促使只给用量页增加只读刷新分支，并单独验证暂不可用字段组，不修改其他管理流程。
- 补充图谱探索 `verify-and-capture.cjs` 的 `openScenario` 与 `validateManagementFlows`，确认审查器会遮挡管理流程后使用键盘收起；全矩阵随后暴露 Chromium HTML `pattern` 的 `/v` 兼容错误，修正字符组合表达式并让租户专项同时捕获 `console` 错误。两次红灯均保留失败截图，最终报告无浏览器异常。

## 六维复核

- 正确性：状态、静态、DOM、键盘、新安全弹窗和全平台有限矩阵均通过；账本未穷举及人工未确认，不能签署整套原型通过。
- 复杂度与冗余：不引入新框架/依赖；共享字段白名单、状态写入回滚和双配置弹窗行为，避免复制两套保存交互。
- 性能：不新增外部请求、定时器或模型调用；草稿只保存合成状态。大规模存储或真实并发不在原型承诺内。
- 架构：生产源码零变更；原型角色参数不成为生产安全边界，平台只读合成资源用量聚合。
- 测试质量：真实失败先于实现，存储错误和拒绝路径保留；修复审查器遮挡与 HTML pattern 兼容问题，未通过强制点击、放宽断言或忽略控制台错误消除差异。

## 余项

账本 98 项，18 项未实现或待决策，且尚未穷举全部控件。本轮已登记租户条目均有浏览器证据；下一切片继续账号创建与密码重置，再推进知识接入、索引、模型、助手发布和评测闭环。邮件邀请/自助激活待决策。页面可见、有限矩阵通过与完整业务可验收是不同结论，人工门禁不被自动化替代。
