# WP-0019：模型供应与租户使用

| 字段 | 值 |
| --- | --- |
| ID | WP-0019 |
| 状态 | 进行中 |
| 负责人 | 模型供应与租户使用 |
| 分支 | main |
| 里程碑 | M6 |
| ADR | ADR-0010, ADR-0022, ADR-0060, ADR-0066, ADR-0070, ADR-0074, ADR-0079, ADR-0082, ADR-0095, ADR-0096, ADR-0097, ADR-0098 |
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

前置完成依据为 [WP-0018 调整范围收口](../WP-0018/evidence/WP-0018-adjusted-scope-closure-20261009.md)，绑定输入提交 `8ade6bbe15a53fdf73a1fea794e642632f59b835`，不证明本包完成。D1～D5 已于 2026-10-12 接受并登记 ADR-0095；各次原型接受统一见[人工接受记录](evidence/WP-0019-S1-prototype-acceptance-20261012.md)，设计接受与生产验收分别记录。

[登录、发现与精确发布](evidence/WP-0019-S1-auth-vendor-publish-and-parity-20261014.md)保存旧协议发现 135 个候选和单独授权修订 4 发布的原时点事实。[手机承接与草稿验收](evidence/WP-0019-S1-mobile-parity-and-draft-20261014.md)保存原型重排承接及一个合成 DRAFT 的真实保存；未探测或发布该草稿。早期源码、目录查询和原型连接证据从这些报告恢复，不以旧结果替代当前部署。

[完整地址生产承接](evidence/WP-0019-S1-address-production-delivery-20261015.md)及[拒绝态生产承接](evidence/WP-0019-S1-denied-production-delivery-20261015.md)分别保存各自完整提交、原型版本及当轮验收；原日期与范围保持不变。

[原生输入承接与状态调查](evidence/WP-0019-S1-native-input-and-state-audit-20261016.md)绑定最终源码／前端运行 `6296a13db72d60da8712eda1200a1d9f1c92c583`：54 项表单、37 项分页、277 项前端及部署十项只读通过；54 组字体几何、18 组字段／箭头／焦点对照、36 次键盘观察通过，业务写入 0。当轮原型 partial／disabled 的 24 次观察及对象停用回归不代表页面触发已实现或 S1 通过。截图仅在 `/private/tmp`。

[写入可用性决定与源码验证](evidence/WP-0019-S1-write-availability-source-20261017.md)记录 10 月 17 日产品候选 `e0224fa9847fdaf297b3ad896e37ae7796e29a50`：348 套件／1,132 项后端、310 项前端，表单 60、partial／disabled 30、分页 37 项通过。三项人工决定已登记 ADR-0098；当轮两候选镜像构建成功，两后端构建因 Docker Desktop 不可用以 EOF 结束，未替换运行服务。该时点的部署、完整表单及 disabled 生产验收未执行；原始 66 组和上一轮辅助读取证据从本报告恢复。

[缓存修复与运行恢复](evidence/WP-0019-S1-cache-cleanup-and-runtime-resume-20261019.md)绑定缓存源码 `14914427775415a0b1686a7b312df4394a139d07`：21 项退出反例和 127 项真实 Chrome 源码回归通过，证据保留。该轮四应用部署至 e022，七服务／四入口、五项匿名检查和前后只读事实通过；当轮已认证验收未执行、CI 因账单未启动，历史结论不改写。

[登录恢复及部署后表单对照](evidence/WP-0019-S1-deployed-disabled-and-form-parity-20261019.md)绑定前端 `799621aeb23c8ba15c0b30bafcfb42a919236f42`、三后端 e022：本地 `pwd.txt` 恢复真实平台／租户登录，未开通或重置账号；暂停四命令、两轮正式十项、十二组 disabled 主控件、六十六组完整表单和十八组交互通过，原始文案差异按 ADR-0098 精确归因。分页禁用外观、其余完整页面对照及新方舟合同仍未闭合。

[最新分页部署对照](evidence/WP-0019-S1-pagination-prototype-verification-20261019.md)绑定最终前端 `7f05664fa77fb6aef3e948279a5657ce6d3ec610`：43 项浏览器源码、310 项前端通过；实际两页三尺寸／200%及禁用悬停 24 组差异 0，正式十项只读通过。首轮 164 项差异及手机宽度 16 项失败均保留；此结论仅限分页，不能代替其余页面级门禁。

## 下一前沿

当前仍为 S1，五项操作的生产验收尚未整体闭合。当前唯一原型为 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`、已接受 `cce686e4634ca3c523e4c8aeffd95752f1a04c3a`；此前凭据区、空态、手机重排、完整地址和拒绝态接受继续有效，不建立副本、不改写历史日期。

已交付连接目录、服务端搜索分页、五厂商表单、详情及已验收页面状态。当前前端仍为 `569eb54dc66fb8cbd97ebf70076824cbf77068b2`，三后端已更新至 `5f489fcffe106170ff21dd12751e91f45ec153c7`；[后续执行证据](evidence/WP-0019-S1-page-states-and-ark-diagnostics-20261019.md#后续执行)分别绑定镜像与源码。七服务／四入口通过；前端、PostgreSQL、Redis、MinIO 容器未替换。

上一轮两页七态／三尺寸／实际 200% 共 84 组、完整表单 66 组、840 次键盘、66 次必填拒绝、十二项恢复及正式十项只读通过。原始 12 项真实空候选行结构差异和 167 项文案／换行差异保留，按原证据精确归因。验收器 `f507ee10f8cdb1fabd579c490b9494213bf69e0c` 保持 32／36／44px 分类及遮挡、裁切断言。本轮前端、原型源码及前端容器未变，未重跑页面矩阵。真实未筛选空目录仍未执行，现有数据保留；受控 disabled 不冒充生产暂停。“平台部署”和 M8 导航仍在责任切片验收。

账号继续使用 Git 忽略的普通 0600 `pwd.txt`；已找到仓库登记的既有受保护推理 Key 来源，无需重新提供密码或密钥。配合 `AccessKey.txt`，正式表单新建方舟连接 `2112058351855599618`，四项创建验收及三用途密文槽计数通过。首次发现暴露“零候选误报成功、领域拒绝且不保存事实”；红绿用例及完整后端 1,133 项通过后部署修复。当前连接为修订 1、DRAFT、未发布，已保存 `VOLCENGINE_ARK_DISCOVERY_NO_MODELS` 失败事实；正式非空合同仍 FAILED，新连接发布未执行。旧连接及其修订 4 发布事实保留。

下一步需人工核对该 AK／SK 所属账号在 cn-beijing、default 项目的自定义接入点及可见范围，用户已回复“尚未核对”。官方 ListEndpoints 省略 ProjectName 时默认查 default，不能从零结果推断所有项目或预置模型均为空。取得项目事实后再决定 S1 内的最小承接；不换动作填充、不创建云端资源制造通过，新发布仍需独立授权。外部 CI 因账户付款／spending limit 未启动；S1 整体未闭合前不进入 S2～S6／WP-0020。

平台／租户概览、普通会员问答及 M6 全量和人工门禁仍由原责任包完成；WP-0019、M6 和实现矩阵完成事实不提升。
