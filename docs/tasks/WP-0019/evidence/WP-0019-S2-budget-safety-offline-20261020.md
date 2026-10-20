# S2 预算与安全样本离线验证

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0019／S2 |
| 输入提交 | 6b54c8c33f8e943a060234e80f97a6882acc96c0 |
| 验证源码提交 | 77cda4b01e7e29552adfc8ca316cd8b3883bb13f |
| 验证范围 | 单目标预算、固定三样本、结果绑定及超时／取消的离线编排；不含真实厂商合同 |
| 日期 | 2026-10-20 |
| 状态 | 离线子任务已闭合；源码与本地验证、公开推送及对应 CI 通过 |
| 产品进度 | S2 2／4，WP-0019 7／29；整片、整包与 M6 未完成 |

## 接受依据与本轮结果

用户接受推荐厂商／模型组合与预算方向，输入方案正文 SHA-256 为 `425ccbabe82880d4b1f7777a8b002ad35dbbad31753e1460535241f96e73306d`，已登记 ADR-0100。真实调用、建立正确模型草稿、部署发布与赋权仍分别授权；不沿用 S1 的发现／连接发布授权。受保护 `pwd.txt` 继续作为既有登录来源，本轮离线实现无需读取密码或厂商凭据。

在 model-runtime 同一应用包内增加 `PlatformCapabilityVerificationSession` 和 `PlatformCapabilitySafetySuite`，没有新模块、公开端口、HTTP／CLI、Spring Bean 或持久化字段。当前调用方为离线合同测试，未装配生产；原 `PlatformModelDeploymentService` 与探测选择服务未变，完整草稿的 `PLATFORM_DEPLOYMENT_CONTRACT_PENDING` 保护保留。

- 固定 `s2-capability-safety-v1`：CHAT／EMBEDDING／RERANK 各三个合成样本，顺序执行、每样本至多一次回调，不能删失败项或重跑同一会话。合计输入按 Unicode 码点计数，Chat 总输出上限 64 Token，单轮单调时钟截止时间 30 秒。
- 精确连接／部署修订、模型、驻留、输入与参数、报价和样本分别绑定，随机 nonce 防止跨轮复用。报价未知／过期／错目标、费用不足在回调前拒绝；BigDecimal 向上计算整轮费用上界。计费输入单位与实际输入 Token 分开，按请求计价也必须有 Token 上界与实际用量回执。
- Chat 校验三个精确预期；Embedding 校验唯一索引零、精确维度及有限非零向量；RERANK 校验三个唯一完整索引、有限非递增得分和文档换序预期，不臆设统一分数范围。结果摘要只保存安全类别、指纹、保守调度计数和费用上界，正文／向量／原始异常不进入摘要。
- 调度等待纳入总截止时间，实际回调前再检查；取消／超时中断当前任务、拒绝迟到结果并停止后续样本。忽略中断的测试回调不能产生验证、发布或授权事实。

`reservedFee` 是本次内存费用上界，尚未接入跨进程账本或实际扣费。报价摘要有效不证明账户报价真实，未来生产调用方须核实价格与完整输入计量；零编排重试也不证明底层网络客户端零重试。真实网络取消、重定向、响应上限与完整协议适配仍待后续，不据此宣称厂商验证通过。

## 红绿、回归与范围复核

| 检查 | 实际结果与证据 |
| --- | --- |
| 初始新功能红灯 | 18 项：API 骨架尚未实现导致 17 个 UnsupportedOperation 错误，输入边界缺少拒绝导致 1 个断言失败；不是已有生产功能故障。`red-budget-samples.log` → `green-budget-samples.log` |
| 总截止时间／上下文反例 | 调度时已到期仍调用一次；低费率下输入与生成预留超过上下文仍 PASSED，两项断言实际失败后修复。`red-dispatch-context-isolated.log` → `green-dispatch-context.log`；首轮受费用先行拒绝的日志另保留，不冒充上下文绕过证明 |
| 独立 Token 计量反例 | 按请求计价时缺 Token 上界仍 PASSED；实际 Token 超出证据仍 PASSED，分别实际失败后修复。`red-independent-token-proof.log`、`red-actual-token-receipt.log` 及对应 green 日志 |
| 最终定向合同 | 2 类、29 项，失败／错误／跳过 0；`focused-tests-final.json`，含三类正例、预算、重放、Unicode、阻塞超时／取消、错误与结构反例 |
| 完整后端 | `./mvnw -q test`：355 类、1,240 项，失败／错误／跳过 0；全部 XML 为最后一轮新报告，源码摘要与提交一致；`backend-final.log`、`backend-test-summary.json` |
| 架构守卫 | 50 项单元测试及双端守卫通过，后端依赖边界违规 0、UUID 文件 0。初次因新代码使用 UUID 被拒绝，改为非业务标识的随机字节 nonce；未修改守卫或基线。`architecture-guard.log` → `architecture-guard-closeout.log` |
| 治理与恢复 | `make project-status`、`make decisions-sync`、`make project-verify`、`make resume-context`；最终控制面更新后再执行并保留 `closeout-*` 日志。81 项治理测试、30 任务与 650 项历史对象沿原门禁校验 |

