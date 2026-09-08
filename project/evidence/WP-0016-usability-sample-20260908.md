# WP-0016：整体易用性与 Claude 样板

## 结论与提交

阶段一全页去向地图、阶段二公共组件与“准备知识 → 发布助手 → 开放使用”连续样板已实现并通过本轮机器验证，等待样板方向人工确认。人工拒绝仍有效；全量送验为 false，不进入阶段三或 WP-0017，不修改生产代码。

- 实现提交：35110ca815fc3eed57abd3d5e2a8b83bc41b9ca7。控制面先行提交：e8f1dacf3。调查基线：6d4249a73585744453b528b55b742fbca0ab542a；起点最后验证提交 0ff24482316c58288f8dd6f8a7e19872fd097c87 是其祖先。
- 原 73 页去向逐项登记在 page-inventory.json；新增 4 个成员页与助手概览，共 78 入口。其余页面的回归不等于全量迁移完成。
- OpenDesign 原项目 249f81f2-6f1a-46ff-988c-da3cdedb35e7 保存 107 个原型、代码、设计文件，逐文件 raw 响应 SHA-256 一致；保存清单见 docs/prototypes/wp-0016/artifacts/opendesign-save-20260908.json。
- 截图仅在 /private/tmp/wp0016-usability-review.JNxt5N；未添加项目截图。保留用户已暂存的五项 fixture 改名，不混入提交；未恢复或重置用户演示账户。

## 红绿证据与结构修正

原 OpenDesign raw 预览、独立 Chromium 1440×900 的四项最小复现均先失败，修正后通过：成员目录混入授权长表单、文档与知识库并列一级导航、文档可见英文阶段、文档页承载全库重建。临时探针为 /private/tmp/wp0016-usability-review.JNxt5N/baseline.cjs；用合成登录建立身份，不注入生产权限。

按对象工作区统一修正：知识库下设文档、处理任务、索引；成员账号、对话功能、可用助手各自独立，开户为三步向导；助手概览承接配置、测试、质量和发布。弹窗容器直接承担 24px 内边距，状态使用中文，模型与策略内部标识进入技术详情。

新增回归先证明并修复：压力夹具容量膨胀、候选重绘丢焦点、手机首步隐藏失效、手动减少动效遗漏 CSS、禁用态可编辑、浏览器返回使用原生确认框。最终全量复跑中，返回文档列表的计数用例还复现一次渲染等待竞态，明确等待目标 main 后 157/157 通过，没有放宽行数断言。

保存沿用既有授权与原子状态写入；两个权限页只提交各自白名单，非秘密草稿按租户、操作者与登录代次隔离。刷新、取消、浏览器返回、撤权和存储失败仍受原边界约束。样板不等于真实后端、解析器或模型服务。

## 可复现验证

在仓库根执行，下列浏览器命令统一设置：

    export PROTOTYPE_BASE_URL='http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html'
    export NODE_PATH=/private/tmp/wp0016-playwright/node_modules
    export PROTOTYPE_ARTIFACT_DIR=/private/tmp/wp0016-usability-review.JNxt5N/final-matrix
    export PROTOTYPE_CONTROL_REPORT=/private/tmp/wp0016-usability-review.JNxt5N/control-inventory-verified.json
    export OPENDESIGN_SAVE_REPORT=/private/tmp/wp0016-usability-review.JNxt5N/opendesign-save.json

