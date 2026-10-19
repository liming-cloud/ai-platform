# WP-0019 S1-C01：候选名称分离与部署验收

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0019 / S1-C01 |
| 日期 | 2026-10-19，Asia/Shanghai |
| 输入基线 | 869354bb863ca01f2668ac8e9ea3add99c2ec471，main，工作区干净 |
| 验证提交 | 7b465a2dda50eacc455bfb82f83e6606865b4c62 |
| 后端运行提交 | 09fafd33d2b6bf70f663c90bf51e4233ffe2380c |
| 验证范围 | 名称采集、冻结、公开读取及候选区域；S1-C01 闭合，不代表 S1 整片、WP-0019 或 M6 完成 |
| 原型 | OpenDesign Cloud 原项目 249f81f2-6f1a-46ff-988c-da3cdedb35e7；已接受 cce686e4634ca3c523e4c8aeffd95752f1a04c3a |

## 修复与边界

依据 ADR-0070，并继承 ADR-0082、0095、0099：供应商 `modelCode` 继续只作受控选择、查询和调用值；独立 `displayName` 从扩展 facade、领域候选、持久化到公共投影保留，页面不再直接显示编码。方舟两个已接受来源使用各自的 `Name` 元数据，不猜测能力；缺失名称不由编码补齐。[官方模型端点字段](https://docs.volcengine.com/docs/ark/inner-describe-model-endpoints-api?lang=zh)分别声明 `Name` 与 `ModelId`。

迁移仅新增可空 `display_name` 列及合法性约束，没有回填、覆盖或重新发现历史对象。历史名称缺失公开表达为“名称暂未提供”；缺失／空白的公共名称响应失败关闭。确认回读保留名称，同码候选仍按编码去重，原 CAS、短事务、网络在事务外和未知分类边界保留。四条触及的复杂 SQL 移入既有 Mapper XML 并精确登记；Java 内联 SQL 文件基线 14→13，手工分页仍为 4，没有放宽守卫。

生产观察同时复现了手机非空行标签缺失、标签颜色偏差，以及同页长连接名在 200% 下撑宽 82px。分别先补反例，再恢复原型已有字段标签、muted 颜色及事实标题收缩／换行规则；未修改全局色板、原型、权限或后续页面。

## 红绿与源码验证

原始失败和中间执行故障均保留于本轮证据根目录，没有删除步骤或放宽断言。

| 验证 | 原始反馈与最终结果 |
| --- | --- |
| HTTP 名称合同 | 原始 3 项中缺少 `$.items[0].displayName` 真实失败；修复后相关 HTTP 4 项通过 |
| 隔离 Chrome 名称 | 独立名称／不透明编码反例先显示编码而失败，修复后通过 |
| 手机标签、颜色及长名称 | 各自反例先失败；局部修复后直接用例通过 |
| 公共读取名称拒绝 | undefined、null、空串、空白四反例先失败，最终读取测试 35 项通过 |
| 受影响后端 | 定向 54 项通过；完整 `./mvnw -q clean test`：349 套件、1,172 项，失败／错误／跳过均 0 |
| 最终前端 | `make frontend-test`：类型、lint、53 文件／314 项及构建通过 |
| 最终隔离源码 Chrome | `node --test scripts/quality/platform-connection-partial.browser.cjs`：60 项通过 |

完整后端绑定 09fafd33；后续两次前端提交未改变后端构建输入。早期源码 Chrome 58 项和前端 310 项仅证明当时版本；最后一次完整源码 Chrome 曾在编辑期间出现执行上下文重载，原日志保留，停止编辑后固定源码重跑 60 项通过。中间测试端口误指向原型 HTTP、迁移反例误比较 JDBC Array 对象身份、SQL 未登记和 Javadoc/Werror 反馈均被定向修正，没有关闭检查或扩大容差。

## 最新部署与区域对照

固定 Git archive 构建排除 `pwd.txt`、`AccessKey.txt` 和 `.env`，构建上下文在 finally 清理。三后端正常 Compose 更新后，再部署最终前端；PostgreSQL、Redis、MinIO 容器、镜像和卷保留。七服务 healthy、四入口 smoke 通过。

| 服务 | 实际 OCI revision | 镜像摘要 |
| --- | --- | --- |
| console-web | 7b465a2dda50eacc455bfb82f83e6606865b4c62 | sha256:4b2aecb833e34740ee5b24912b1c642bf65b9c1e766e35eaffc2ad57f6d4c336 |
| control-plane | 09fafd33d2b6bf70f663c90bf51e4233ffe2380c | sha256:6f139c9e204b6e47dad0364c107edea16be4849281a9bc78bd9edd5af2df88d3 |
| query-runtime | 09fafd33d2b6bf70f663c90bf51e4233ffe2380c | sha256:fb288fc4f28a7de713db70d50047dbe577ff32bdae0fdb2f4d27df2965227727 |
| ingestion-worker | 09fafd33d2b6bf70f663c90bf51e4233ffe2380c | sha256:c596ce52f7660279f4c40c2c53d9cc3353a57cda56a8bea8d7850a9e8ffbe509 |

2026-10-19 19:39:09–19:39:25，最新部署对原项目[连接详情](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=success&review=1)完成 **12 组**候选区域对照：390／1024／1440 × 普通／200% × 真实读取／受控独立名称。字体、颜色、内边距、字段伪元素、两列结构、溢出与裁切严格检查；候选正文实测 15→30px，两边相同。最终差异、溢出、裁切、页面异常、被阻断写入和模型业务写入均 **0**，键盘及 reduced-motion 通过。人工查看手机普通／放大对照、受控长名称及 1024／1440 截图，未见候选内容重叠。

真实读取的两个历史候选展示缺失说明，编码不可见；受控组只替换当前浏览器候选 GET 成功响应的名称及编码，其他字段保留，证明实际部署组件消费独立名称并处理长名称。**受控响应不是新厂商发现或生产数据**。来源 `Name` 的采集、冻结和回读由扩展协议反例、真实隔离 PostgreSQL 与 HTTP 测试证明；本轮没有重新调用厂商、发现、确认或发布。

原始部署对照 `candidate-region-r3/candidate-region.json` 的 10 项差异保持 FAILED；8 项为两字段／两倍率／真实及受控的手机标签颜色，2 项为长连接名引起的页面溢出。最终独立目录 `candidate-region-final/` 为 PASSED／0 差异。两次准备中断没有运行完整矩阵；其中原型登录辅助函数默认转向测试端口，设置本轮原项目基址后恢复，未修改或重启 OpenDesign Cloud。原型受控资源自已接受提交以来无差异。

这里只验收受影响区域，不重复先前 84 组页面七态、66 组表单、真实创建／发现／发布或空库验收。真实名称、数量和待确认分类与原型模拟数据分开，未新增视觉例外；S2 平台部署及 M8 入口仍按原责任切片交付。

## 只读事实与剩余阻断

部署前后及最终浏览器后均用 `REPEATABLE READ READ ONLY` 独立比较：连接 3、凭据行 5、发现 13、历史候选 787、发布 2，平台部署／目录／租户授权均 0；三个精确对象修订、状态、用途配置和发现／发布计数相同。787 条历史名称仍为空，当前连接 `2112058351855599618` 修订 2 的两个候选仍未知类型、待管理员确认；没有读取秘密列、真实编码或名称。只增加结构，不改既有业务行；登录／退出复用普通 0600、Git 忽略的 `pwd.txt`，不要求重新提供凭据。

**S1-C01 子任务已闭合；当前剩余阻断为外部 CI。** 推送前复核 [869354bb 的 Actions](https://github.com/liming-cloud/ai-platform/actions/runs/39237455615)：唯一作业步骤数 0，因付款失败或 spending limit 未启动；不是产品测试失败，也不是自动审批容量拒绝。最终提交的运行状态单独核对，不将本地检查说成云端通过。账号所有者按[既有账单步骤](WP-0019-S1-page-states-and-ark-diagnostics-20261019.md#ci-账单阻断与人工处理步骤)处理 Payment information、Actions 预算；仍异常联系 GitHub Support，解锁后运行最终提交 CI。没有读取私人账单或修改额度。

五项操作的原始版本、授权及数据证据沿用[交付复核](WP-0019-S1-page-states-and-ark-diagnostics-20261019.md#五项操作证据链复核)，仅在唯一实现矩阵补本次名称修复依据。S1 整片尚未完成，WP-0019、M6 保持进行中，不进入 S2。

## 恢复与工程审查

Charter、ROADMAP、STATE、任务、十二条直接 ADR 及输入祖先关系已复核。Local Codex 显式加载当前工程助手 `engineering-assistant:diagnosing-bugs`、`engineering-assistant:tdd`、`engineering-assistant:code-review` 和安全 seam／证据审计参考，使用本包控制面内联执行；来源为当前安装 `2.2.0-rc.1+codex.local-20260929-051321/skills/`，未使用其他任务 Runtime，无 StageRunResult，阶段结果校验不适用。

代码图谱探索候选发现、Mapper／公共 View、详情组件及读取投影；`PlatformProviderModelCandidateView`、`domainPlatformDiscovery`、`readPlatformConnectionDetail` 影响分析分别为 21、23、6 个符号。据此沿既有 facade／领域／存储／投影修复，不新建抽象模块、不改分类或后续契约；CSS 与未命中的精确资源定向补读。固定 diff 内联评审无剩余范围内问题。

证据根目录 `/private/tmp/wp0019-s1-c01-20261019-e9kyx4ua`，目录 0700、文件 0600；截图只在其中，不进入仓库。`safe-evidence-digests.json` 绑定 95 项非秘密产物，SHA-256 `98468588249cf1b4666b1d98259b02816787f1de7dd6f14fe4ad81a248138214`，受保护值及待提交 diff 扫描均 0 命中；保留所有原始失败，排除凭据、秘密摘要、截图、trace 和可重建缓存。

收口实际执行 `make decisions-sync`、`make project-status`、`make project-verify`（81 项／30 个任务）、`make architecture-guard`（50 项及双端守卫）、`make resume-context` 均退出 0，日志为本目录 `closeout-*.log`；恢复输出 11,428 字节，低于 12 KiB。STATE 及整片完成事实未变，最终固定文档 diff 再校验后精确路径提交，正常推送 main；最终 Git／CI 回执在推送后独立核对。
