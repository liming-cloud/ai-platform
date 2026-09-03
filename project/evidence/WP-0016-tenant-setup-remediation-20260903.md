# WP-0016 租户建立与配置易用性整改

## 范围与基线

- 基线 `c34a0ae72de316d7c37194a9275e024de7d1b3c1`，分支 `codex/trusted-rag-foundation-v1`；开始时工作区干净。
- 只修改 WP-0016 可丢弃原型、夹具、测试及控制面。保持 7/5/7，不修改生产前后端、API、数据库；人工拒绝继续有效，不推进 WP-0017。
- 用户要求缩短租户表单、开放配额编辑、修正文案和同排输入对齐。补充确认三项登录策略可编辑，隔离、权限校验和密码不回显始终开启；接受 ADR-0076。
- 状态：已实现待最终浏览器复验；本轮实现提交在提交后绑定。工作包仍进行中，不将部分自动检查等同于完成。

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
| 静态与 URL | 4,035 断言、427 渲染、1,428 HTTP 场景，`artifacts/tenant-remediation-static.json`；相比历史少 1 项是等待授权时不对历史浏览器报告作当前通过校验，不是删除断言 |
| OpenDesign lint | P0/P1/P2 均 0，`artifacts/tenant-remediation-opendesign-lint.json` |
| 首轮浏览器 | 8/10，`artifacts/tenant-ui-first-review.tap`；已目视核对 390/1024/1440 首步与桌面配额弹窗，最终代码未复验 |
| 最终浏览器、全矩阵、问答与布局回归 | 未运行；获知已保存访问限制后暂停，等待本次授权；历史结果不沿用为新代码通过 |
| 项目治理 | `project-status`、`project-verify` 通过；14 项治理测试，恢复上下文 7,609 字节，低于 12 KiB |

Node 回归完整命令为 WP-0016 声明的五个既有 `.mjs` 测试加 `tenant-quota-state.test.mjs` 与 `tenant-security-state.test.mjs`。安全、配额与暂不可用用例均保留独立红绿日志。10 项浏览器脚本追加了配额步骤按钮可见性与无横向溢出断言，待授权后连同既有问答、布局和全矩阵一并执行。

## OpenDesign 与工程能力

- 当前环境 Codex；使用已安装 engineering-assistant 插件，最小充分选择并完整加载 `prototype`（界面分支）、`tdd`、`decision-interview`、`code-review`，不启动 Runtime flow；未产生 StageRunResult / QualitySnapshot，结构校验不适用。
- 读取 Charter、STATE、WP-0016 及全部 7 个既有活动 ADR，新增 ADR-0076 后共 8 个。`lastVerifiedCommit` 是 HEAD 祖先，保留历史已验证基线。
- 代码图谱探索租户渲染、创建、功能绑定与共享字段，查看 `renderTenantCreate`、`renderTenantDetail`、`bindTenantFeatures`、`createTenant`；深度 2 影响：`renderTenantCreate` 4 个、`createPrototypeStore` 3 个、`renderField` 4 个原型符号。依据结果限定在原型状态与共享渲染，不扩散到生产服务。
- 复用 OpenDesign 0.21.1 同一项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`；通过 Local Codex 运行 `1ea2598d-4e99-4d78-9fd0-972529ac6cb7` 精修页面，项目直接指向仓库原型；运行终态 `succeeded`、退出码 0。生成运行只负责 UI，根任务负责状态、测试、控制面及用户追加确认后的安全配置整合；成功终态不代表浏览器或人工通过。
- OpenDesign 内部浏览器随后返回用户已保存的本地地址访问限制；获知限制后停止浏览器验证，向用户单独申请本次隔离 Chromium 复验授权。不通过其他入口绕过。
- 补充图谱跟踪 `primaryAction`、`contentFor`、`render`、`decorateExistingPage`；`primaryAction` 深度 2 影响 5 个原型符号，促使只给用量页增加只读刷新分支，并单独验证暂不可用字段组，不修改其他管理流程。

## 六维复核

- 正确性：状态与静态回归通过；最终 DOM、键盘及新安全弹窗未复验，不能签署全部通过。
- 复杂度与冗余：不引入新框架/依赖；共享字段白名单、状态写入回滚和双配置弹窗行为，避免复制两套保存交互。
- 性能：不新增外部请求、定时器或模型调用；草稿只保存合成状态。大规模存储或真实并发不在原型承诺内。
- 架构：生产源码零变更；原型角色参数不成为生产安全边界，平台只读合成资源用量聚合。
- 测试质量：真实失败先于实现，存储错误和拒绝路径保留；最终浏览器仍待授权，未通过放宽断言或隐藏说明来消除差异。

## 余项

账本 98 项，18 项未实现或待决策，另有本轮已实现但待浏览器验证项。先复验本轮，再继续账号创建与密码重置、知识接入、索引、模型、助手发布和评测闭环。邮件邀请/自助激活待决策。页面可见、有限矩阵通过与完整业务可验收是不同结论，人工门禁不被自动化替代。
