# WP-0019：模型供应与租户使用

| 字段 | 值 |
| --- | --- |
| ID | WP-0019 |
| 状态 | 进行中 |
| 负责人 | 模型供应与租户使用 |
| 分支 | main |
| 里程碑 | M6 |
| ADR | ADR-0010, ADR-0022, ADR-0060, ADR-0066, ADR-0070, ADR-0074, ADR-0079, ADR-0082, ADR-0095, ADR-0100 |
| 证据 | pending |
| 提交 | pending |
| 输入提交 | 8ade6bbe15a53fdf73a1fea794e642632f59b835 |
| 基线提交 | 8ade6bbe15a53fdf73a1fea794e642632f59b835 |
| 人工验收 | 不适用 |

## 问题

平台供应商与部署、租户使用和路由未完整承接原型。WP-0018 已按 ADR-0094 调整范围收口，本包设计 D1～D5 已人工接受，S1 平台连接与厂商预设已闭合；当前推进 S2，三项细化提案已接受，具体原型候选已固定，待单独确认。原 WP-0019 普通问答目标按 ADR-0079 转入 WP-0024。

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

S2 两页四操作、已接受／待决定事项及逐项验收标准见 [S2 实施边界](s2-platform-supply.md)。三项方向已接受并登记 ADR-0100，原项目具体候选 `f3216dffe717e194e96fa20b4afee7983a9ff420` 已完成技术验证，单独接受后再生产承接；不把源码修复或原型模拟提升为 S2 部署或切片通过。

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

前置完成依据为 [WP-0018 调整范围收口](../WP-0018/evidence/WP-0018-adjusted-scope-closure-20261009.md)，输入提交 `8ade6bbe15a53fdf73a1fea794e642632f59b835`。D1～D5 于 2026-10-12 接受并登记 ADR-0095；各次原型接受见[人工接受记录](evidence/WP-0019-S1-prototype-acceptance-20261012.md)。

[S1 正式切片收口](evidence/WP-0019-S1-slice-closure-20261019.md)绑定验证提交 `c563d04ef7dd14d4eee00b2e7b4a0277c34548f3`：原五项真实操作、独立原型、只读事实、名称修复和最新 main 的云端 CI 组合通过。原创建／发现／精确授权发布、表单／分页／七态、真实空目录、失败与修复过程从该报告链接恢复；原日期及版本不改写，不再在恢复入口重复过程清单。

## 下一前沿

**S1 两入口、五项责任操作已闭合，无已确认阻断。** 第一次 CI 的账单失败保留；[同一验证提交的第 2 次尝试](https://github.com/liming-cloud/ai-platform/actions/runs/39259795183)已实际 success，九步骤全部成功。五项在[唯一实现矩阵](../milestones/M6/implementation-map.json)为 verified，完整提交和责任证据分别绑定；本轮未重复产品验收或修改数据。

S1 收口验证的前端为 `7b465a2dda50eacc455bfb82f83e6606865b4c62`、三后端为 `09fafd33d2b6bf70f663c90bf51e4233ffe2380c`；本轮未部署，不把 S2 源码或控制面提交当作运行版本。原型仍是 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`、已接受 `cce686e4634ca3c523e4c8aeffd95752f1a04c3a`；新候选未接受前不提升该基线。

S1 收口记录中的方舟精确连接修订 2、单独授权发布及平台部署／目录／租户授权为 0 的原时点事实保留；本轮不修改数据或重做 S1。Git 忽略的普通 0600 `pwd.txt`、`AccessKey.txt` 继续作为既有受保护来源，S2 沿用其用途边界，不要求重发密码。

**当前前沿：S2 平台供应。** 用户已接受 [S2 三项细化提案](s2-platform-supply.md#三项细化提案已接受)，登记 ADR-0100；原项目候选 `f3216dffe717e194e96fa20b4afee7983a9ff420` 已形成，23 项状态测试、84 次页面／24 次表单观察及模拟流程、只读恢复、取消清理通过，具体结果与临时证据摘要见同一文档。下一动作审阅并单独接受候选，随后生产承接。“未发布连接不能建部署”的源码修复已绑定 `4ad62393847549a92593068f79ce6ee6f1ec5cb0`，上下文及依赖 284 项通过，未部署；不以该局部修复提升四项责任操作。真实推理、精确部署发布与租户赋权的执行授权另行核对，不沿用 S1 发现授权。

S2～S6 二十四项操作、WP-0019 整包和 M6 均未完成，STATE 保持 WP-0019／M6；不进入 S3。完整 IAM 审计、M8 导航、治理概览和 M6 人工门禁继续由原责任切片交付。
