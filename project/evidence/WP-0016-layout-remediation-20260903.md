# WP-0016 统一布局与审查器整改证据

## 范围和结论

- 基线：`a42a359701d385dcfe07145374f1d75f7dd0c2a3`，分支 `codex/trusted-rag-foundation-v1`，开始时工作区干净。实现及测试证据提交：`5048b659c`（`bug：修复WP-0016窄屏布局与审查器可达性`）。
- 用户“同意”按同意继续整改处理，不视为整套人工验收。只修复可丢弃原型 B03/B04 及同源共享布局；不修改生产前后端、API、数据库或权限状态模型。设计参数保持 7/5/7。
- B03：平台角色改用“平台管理员”中文名称；详情标题、标签允许换行，长标识不撑开栏位；手机详情、向导、结果、时间线补齐单列断点。
- B04：审查器高度不超出动态视口；顶部原生 summary 固定可达，内部可滚动，长选项不撑开控件。重置后能点击或用 Enter/Space 收起，焦点保留，产品控件可继续使用。
- 两个缺陷在合成原型范围关闭。全平台有限自动合同通过，不代表全部业务操作、所有可访问性或人工认可；97 个登记控件仍有 20 个未闭环，且尚未穷举全部控件。WP-0016 继续进行中，WP-0017 门禁不变。

## 复现与修复

1. `layout-remediation-red.tap`：新增 9 项真实浏览器测试，0/9 通过。390px 角色页横向溢出，角色标题暴露内部码；1280×720 审查器顶部为负坐标，关闭入口不可达。
2. 第一轮最小修复后 9/9 通过，但截图显示左栏仍仅 30px、审查器内容宽度 440px 超过 302px 容器。没有将文档无溢出当作视觉通过。
3. 补充自身栏位、手机单列和控件无裁切断言；`layout-remediation-containment-red.tap` 为 2/13 通过，11 项因预期布局原因失败。根因为单列规则只覆盖 681–1040px，遗漏手机断点，且原生 select 的最小内容宽度未受限。
4. 修复共享断点、grid 轨道及 select 宽度后，同组 13/13 通过；截图等待有限动画结束。没有增加文档裁切规则、force click、隐藏审查器或放宽安全状态测试。

## 可复现命令

已有本机 Playwright 1.62.1 / Chromium 与 4176 服务直接复用，没有重新安装。下列命令从仓库根执行，日志与截图均为合成数据。

```bash
NODE_PATH=/private/tmp/wp0016-playwright/node_modules node --test docs/prototypes/wp-0016/scripts/layout-regression.test.cjs
NODE_PATH=/private/tmp/wp0016-playwright/node_modules QUERY_ARTIFACT_DIR=docs/prototypes/wp-0016/artifacts/layout-remediation-query node --test docs/prototypes/wp-0016/scripts/query-journey.test.cjs docs/prototypes/wp-0016/scripts/query-resilience.test.cjs
node --test docs/prototypes/wp-0016/scripts/remediation-contract.test.mjs docs/prototypes/wp-0016/scripts/business-closure.test.mjs docs/prototypes/wp-0016/scripts/reviewer-regression.test.mjs docs/prototypes/wp-0016/scripts/query-state.test.mjs docs/prototypes/wp-0016/scripts/query-boundaries.test.mjs
NODE_PATH=/private/tmp/wp0016-playwright/node_modules PROTOTYPE_ARTIFACT_DIR=docs/prototypes/wp-0016/artifacts/layout-remediation-final-browser-20260903 node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs
node docs/prototypes/wp-0016/scripts/verify-static.mjs
make project-status
make resume-context
make project-verify
git diff --check
```

| 验证 | 结果与证据 |
| --- | --- |
| 布局专项 | 13/13；`artifacts/layout-remediation-final.tap`；390/1024/1440 角色详情、四类手机工作区、390×600 至 844×390 低高度/横屏、键盘及 reduced-motion |
| OpenDesign 直接预览 | 同组 13/13；`artifacts/layout-remediation-opendesign.tap`，不是只验证独立 4176 服务 |
| 普通用户浏览器 | 15/15；`artifacts/layout-remediation-query.tap` |
| Node 回归 | 36/36；`artifacts/layout-remediation-node.tap` |
| 全平台真实浏览器 | 68 页 × 7 状态 × 3 视口，1,428 场景、9,661 断言、14/14 阶段、85 张截图；无运行异常；`artifacts/layout-remediation-final-browser-20260903/verification.json` |
| 静态合同 | 4,036 断言、427 共享渲染与 1,428 HTTP 场景通过；`artifacts/layout-remediation-static.json` |
| OpenDesign lint | P0/P1/P2 均为 0；`artifacts/layout-remediation-opendesign-lint.json` |
| 项目治理 | `project-status` 与 `project-verify` 通过；14 项治理测试、20 个工作包；`resume-context` 为 7,043 字节，低于 12 KiB |

