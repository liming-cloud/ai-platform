# WP-0019：模型供应与租户使用

| 字段 | 值 |
| --- | --- |
| ID | WP-0019 |
| 状态 | 进行中 |
| 负责人 | 模型供应与租户使用 |
| 分支 | main |
| 里程碑 | M6 |
| ADR | ADR-0010, ADR-0022, ADR-0023, ADR-0058, ADR-0060, ADR-0066, ADR-0070, ADR-0074, ADR-0079, ADR-0082, ADR-0095, ADR-0096 |
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
    make work-package-acceptance-test
    make acceptance-model-provider
    make acceptance-model-deployment

S1 已落地 make acceptance-work-package WP=WP-0019 SLICE=platform-connection，默认只读，创建草稿、真实发现、发布当前修订分别显式选择。入口测试不代表部署通过；本包完成前仍须执行真实数据库/会话、HTTP、生产浏览器及 Compose。S2～S6 与整包入口未实现，缺少基础设施、受控凭据或评测材料明确阻塞，不静默跳过。

## 证据

前置完成依据为 [WP-0018 调整范围收口](../WP-0018/evidence/WP-0018-adjusted-scope-closure-20261009.md)，绑定输入提交 `8ade6bbe15a53fdf73a1fea794e642632f59b835`；它不证明本包模型能力已完成。

本包领域、真实事务、接口、浏览器和安全证据按切片分别登记。原型原人审依据 docs/tasks/WP-0016/evidence/WP-0016-human-acceptance-20260913.md，后续修订单独绑定版本，不代替生产验证；不得记录密码、摘要或 token。

本轮设计调查绑定 `23b5bae1f847117a2c2a8571f7260bf6b56ac452`：[设计与切片计划](design-and-slices.md)、[生产契约提案](contract-proposal.md)。D1～D5 已于 2026-10-12 人工接受并登记 ADR-0095；设计接受不改变实现矩阵、STATE、部署或整包完成事实。

[设计执行证据](evidence/WP-0019-design-and-slice-plan-20261011.md)绑定设计提交 `ac1859cc66078bb5c1d1a89dd16e342ef4ef3a71`，记录原项目观察、操作覆盖、图谱影响及治理门禁；不作为生产模型功能或视觉验收证据。

[发现凭据原型候选与只读详情源码证据](evidence/WP-0019-S1-discovery-credentials-and-detail-source-20261012.md)绑定源码 `c23b2960ae658c12202c9303e7971320ce872225` 和原型候选 `d5deddaa1955814c5f08795b0b1ca58a7e6ac784`：原型状态 178 项、浏览器专项 15＋既有回归 12 场景、只读详情定向 15 项及完整后端 1106 项通过；只证明声明范围，候选待人工接受，未部署或完成生产一致性验收。

[预设来源源码与既有连接复验证据](evidence/WP-0019-S1-preset-source-and-existing-connection-check-20261012.md)绑定 `c52669a9bdba52b25aba2efdbd7694c617df6aa1`：五厂商来源／能力意图持久化、草稿出站校验、部分 AK/SK 拒绝及 MyBatis-Plus 分页通过；完整后端 1116 项、前端 268 项及架构／治理门禁通过。有效登录凭据已找到并经正式接口使用，无需人工重新提供。仅对一个既有连接执行的发现被 DNS 出站策略拒绝，尚未到达厂商；数据库只有推理 Key，缺少独立发现 AK/SK。本批次未部署，生产功能与视觉仍未验收。

[重发现一致性与连接页面源码证据](evidence/WP-0019-S1-connection-consistency-source-20261012.md)绑定 `fc970f9ec5a07da1eb41cc80ec84f31d93469f48`：重发现时效／状态及原子 CAS、非秘密配置事实、五厂商表单、详情深链接、探测与独立发布交互已完成本批源码验证。完整后端 1124 项、前端 267 项、隔离浏览器 29 项及架构／治理通过；含身份替换模态残留和旧协议误标的红绿证据。未部署或获得 S1 生产及原型一致性验收结论。

[正式只读复验与部署对照](evidence/WP-0019-S1-deployed-read-only-and-parity-audit-20261012.md)绑定 `3efce6946c7af8f934a7bfaa9928c8b77df046a4`：正式入口、过期发布提示与标题排版局部修复已验证；前端 274 项、隔离浏览器 32 项，最终 Compose 正式只读 10 项通过，业务写入为 0，精确数据库事实不变。当轮前端运行此提交，三个后端应用运行 `dbe0d5d0f780bd867c8f2b1a2afdcd48c1d51195`，镜像分别登记。标题／正文基线 36 项精确对照通过；当轮搜索、完整分页、页头摘要及其他页面细节尚未承接，完整原型一致性未通过。没有真实厂商成功合同，未执行草稿／发现／发布生产闭环，不表示 S1 完成。

[目录查询源码与部署记录](evidence/WP-0019-S1-directory-query-source-and-deploy-20261013.md)绑定已接受 ADR-0096、后端 `7906451e3f2e04a03e098e601b37425e29857cb5` 和前端 `753aec9250821541fd87b3c9c89e689d773b8e2a`：有界服务端搜索、全目录发布摘要及完整分页已实现；后端 1132 项、前端 277 项、隔离浏览器 29＋32 项通过。四应用已更新、七服务健康、四入口及精确只读事实通过；原型 20 个依赖匹配接受版本。当前缺有效平台验收凭据，新候选正式生产浏览器和完整视觉未执行，不沿用上轮通过结论。

[最新验收阻断与原型空态候选](evidence/WP-0019-S1-acceptance-blockers-and-prototype-empty-20261014.md)绑定正式入口 `83d5417065ec21612012c48acae75838317a3c42`：两个既有环境登录来源分别执行一次，均 401；平台主体／资格活动，S1 页面断言未运行、业务写入 0。七服务、四入口及精确模型事实通过，DNS 仍为保留网段。原型空态候选 `82f3a15e1875856e655e8b72e4b6673534138160` 的 15＋12 项浏览器、14 项状态／预设通过；提交时待接受，随后人工接受登记在本包接受记录，不代替生产视觉。

## 下一前沿

当前仍为 S1，五项操作生产验收尚未整体闭合。用户于 2026-10-12 接受方舟独立发现凭据区版本 `d5deddaa1955814c5f08795b0b1ca58a7e6ac784`，于 2026-10-14 明确接受两页空态说明修正 `82f3a15e1875856e655e8b72e4b6673534138160`，后者为当前原型基线，见[人工接受记录及承接检查](evidence/WP-0019-S1-prototype-acceptance-20261012.md)。前两版保留历史，不改写当时验证。OpenDesign Cloud 仍绑定原注册目录，不建立副本。

下一步仍在 S1：先解决原型空目录没有新增入口、空详情没有返回路径的已核实交互缺口。本次文案接受保持有效；决定返回前保留生产创建能力，不将空发现结果等同于连接不存在。10 月 14 日两个现存登录来源均 401，平台主体／资格活动，200 个安全 JSON 无平台材料，不原样重试或重置账号。恢复有效受控来源后，分别固定当前运行版本，串行执行正式只读和新基线原型对照。DNS 保留网段及独立发现用途缺口仍须满足后执行受控厂商合同，不放宽 SSRF 或导出密钥。未经功能与完整视觉闭环不进入 S2；已接受设计、源码通过和模型能力可用分别结论。

模型供应闭环完整验证并提交后才进入 WP-0020；平台／租户概览、普通会员问答及 M6 全量和人工门禁仍由原责任包完成。
