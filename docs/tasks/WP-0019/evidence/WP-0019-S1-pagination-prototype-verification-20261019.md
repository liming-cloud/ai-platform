# WP-0019 S1：分页控件生产原型一致性验收

执行日期：2026-10-19（Asia/Shanghai）。本次只修复两页分页的既有样式缺陷，源码、构建、真实部署和本文声明的原型对照已通过。**不代表 S1、WP-0019 或 M6 完成；其余页面级门禁和外部 CI 仍未闭合。**

## 固定对象与边界

- 起始控制提交 `86ba8946dd79bafbf09d610dde65801a8e7bb205`；首轮修复 `3ed147851fa0210e7841abf98272f5a00f25a614`，最终源码／前端实际运行 `7f05664fa77fb6aef3e948279a5657ce6d3ec610`。章程、路线、STATE、工作包和直接 ADR 已复核，产品前沿保持 WP-0019／S1。
- 最终前端镜像 `sha256:b15305c253aa09f3a34b8b03d2ef602d442e5000e91e2026288f509c9d3b37b2`。从固定 Git archive 构建，构建输入不含 `pwd.txt`、`AccessKey.txt` 或 `deploy/.env`；临时构建源码在 finally 移除。
- 三后端实际运行仍为 `e0224fa9847fdaf297b3ad896e37ae7796e29a50`，镜像摘要见正式只读报告与[运行恢复证据](WP-0019-S1-cache-cleanup-and-runtime-resume-20261019.md)。每次只更新 console-web；其余六个容器标识及镜像前后相同，七服务健康、四入口 smoke 通过。没有重启 Docker 或操作业务数据。
- 原型唯一来源仍为 OpenDesign Cloud 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`、已接受 `cce686e4634ca3c523e4c8aeffd95752f1a04c3a`。[原项目目录入口](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=success&review=1)由 MCP 元数据重新确认。分页所在 `prototype.css` 的固定 Git、当前文件及 HTTP 内容逐字节相同，SHA-256 为 `96b48a55d4098d59cb8524bca484cf8def9e97a553cd02d9fd2653115d92863b`。未修改原型或新增设计例外。

## 失败证据与局部修复

真实旧部署的桌面正常／200% 及禁用悬停四组对照失败，164 项差异来自 Fluent 默认禁用色、620／600 字重、内边距、当前页边框、原生控件文字色／边框及放大高度。先在既有浏览器回归增加精确反例，原 37 项通过后，新断言真实失败。

只修改 `platform-model-connections.css` 的分页选择器：复用原型紧凑间距、暖色禁用态与透明度、当前页边框及可随文字撑高的原生控件。定向诊断纠正了测试将原生 select 的 `normal` 行高错误等同 input 像素行高的预期，并定位实际继承中性色问题；原始失败均保留，没有放宽字号、颜色或 1px 几何容差。固定 diff 评审将背景覆盖收窄为禁用当前页，保留既有 hover 行为。

首轮源码 43 项通过且部署后，完整 24 组仍发现 16 项差异，全部是手机当前页按钮 32px／44px 的同一问题。补入精确宽度断言先失败，再将硬编码 2rem 改为既有紧凑控件变量。最终完整 43 项和前端类型／lint／53 文件 310 项／构建再次通过，不用首轮绿灯代替最终部署。

## 最终部署结果

| 入口 | 实际执行与结论 |
| --- | --- |
| 原型对照 | 两页 × 390／1024／1440 × 正常／实际 200% × 普通／禁用悬停，共 24 组；六类分页控件的字体、颜色、边框、间距、透明度、宽高及禁用条件，差异 **0** |
| 数据前提 | 目录使用真实首屏；详情在生产和原型均通过正式搜索输入同一无匹配词取得零匹配分页。没有修改接口返回、清除数据或用 URL 伪造生产状态；不把真实数据条数差异列为视觉例外 |
| 正式浏览器入口 | `make acceptance-work-package WP=WP-0019 SLICE=platform-connection` 默认只读，十项通过；平台／租户／匿名边界、精确连接、分页刷新、三尺寸／200%、表单联动与取消清密、键盘、reduced-motion、退出拒绝 |
| 影响 | 业务写入 0、被阻止的意外写入 0、页面异常 0；正式前后读取均为连接 `2093675537095458817`、修订 4、旧协议、既有发布事实，未重复发现或发布 |
| 范围限制 | 正式入口自身仍保留 `prototypeParity=NOT_RUN`；24 组是独立的分页原型证据，不改写该字段，不代表整页七态或整包原型一致 |

人工查看了最终手机 200% 的两端原始截图，分页按钮、44px 当前页及放大原生控件与机器比较一致。全页截图中的后续导航与页面其他区域不包含在本次分页结论中，也不新增例外。没有新增数据库底表查询；正式接口前后事实不替代数据库证据。厂商调用、本轮账号创建和密码重置均为 0。

## 控制面与恢复

源码回归命令为 `node scripts/quality/platform-connection-pagination.browser.cjs`；使用已声明的 Playwright 1.62.1 与真实 Chrome，`NODE_PATH` 指向本机既有运行时。部署和正式验收均绑定上面的独立前后端 revision。架构 50 项及前后端守卫通过；未修改后端，不把较早 Maven 结果说成本次重跑。

原始失败、成功、截图和日志均保存在 0700 `/private/tmp/wp0019-s1-pagination-20261019-el9urv_k`，文件 0600；最终部署在 `deployed-7f05664f/`。十八项非秘密证据清单 `safe-evidence-digests.json` 的 SHA-256 为 `ae142164a4a410f5681685091394098445b714fe480c667661c2c6101fa012ba`，排除凭据及摘要、截图、完整网络配置和缓存。当前执行产生的可重建 Vite 缓存均已移除，失败截图和日志保留。

收口已运行 `make decisions-sync`、`make project-status`、`make project-verify`（81 项／30 个任务）、`make architecture-guard`（50 项及前后端守卫）、`make resume-context`，全部通过；恢复输出 11,285 字节，低于 12 KiB。详细结果在同目录 `closure-*.log`，晚于十八项核心清单生成；最终缓存检查覆盖本轮 9 个源码执行目录，残留 Vite 缓存 0。

最新独立确认的 [86ba8946 CI](https://github.com/liming-cloud/ai-platform/actions/runs/39194774428)步骤启动 0，因账户付款／spending limit 未运行；不能当作质量门禁通过。最终推送后另核对对应提交，不关闭或绕过 CI。

下一前沿为其余页面级七态／全景对照、真实未筛选空目录，以及[方舟真实合同](WP-0019-S1-ark-network-and-live-contract-20261019.md)的非空与正式生产流程缺口。网络与 AK／SK 认证已成功，但候选 0；既有连接无新发现配置，正式创建材料尚缺推理 Key。S2～S6 未开始，工作包与 M6 状态不提升。

Local Codex 显式加载当前工程助手 `wayfinder`、`tdd`、`code-review`。代码图谱定位 `PlatformModelSupplyWorkspace` 与 `PlatformConnectionPagination`，对分页组件执行一层影响分析；据此限定为两页共享的局部分页选择器，未改共享主题或公共契约。内联任务无 Runtime／StageRunResult，阶段校验不适用；固定 diff 与真实红绿、部署结果分别保存。