上表 `artifacts/` 均相对 `docs/prototypes/wp-0016/`。已目视复核三档角色详情、1280×720 与 390×600 审查器；代表图在 `layout-remediation-independent/` 和 `layout-remediation-opendesign/`。中间全平台报告不替代最终报告，其重复截图移至 `/private/tmp/wp0016-layout-intermediate.hMBFlj/screenshots` 保留，可恢复但不进入本轮提交。TAP 日志只规范化行尾空白，断言与结果没有改写。

## OpenDesign 与工程能力证据

- 复用已安装签名稳定版 0.21.1 和项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，项目目录直接指向仓库原型。此轮为现有文件缺陷修复，未另建项目或委派生成任务。原 MCP 连接返回 Transport closed 后，通过官方安装包内绝对 CLI 的 MCP stdio 读取项目，未调用 `/usr/bin/od`。
- 直接预览测试使用 `PROTOTYPE_BASE_URL=http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html` 和 `LAYOUT_ARTIFACT_DIR=docs/prototypes/wp-0016/artifacts/layout-remediation-opendesign` 运行同一布局脚本。URL 仅在当前本机 daemon 会话有效。
- OpenDesign 返回文件与磁盘 SHA256 一致：`prototype.css` 为 `fd187889326360c8e77f903d8e2a09e8d57967385bd221201a7c9d77ab1f4565`；`page-fixtures.json` 为 `730e99efdef5fb97ba44fb3b859bf9f2dad40bcfea0dd18974aebe7653793f8d`。
- lint 使用 `/Applications/Open Design.app/Contents/Frameworks/Open Design Helper.app/Contents/MacOS/Open Design Helper` 启动 `/Applications/Open Design.app/Contents/Resources/app/prebundled/daemon/daemon-cli.mjs`，沿用稳定版 `OD_DATA_DIR`/`OD_SIDECAR_IPC_PATH` 和 `ELECTRON_RUN_AS_NODE=1`，参数为 `lint <原型绝对路径>/index.html --fail-on p1 --json`。
- 当前运行环境 Codex；能力来自已安装 `engineering-assistant` 2.0.0-rc.1 本地插件。最小充分选择并完整加载 `engineering-assistant:prototype`（含界面分支）、`engineering-assistant:tdd`、`engineering-assistant:code-review`。控制面读取 Charter、STATE、WP-0016 及其全部 7 个活动 ADR；`lastVerifiedCommit` 是 HEAD 祖先，保留该历史基线，不以局部修复冒充整个工作包通过。
- 代码图谱先探索 `full-platform.js`、`renderDetail`、`renderRows`、`validateReviewerReachability` 并查看 `initializeReviewer`；`renderDetail` 深度 2 影响为 4 个原型符号。依据图谱将修复限定在共享样式与合成夹具，不改渲染、查询和状态模型。样式断点与布局边界再由精确 CSS 和浏览器测量补充验证。
- 未启动持久化 Runtime flow，未产生 StageRunResult 或 QualitySnapshot；相应阶段结构校验不适用。

## 六维评审及后续

- 正确性：两轮真实红灯与 13/13 绿灯、OpenDesign 同源回归证明 B03/B04 关闭。
- 复杂度：只修改共享 CSS 与一个展示名称，保留原生 details/summary，不引入额外弹层状态或框架。
- 冗余：复用现有四类工作区规则和底部偏移变量，没有复制页面、导航或业务状态。
- 性能：未新增 JS 运行回调、网络请求和依赖；截图等待只存在于测试，既有 15 项查询回归通过。
- 架构：生产目录零修改，领域/权限/持久化零变更，原型保持可丢弃，不直接吸收为生产实现。
- 测试质量：浏览器正常点击、键盘与几何测量并用，单列和控件自身边界补足原矩阵盲点；全平台 PASS 只覆盖报告明确声明的有限合同，不将导航等同于业务闭环。

下一切片在 WP-0016 内闭环账号创建、初始密码/重置与租户管理员交付；不预先承诺邮件邀请、自助激活或可配置安全政策，待决策项必须先确认。
