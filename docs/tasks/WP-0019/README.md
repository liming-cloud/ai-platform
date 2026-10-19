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

[写入可用性决定与源码验证](evidence/WP-0019-S1-write-availability-source-20261017.md)绑定产品候选 `e0224fa9847fdaf297b3ad896e37ae7796e29a50`：348 套件／1,132 项后端、310 项前端，表单 60、partial／disabled 30、分页 37 项通过。三项人工决定已登记 ADR-0098；两候选镜像构建成功，两后端构建因 Docker Desktop 不可用以 EOF 结束，尚未替换运行服务。最新部署、完整表单及 disabled 生产验收未执行；原始 66 组和上一轮辅助读取证据从本报告恢复。

2026-10-19 验收工具维护已绑定源码 `14914427775415a0b1686a7b312df4394a139d07`：三个源码浏览器入口只清除本轮 `.vite`，先关闭资源再清理，异常启动／未捕获异常／显式退出同步兜底，SIGINT／SIGTERM／SIGHUP 有界退出；截图、日志和验收证据保留。12 项旧实现反例真实失败，最终 21 项退出测试及正式入口 29 项 Python／43 项 Node 通过；真实 Chrome 表单 60、分页 37、partial／disabled 30 项通过，四次运行（含首次失败）均无缓存残留，原有 72 项证据摘要一致。首次分页导航失败未进入断言，只增加只读观测后通过，失败证据保留；SIGKILL／断电不在用户态清理保证内。复验命令为 `make work-package-acceptance-test` 及三个 `scripts/quality/platform-connection-{form,pagination,partial}.browser.cjs`；日志在 `/private/tmp/wp0019-cache-cleanup-20261019-d8_wd8i8`，`cache-evidence.json` SHA-256 为 `b32150c9457447a13505a150646fec1c12d7b1ce58a499a40b3763254dbfdcc7`。本项只闭合验收工具缺陷，不提升以下生产验收状态。

## 下一前沿

当前仍为 S1，五项操作的生产验收尚未整体闭合。当前唯一原型为 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`、已接受 `cce686e4634ca3c523e4c8aeffd95752f1a04c3a`；此前凭据区、空态、手机重排、完整地址和拒绝态接受继续有效，不建立副本、不改写历史日期。

已交付的连接目录、服务端搜索分页、五厂商表单、详情及 partial 等原范围保留；最近确认运行前端为 `4e47d97ed3e330450f841a89c16c407d3a961ba4`，后端为 `7906451e3f2e04a03e098e601b37425e29857cb5`。本轮构建期间 Docker Desktop 不可用、18080 健康检查失败，当前运行健康待恢复确认；未执行服务替换，不以 HEAD 代替实际部署。

三项决定已接受且源码验证完成，不再重复询问：候选 `e0224fa9847fdaf297b3ad896e37ae7796e29a50` 实现可信 writesAvailable 和四类 S1 写命令统一拒绝；三处说明与错误色候选按 ADR-0098 承接。Docker 恢复后先核对七服务和四镜像，再顺序补构建、部署、真实暂停／恢复及最新完整表单验收。受保护观察程序已准备但未执行，不降低断言或制造业务数据。ADR-0058 底线由 ADR-0095／0098 继续引用，直接 ADR 保持 12 项。

新方舟控制面仍缺匹配的真实认证配置，既有连接未配置独立 AK/SK。旧发现 135 个候选已于 2026-10-15T03:55:24.971802Z 到期，历史成功不作为当前有效验证；新协议最小 ListEndpoints 合同尚未执行。取得匹配材料及对应调用授权后再验，不索取新的平台密码，不导出密钥，不重复旧发现／修订 4 发布。S1 功能和原型门禁整体闭合后才进入 S2～S6；全部供应任务闭合后才进入 WP-0020。

平台／租户概览、普通会员问答及 M6 全量和人工门禁仍由原责任包完成；WP-0019、M6 和实现矩阵完成事实不提升。
