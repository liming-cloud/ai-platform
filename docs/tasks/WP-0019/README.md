# WP-0019：模型供应与租户使用

| 字段 | 值 |
| --- | --- |
| ID | WP-0019 |
| 状态 | 进行中 |
| 负责人 | 模型供应与租户使用 |
| 分支 | main |
| 里程碑 | M6 |
| ADR | ADR-0010, ADR-0022, ADR-0023, ADR-0040, ADR-0058, ADR-0060, ADR-0066, ADR-0070, ADR-0074, ADR-0079, ADR-0082, ADR-0095 |
| 证据 | pending |
| 提交 | pending |
| 输入提交 | 8ade6bbe15a53fdf73a1fea794e642632f59b835 |
| 基线提交 | 8ade6bbe15a53fdf73a1fea794e642632f59b835 |
| 人工验收 | 不适用 |

## 问题

平台供应商与部署、租户使用和路由未完整承接原型。WP-0018 已按 ADR-0094 调整范围收口，本包设计 D1～D5 已人工接受，当前实施前沿为 S1 平台连接与厂商预设。原 WP-0019 普通问答目标按 ADR-0079 转入 WP-0024。

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

## 验证

    make project-status
    make project-verify
    make architecture-guard
    ./mvnw -q -pl backend/contexts/model-runtime -am test
    make frontend-test
    make acceptance-model-provider
    make acceptance-model-deployment

本包完成前必须落地 make acceptance-work-package WP=WP-0019，执行真实数据库/会话、HTTP、生产浏览器及 Compose；当前入口尚未实现，不得当作已通过。缺少基础设施、受控凭据或评测材料明确阻塞，不静默跳过。

## 证据

前置完成依据为 [WP-0018 调整范围收口](../WP-0018/evidence/WP-0018-adjusted-scope-closure-20261009.md)，绑定输入提交 `8ade6bbe15a53fdf73a1fea794e642632f59b835`；它不证明本包模型能力已完成。

本包领域、真实事务、接口、浏览器和安全证据按切片分别登记。原型原人审依据 docs/tasks/WP-0016/evidence/WP-0016-human-acceptance-20260913.md，后续修订单独绑定版本，不代替生产验证；不得记录密码、摘要或 token。

本轮设计调查绑定 `23b5bae1f847117a2c2a8571f7260bf6b56ac452`：[设计与切片计划](design-and-slices.md)、[生产契约提案](contract-proposal.md)。D1～D5 已于 2026-10-12 人工接受并登记 ADR-0095；设计接受不改变实现矩阵、STATE、部署或整包完成事实。

[设计执行证据](evidence/WP-0019-design-and-slice-plan-20261011.md)绑定设计提交 `ac1859cc66078bb5c1d1a89dd16e342ef4ef3a71`，记录原项目观察、操作覆盖、图谱影响及治理门禁；不作为生产模型功能或视觉验收证据。

[连接独立发布源码证据](evidence/WP-0019-S1-connection-publication-source-20261012.md)绑定 `c374be42c834cf9028fc0501801a1c1af8996bb9`：发布事实、权限、修订／时效、并发重放、实际落库响应及失败回滚通过；完整后端 1101 项通过。仅为 S1 后端子任务，未部署或完成 S1 生产／原型验收；首次隔离数据库端口冲突与复跑结果一并保留。

[发现凭据原型候选与只读详情源码证据](evidence/WP-0019-S1-discovery-credentials-and-detail-source-20261012.md)绑定源码 `c23b2960ae658c12202c9303e7971320ce872225` 和原型候选 `d5deddaa1955814c5f08795b0b1ca58a7e6ac784`：原型状态 178 项、浏览器专项 15＋既有回归 12 场景、只读详情定向 15 项及完整后端 1106 项通过；只证明声明范围，候选待人工接受，未部署或完成生产一致性验收。

[预设来源源码与既有连接复验证据](evidence/WP-0019-S1-preset-source-and-existing-connection-check-20261012.md)绑定 `c52669a9bdba52b25aba2efdbd7694c617df6aa1`：五厂商来源／能力意图持久化、草稿出站校验、部分 AK/SK 拒绝及 MyBatis-Plus 分页通过；完整后端 1116 项、前端 268 项及架构／治理门禁通过。有效登录凭据已找到并经正式接口使用，无需人工重新提供。仅对一个既有连接执行的发现被 DNS 出站策略拒绝，尚未到达厂商；数据库只有推理 Key，缺少独立发现 AK/SK。本批次未部署，生产功能与视觉仍未验收。

[重发现一致性与连接页面源码证据](evidence/WP-0019-S1-connection-consistency-source-20261012.md)绑定 `fc970f9ec5a07da1eb41cc80ec84f31d93469f48`：重发现时效／状态及原子 CAS、非秘密配置事实、五厂商表单、详情深链接、探测与独立发布交互已完成本批源码验证。完整后端 1124 项、前端 267 项、隔离浏览器 29 项及架构／治理通过；含身份替换模态残留和旧协议误标的红绿证据。未部署或获得 S1 生产及原型一致性验收结论。

## 下一前沿

当前仍为 S1。连接发布、重发现一致性、非秘密事实与两页交互已通过本批源码验证，五项操作的生产验收尚未整体闭合。用户于 2026-10-12 明确回复“接受，按该版本继续 S1”，接受原项目方舟独立发现凭据区及详情状态修订 `d5deddaa1955814c5f08795b0b1ca58a7e6ac784`，见[人工接受记录](evidence/WP-0019-S1-prototype-acceptance-20261012.md)。先前版本 `35571b0f823390bd6b80f5771e512b11be9403a4` 保留为历史基线。用户已解锁，本轮 OpenDesign Cloud 项目连接恢复，仍绑定原注册目录；这些环境恢复不作为生产验收事实。

下一步仍在 S1：建立正式 `WP=WP-0019 SLICE=platform-connection` 验收入口，接续已验证源码，先核实安全出站 DNS 与匹配的独立发现认证，再完成受控真实厂商合同。不得放宽 SSRF、导出厂商密钥或用旧 VERIFIED 替代。随后执行最新 Compose 真实会话、精确对象事实、两页及弹窗七态三尺寸／200% 文字与专项原型对照；未经功能与视觉闭环不得进入 S2。有效登录凭据已找到并使用，不重复要求人工提供。配置影响仍只承接真实候选／评测标记，不提前实现索引和助手完整生命周期。本包当前没有新的生产浏览器、真实厂商或生产原型一致性完成结论，不沿用 WP-0018 验证代替。

模型供应闭环完整验证并提交后才进入 WP-0020；平台／租户概览、普通会员问答及 M6 全量和人工门禁仍由原责任包完成。
