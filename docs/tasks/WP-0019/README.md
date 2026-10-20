# WP-0019：模型供应与租户使用

| 字段 | 值 |
| --- | --- |
| ID | WP-0019 |
| 状态 | 进行中 |
| 负责人 | 模型供应与租户使用 |
| 分支 | main |
| 里程碑 | M6 |
| ADR | ADR-0010, ADR-0022, ADR-0060, ADR-0066, ADR-0070, ADR-0074, ADR-0079, ADR-0082, ADR-0095, ADR-0100, ADR-0101 |
| 证据 | pending |
| 提交 | pending |
| 输入提交 | 8ade6bbe15a53fdf73a1fea794e642632f59b835 |
| 基线提交 | 8ade6bbe15a53fdf73a1fea794e642632f59b835 |
| 人工验收 | 不适用 |

## 问题

平台供应商与部署、租户使用和路由未完整承接原型。WP-0018 已按 ADR-0094 调整范围收口，本包设计 D1～D5 已人工接受，S1 平台连接与厂商预设已闭合；当前推进 S2，已按接受原型完成草稿、精确详情与完整表单子任务；搜索、能力验证和发布赋权仍在 S2 后续。原 WP-0019 普通问答目标按 ADR-0079 转入 WP-0024。

## 本工作包唯一结果

厂商预设、连接验证、平台发布与授权、租户配置验证、部署及同能力同驻留区域路由形成可操作和可诊断的闭环。

## 范围内

原型入口：platform-model-connections、platform-model-connection-detail、platform-model-deployments、platform-model-deployment-detail、model-providers、model-provider-detail、model-configurations、model-configuration-detail、model-deployments、model-deployment-detail、routing-policies。

- 依赖 WP-0018；官方资料验证厂商、能力、区域及地址预设，不以 UI 选择代替真实探测。
- 平台连接/部署独立发布、授权与停用；租户只消费有效供应，配置/部署/路由分别保存。
- Chat/Embedding 经内部 Port 和 Spring AI；rerank 用独立最小端口，不伪造 OpenAI 标准。
- 密钥专用提交/轮换，不回显；基础设施 HTTP 实现域名、SSRF、超时、重试和脱敏。
- 冻结路由和能力快照，拒绝不兼容维度、跨能力和跨区域隐式回退。

## 范围外

无调用方模型能力、全量厂商重写、知识索引、自动索引激活、多模态主检索和生产发布。

## 公共接口与数据

复用现有连接、配置、部署、授权和路由资源；缺口先接受专属 ADR。公开合同不暴露厂商 SDK、Spring 或 HTTP 类型。

## 约束

触及旧新分支、内联 SQL 和框架泄漏先清理；网络调用不持有业务事务，过期配置不能覆盖新结果。未完成真实厂商合同不得宣称能力可用。

承接[分页守卫盘点](../WP-0017/evidence/WP-0017-pagination-guard-closure-20261002.md)中 `ControlledModelCatalogEntryMapper#selectPublishedAvailablePage`、`ModelConfigurationMapper#selectVisiblePage`：触及目录时在 infrastructure 改用 `Page/IPage`，删除外层手工分页，保留来源可用性、租户授权谓词及固定排序；验证页边界和同范围总数后递减精确基线，不迁移无关模型能力。

## 验收标准

- 11 入口真实保存、发布、撤回和失败恢复；空、拒绝、过期和权限反例通过。
- Chat/Embedding/所声明 rerank 各有协议反例与受控真实调用证据，不打印秘密。
- SSRF、密钥轮换、授权撤销、过期快照、维度和区域不匹配均失败关闭。
- 三尺寸、分页、厂商表单、弹窗与原型一致。

逐片反例、真实事务／厂商证据、七态三尺寸和整包门禁见[设计与切片计划](design-and-slices.md)。本包共十一入口、二十九项生产操作，分为 S1 平台连接（5）、S2 平台供应（4）、S3 租户发现（4）、S4 配置版本（7）、S5 租户部署（5）、S6 路由（4）；每片功能与原型一致性分别闭环后才进入下一片。

S2 两页四操作、已接受／待决定事项及逐项验收标准见 [S2 实施边界](s2-platform-supply.md)。三项方向已接受并登记 ADR-0100，原项目具体候选 `f3216dffe717e194e96fa20b4afee7983a9ff420` 已完成技术验证并获人工接受，草稿、精确详情与完整表单的当前部署和原型验收见下方独立交付证据；不把前两项操作通过提升为 S2 整片通过。

## 验证

    make project-status
    make project-verify
    make architecture-guard
    ./mvnw -q -pl backend/contexts/model-runtime -am test
    make frontend-test
    make work-package-acceptance-test
    make acceptance-model-provider
    make acceptance-model-deployment