| 命令 | 结果 |
| --- | --- |
| node --test docs/prototypes/wp-0016/scripts/*.test.mjs | 161/161 通过 |
| node --test --test-concurrency=2 docs/prototypes/wp-0016/scripts/*.test.cjs | 157/157 通过 |
| node docs/prototypes/wp-0016/scripts/verify-static.mjs | 78 页、3806 断言通过；HTTP 可达不代替真实场景 |
| node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs | 78×7×3=1638 场景、11035 断言；无失败或运行异常，95 张临时图 |
| node docs/prototypes/wp-0016/scripts/enumerate-controls.cjs | 66 个范围内 M6 入口×7 态，7779 控件，未接线与异常均为零 |
| node docs/prototypes/wp-0016/scripts/save-opendesign.cjs | 107 文件保存与 SHA-256 校验通过 |
| OpenDesign lint（下列绝对路径命令） | P0/P1/P2 均为零 |
| make project-status、make project-verify、make resume-context、git diff --check | 治理通过，14 个测试，恢复上下文不超过 12 KiB |

    ELECTRON_RUN_AS_NODE=1 OD_SIDECAR_IPC_PATH=/tmp/open-design/ipc/release-stable/daemon.sock '/Applications/Open Design.app/Contents/Frameworks/Open Design Helper.app/Contents/MacOS/Open Design Helper' '/Applications/Open Design.app/Contents/Resources/app/prebundled/daemon/daemon-cli.mjs' lint '/Volumes/slm-ssd/projects/ai-project/ai-platform/docs/prototypes/wp-0016/index.html' --fail-on p1 --json

完整机器摘要：docs/prototypes/wp-0016/artifacts/stage-two-validation-summary.json。控件接线枚举不等于穷举每个输入组合；账号、知识、助手、成员权限和问答行为由专项测试验证。

连续任务在 390×844、1024×900、1440×900、1366×768、720×450 五种布局从新建库走到本人问答完成并刷新恢复。720×450 是 200% 等效 CSS 重排，不声称实际设置浏览器缩放。压力专项检查 1000 文档、1000 普通成员、200 助手候选、100 版本、10000 记录下默认 DOM 20 行、搜索、跳页、序号窗口与焦点；夹具仅注入独立测试上下文。

目视抽查知识目录、文档详情、成员独立保存、发布弹窗、手机向导。动效测试覆盖快速收敛、取消恢复焦点、系统和手动减少动态效果。没有独立新用户可用性实验，不把截图、自动布局或合成问答视为人工认可。

## 设计依据、影响分析与工具边界

- 本机 OpenDesign 0.21.1 的 Claude (Anthropic) 包已完整读取 USAGE.md、DESIGN.md、tokens.css、组件清单；它是整理后的参考设计，不声称为 Anthropic 官方完整企业组件规范。7/5/7 保持，集中扩展中文字体、密度、对比度和 150/200/240ms 动效，不下载专有字体、不替换生产 Fluent UI React v9。
- 在原 OpenDesign 项目使用 Local Codex 与界面精修、动效配方；两次生成 run 9f91945a-f036-4ab6-acf2-bdbbd2e4582f、f40821b3-0e11-4a26-83a8-f44fc4ce5f37 均 succeeded。项目元数据 designSystemId 为 null，任务显式使用本机 Claude 包并验证实际 tokens；不谎称元数据已绑定。
- 代码图谱探索 renderSourceDetail、renderAssistantRelease、adminShell、renderConsoleNavigation、renderMemberFeatureEditor、workspaceJourney、memberWorkspace 及公共分页。renderConsoleNavigation 影响 50 个原型符号，bindDirectoryPagination 影响 3 个符号，因此复用原分页标记并回归公共外壳；未扩展到生产实现。图谱未收录的新测试/压力文件按已知路径精确检查。
- 沙箱因已配置可写根含符号链接而不能启动；使用已获准的绝对仓库路径执行，不修改全局配置。所有本地编辑仍经 apply_patch；浏览器采用独立 Chromium，不读取用户 Chrome profile。

## 工程助手与下一门禁

当前运行环境 Codex，能力来源为已安装 engineering-assistant 插件。实际路由 prototype / inline / proceed；显式加载 engineering-assistant:engineering-assistant、engineering-assistant:prototype（含 UI、授权、前沿参考），收口加载 engineering-assistant:code-review 与安全边界参考。使用 Charter、STATE、WP-0016 的 12 个活动 ADR、原型设计及固定 diff。未产生持久 flow、StageRunResult 或 QualitySnapshot，阶段结果校验不适用。

营销页技能明确不适用于此类表格和多步骤产品，未采用其规则。下一步只等待用户按 OpenDesign 内 ACCEPTANCE.md 确认样板方向，然后按同一地图迁移剩余页面；最终全量人工确认仍是独立门禁。
