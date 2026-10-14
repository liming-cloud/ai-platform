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

前轮成果分别见[预设来源与分页](evidence/WP-0019-S1-preset-source-and-existing-connection-check-20261012.md)、[重发现一致性与页面源码](evidence/WP-0019-S1-connection-consistency-source-20261012.md)、[首次正式只读部署复验](evidence/WP-0019-S1-deployed-read-only-and-parity-audit-20261012.md)。原版本、测试、DNS 拒绝及局部视觉结果保留于对应证据；它们不证明本轮部署、真实厂商成功或 S1 完成。

[目录查询源码与部署记录](evidence/WP-0019-S1-directory-query-source-and-deploy-20261013.md)绑定已接受 ADR-0096、后端 `7906451e3f2e04a03e098e601b37425e29857cb5` 和前端 `753aec9250821541fd87b3c9c89e689d773b8e2a`：有界服务端搜索、全目录发布摘要及完整分页已实现；后端 1132 项、前端 277 项、隔离浏览器 29＋32 项通过。四应用已更新、七服务健康、四入口及精确只读事实通过；原型 20 个依赖匹配接受版本。当轮缺有效平台验收凭据，候选正式生产浏览器和完整视觉未执行，不沿用上轮通过结论。

[接受空态的源码与前端部署](evidence/WP-0019-S1-empty-state-source-and-deploy-20261014.md)绑定 `0b41defb2620cbe765962a6af30fff4ba907a8ec`：空目录、精确连接不存在、返回恢复及正式模态触发器已承接；前端 277 项、隔离浏览器 41＋29 项、180 项面板属性对照及架构／入口合同通过。仅前端镜像更新，后端仍为 `7906451e3f2e04a03e098e601b37425e29857cb5`，七服务、四入口及两条匿名路由通过。保护页面和完整生产视觉未执行；旧候选与设计证据从接受记录恢复，不改写原结果。

[登录恢复、真实发现、精确发布及原型复验](evidence/WP-0019-S1-auth-vendor-publish-and-parity-20261014.md)绑定最终前端 `e671f4dcaa01609091e6811e4f69ba4d05b0e37b`，后端仍为 `7906451e3f2e04a03e098e601b37425e29857cb5`：复用有效来源后正式登录通过，精确 DNS 排除后既有 `OPENAI_MODELS_API` 发现 135 个真实候选；单独授权的修订 `4` 发布六项通过，最终生产只读十项通过、业务写入 0，数据库确认未创建部署或授权。前端 277 项、隔离浏览器 42＋33 项、入口合同 29＋22 项及架构通过；48 次观察 / 1452 项属性匹配，但人工发现原型和生产在 390px / 200% 文字均有目录重叠，共享范围色彩也待修正。完整视觉、生产草稿和新方舟 AK/SK 合同未闭合，S1 仍未完成。

[原项目连接恢复与手机重排](evidence/WP-0019-S1-prototype-mobile-reflow-20261014.md)绑定已人工接受的 `9008c62e7524f7bd2e96c2688c845388c6ef05d1`：经现有官方 MCP 入口重建工作连接，原项目读写及 HTTP 一致；仅修复 S1 两页手机单元格固定高度。真实红绿、15 项原型浏览器、14 项状态／预设和 84 次七态／三尺寸／双字号观察通过。冻结脚本恢复原摘要，反例迁入现行质量目录；尚未承接生产。旧客户端连接仍失效，不修改配置或安装包。

## 下一前沿

当前仍为 S1，五项操作生产验收尚未整体闭合。凭据区、空态文案与操作已接受；2026-10-14 用户进一步接受手机重排候选 `9008c62e7524f7bd2e96c2688c845388c6ef05d1`，成为当前 S1 原型基线，见[人工接受记录](evidence/WP-0019-S1-prototype-acceptance-20261012.md)。旧版与原验证时点保留历史。OpenDesign Cloud 仍绑定原注册目录，不建立副本。

当前可操作范围：平台连接目录、搜索分页、五厂商表单及详情已部署；既有旧协议连接已真实发现并独立发布。前端实际运行 `e671f4dcaa01609091e6811e4f69ba4d05b0e37b`，后端三应用仍运行 `7906451e3f2e04a03e098e601b37425e29857cb5`。401 与 fake-IP 阻断已解除，不再索取平台密码。真实草稿创建和新方舟控制面合同尚未验收，旧协议成功不能证明新协议成功。

下一步仍在 S1：按已接受的手机重排版本承接生产、修正共享色彩并完成七态视觉；官方 MCP 工作连接及原项目当前预览已核对。复用仍有效的 0600 登录来源，满足专用草稿材料及匹配的独立发现认证后补齐写入／新方舟合同。未经功能、完整视觉及相应真实合同闭环不进入 S2；不导出密钥、不把属性匹配写为完整一致。

模型供应闭环完整验证并提交后才进入 WP-0020；平台／租户概览、普通会员问答及 M6 全量和人工门禁仍由原责任包完成。