S1 已落地 make acceptance-work-package WP=WP-0019 SLICE=platform-connection，默认只读，创建草稿、真实发现、发布当前修订分别显式选择。入口测试不代表部署通过；本包完成前仍须执行真实数据库/会话、HTTP、生产浏览器及 Compose。S2 已新增 `SLICE=platform-supply` 的默认只读／单次草稿入口，前两项责任操作已完成真实创建与只读恢复，后两项仍未交付；S3～S6 与整包入口未实现，缺少基础设施、受控凭据或评测材料明确阻塞，不静默跳过。

## 证据

前置完成依据为 [WP-0018 调整范围收口](../WP-0018/evidence/WP-0018-adjusted-scope-closure-20261009.md)，输入提交 `8ade6bbe15a53fdf73a1fea794e642632f59b835`。D1～D5 于 2026-10-12 接受并登记 ADR-0095；各次原型接受见[人工接受记录](evidence/WP-0019-S1-prototype-acceptance-20261012.md)。

[S1 正式切片收口](evidence/WP-0019-S1-slice-closure-20261019.md)绑定验证提交 `c563d04ef7dd14d4eee00b2e7b4a0277c34548f3`：原五项真实操作、独立原型、只读事实、名称修复和最新 main 的云端 CI 组合通过。原创建／发现／精确授权发布、表单／分页／七态、真实空目录、失败与修复过程从该报告链接恢复；原日期及版本不改写，不再在恢复入口重复过程清单。

[S2 草稿与精确详情交付](evidence/WP-0019-S2-draft-detail-delivery-20261020.md)绑定前端验证提交 `98ffdd5b0fa6cc388cef82d8ca8fca0782fd4671`、后端运行提交 `e28aa34572b1894908ae538f7dc74071a00fedf9` 和已接受原型 `f3216dffe717e194e96fa20b4afee7983a9ff420`。唯一真实草稿、失败与修复链、最终只读浏览器、原型对照及数据库事实分别登记。

[S2 受控验证准备](evidence/WP-0019-S2-capability-preparation-20261020.md)绑定源码提交 `dbf482ce1d9eee0af2cc1716ec00d648113b8e6b`：厂商／模型／预算／样本的[可审阅方案](s2-capability-verification.md)已形成，最小平台协议与反例通过完整后端；没有真实推理、部署、发布或赋权，不增加产品操作完成数。

[S2 预算与安全样本离线验证](evidence/WP-0019-S2-budget-safety-offline-20261020.md)绑定源码提交 `77cda4b01e7e29552adfc8ca316cd8b3883bb13f`：组合与预算方向已接受，固定三样本、费用上界、实际计量、总截止时间与取消的 29 项离线合同及 1,240 项完整后端通过。尚未装配生产或执行真实调用；[RERANK 来源合同](s2-rerank-source-proposal.md)已接受并登记 ADR-0101，正在完成提交推送与对应 CI 核验，不提升“执行能力验证”状态。

## 下一前沿

**S1 两入口、五项责任操作已闭合；S2 草稿与查看部署两项已闭合，合计 7／29。** S1 原验收事实、日期和版本不改写，不重复其业务验收。S2 最终前端为 `98ffdd5b0fa6cc388cef82d8ca8fca0782fd4671`，三后端为 `e28aa34572b1894908ae538f7dc74071a00fedf9`；最终 Compose、浏览器、原型及只读事实均通过，源码 [CI](https://github.com/liming-cloud/ai-platform/actions/runs/39374085111)九步骤实际 success，没有已确认的账单阻断。

已通过正式表单建立唯一合成草稿 `2112408560880058369`，仍为 DRAFT／版本 0，没有真实能力验证、发布或生效授权。CHAT／EMBEDDING／RERANK 的声明、参数校验和未知候选确认已交付；声明不是实测能力。新草稿的旧探测／发布路径失败关闭。凭据复用 Git 忽略的普通 0600 `pwd.txt`，不要求重发；本轮不使用或导出厂商密钥。

**当前前沿仍为 S2。** 组合与预算方向已接受，三样本／费用／实际用量／总截止时间／取消的离线内核已通过本地验证；尚未装配生产，实际运行版本仍沿前述草稿交付记录。下一步按已接受 ADR-0101 实现 RERANK 权威来源，接入完整生产样本／usage、独立重排适配和真实事务提交，再准备来源、账户价格与计量、正确 Chat 草稿及精确执行授权；不重取既有密码，不从组合接受外推真实调用许可。原子发布与生效租户授权仍需实现及独立授权。用户已于 2026-10-20 明确确认仓库保持公开并推送，取代 GOV-0002 当时的私有目标；旧迁移验收事实不改写。当前自动推送经本地验证的子任务提交，并独立核对相应 CI，不改仓库可见性。目录搜索仍留 S2 后续，合同方向尚未接受；同名租户的人工辨识问题在发布审阅前按原型决策处理。

S2 为 2／4，余下两项及 S3～S6 二十项共二十二项操作未完成；WP-0019 整包和 M6 未完成，STATE 保持 WP-0019／M6，不进入 S3。完整 IAM 审计、M8 导航、治理概览和 M6 人工门禁继续由原责任切片交付。