中间两轮全量结果分别为 1,237／1,239 项，只记录当时范围，不能替代最后源码的 1,240 项。修正均先保留失败证据，没有降低断言、删除样本或放宽门禁。既有 S1、草稿、页面矩阵和原型浏览器验收没有重跑。

## 部署与原型分别记录

本轮未部署。开始时只读快照中七个 Compose 服务均 healthy；运行版本与本轮源码提交不同：

| 运行组件 | OCI revision | 镜像 SHA-256 |
| --- | --- | --- |
| console-web | 98ffdd5b0fa6cc388cef82d8ca8fca0782fd4671 | ed800fb0a9225d550d561e05b3473b4710b0c013cde0df9a01f02b84e172f0a7 |
| control-plane | e28aa34572b1894908ae538f7dc74071a00fedf9 | 9aa1125f21b3bef864ef8ff9adb8d5b690ed2ee68846e8ff81429da6bb7693e4 |
| query-runtime | e28aa34572b1894908ae538f7dc74071a00fedf9 | 9d0f531bf420a05b72715a4c869e25e584ebb94868b4f66fc3a6164af1f74830 |
| ingestion-worker | e28aa34572b1894908ae538f7dc74071a00fedf9 | 251c4bc395e3dfb20dc1fa7203ad1d4c1afb4fece04751440ce2f3b1f8a6e463 |

OpenDesign Cloud 唯一原项目为 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，S2 已接受版本仍为 `f3216dffe717e194e96fa20b4afee7983a9ff420`；[平台部署入口](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-deployments&state=success&review=1)。受控原型目录与该版本差异为零。本轮没有页面改动或新的视觉验收，不把原型资源未变说成重新通过浏览器验收；原生产还原结论仍见[草稿与详情交付](WP-0019-S2-draft-detail-delivery-20261020.md)。截图未进入仓库，本轮没有截图产物。

## 已接受来源与下一前沿

[RERANK NONE 来源提案](../s2-rerank-source-proposal.md)比较静态目录与已核实的官方重排列表，推荐仅硅基流动增加显式列表配置，其他 NONE 不变。用户随后明确接受推荐方案并登记 ADR-0101；本报告的源码验证仍未改预设、发现配置、候选、连接数据、来源 API 或迁移，不把设计接受记为来源功能完成。

后续按已接受的来源合同推进离线实现，补齐生产样本／usage 与独立重排协议、底层网络边界和真实事务提交（来源修订、CAS、取消／迟到、审计回滚）；价格真实性及计量材料继续准备。最近已登记的来源准备事实沿[受控验证方案](../s2-capability-verification.md)，本轮不重复读取业务数据，也不凭旧材料宣称新来源已就绪。现有合成 Chat 草稿不用于真实模型验证。材料齐备后才单独授权精确调用，发布及生效租户授权仍未实现和授权；搜索留在 S2 后续，S3 不启动。

推送前 GitHub API 实际返回 `liming-cloud/ai-platform` 的 `isPrivate=false`，与 GOV-0002 当时的私有目标不同。用户随后明确回复“确认保持公开并推送”，当前公开范围和自动推送已获授权，替代旧目标的可见性要求；旧迁移事实不改写。未修改仓库可见性或付款设置。源码与接受记录已推送至 `dda345d2082320add8aeace400ce1fcff59e2564`，对应 [CI](https://github.com/liming-cloud/ai-platform/actions/runs/39389463323)实际 completed／success，九步骤均 success，完整回执为 `ci-source-closeout-full.json`。本地源码与 1,240 项回归绑定仍为前述 `77cda4b01e7e29552adfc8ca316cd8b3883bb13f`；后续仅文档收口提交另行核对其 CI，不用此绿灯替代新 HEAD。

## 可复核资料与工具

执行资料为 `/private/tmp/wp0019-s2-budget-20261020-s5R98Q`（0700，文件 0600）。`evidence-digests.json` 绑定验证源码提交，包含 59 项、5,185,003 字节，SHA-256 为 `6f3b9bb9dcf1c1f135d49627e175126d87b217a611a09137288096aeca2be8aa`；不含清单自身及后续控制面验证／提交／推送回执。`runtime-readonly.json`、`scope-bindings.json`、`reviewed-source.diff`、`fixed-diff-review.txt` 分别保存运行快照、接受绑定、精确差异和内联自审，未冒充独立人工评审。

本轮使用当前 Local Codex 个人安装包中的 `engineering-assistant:wayfinder`、`codebase-design`、`tdd`、`code-review`，显式读取相应 SKILL 及 HITL、安全 seam、阶段证据规则；内联执行，无 Runtime／StageRunResult／质量节点，阶段结果校验不适用。图谱探索部署用例和探测选择链，影响分析为 `PlatformModelProbeProviderService` 38 个、发现读取端口 3 个、新 Session 内部 36 个符号；据此保留现有入口，限定为同包离线内核，无跨上下文依赖变化。
