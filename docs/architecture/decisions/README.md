# 决策索引

> 本表由 `python3 scripts/project_governance.py sync-decisions` 从 ADR 元数据生成。

原状态是决策时点记录；被后续 ADR 取代的条款不是现行规范。当前范围从 docs/README.md 的主题入口读取。

| 编号 | 原状态 | 决策 | ADR |
| --- | --- | --- | --- |
| ADR-0001 | 已接受 | 绿地重建 | `docs/architecture/decisions/ADR-0001-greenfield-replacement.md` |
| ADR-0002 | 已接受 | DDD 模块与三个运行进程 | `docs/architecture/decisions/ADR-0002-modular-foundation-runtime.md` |
| ADR-0003 | 已接受 | 能力驱动的 Spring AI 模型运行时 | `docs/architecture/decisions/ADR-0003-capability-driven-model-runtime.md` |
| ADR-0004 | 已接受 | 仓库项目控制面 | `docs/architecture/decisions/ADR-0004-repository-project-control-plane.md` |
| ADR-0005 | 已接受 | 任务导向的 Fluent UI | `docs/architecture/decisions/ADR-0005-task-first-fluent-ui.md` |
| ADR-0006 | 已接受 | 正式 GA 技术基线 | `docs/architecture/decisions/ADR-0006-stable-ga-technology-baseline.md` |
| ADR-0007 | 已接受 | 在设计分支清理旧实现 | `docs/architecture/decisions/ADR-0007-clean-design-branch.md` |
| ADR-0008 | 已接受 | 可扩展仓库与模块目录 | `docs/architecture/decisions/ADR-0008-scalable-repository-layout.md` |
| ADR-0009 | 已接受 | Java 命名空间 | `docs/architecture/decisions/ADR-0009-java-namespace.md` |
| ADR-0010 | 已接受 | 模型供应商密钥与出站访问边界 | `docs/architecture/decisions/ADR-0010-secret-and-egress-boundary.md` |
| ADR-0011 | 已接受 | 租户认证与持久化隔离边界 | `docs/architecture/decisions/ADR-0011-tenant-auth-and-persistence-boundary.md` |
| ADR-0012 | 已接受 | 演示人员密码摘要与验证边界 | `docs/architecture/decisions/ADR-0012-password-hashing-boundary.md` |
| ADR-0013 | 已接受 | 知识空间租户、幂等和配额事务边界 | `docs/architecture/decisions/ADR-0013-knowledge-space-transaction-boundary.md` |
| ADR-0014 | 已接受 | 文本内容暂存、对象写入与发布一致性 | `docs/architecture/decisions/ADR-0014-text-content-object-publication.md` |
| ADR-0015 | 已接受 | 版本化向量索引生命周期 | `docs/architecture/decisions/ADR-0015-versioned-vector-index-lifecycle.md` |
| ADR-0016 | 已接受 | 动态模型运行时、不可变助手版本与可信引用回答 | `docs/architecture/decisions/ADR-0016-model-runtime-assistant-and-trusted-answer.md` |
| ADR-0017 | 已接受 | 查询渠道助手目录与浏览器状态边界 | `docs/architecture/decisions/ADR-0017-query-channel-catalog-and-browser-state.md` |
| ADR-0018 | 已接受 | 演示查询配额与运营事实投影 | `docs/architecture/decisions/ADR-0018-demo-query-quota-and-operations-projection.md` |
| ADR-0019 | 已接受 | 查询感知的候选扩召回与混合重排 | `docs/architecture/decisions/ADR-0019-query-aware-candidate-selection.md` |
| ADR-0020 | 已接受 | 演示数据备份与隔离恢复契约 | `docs/architecture/decisions/ADR-0020-demo-backup-and-isolated-recovery.md` |
| ADR-0021 | 已接受 | 后端架构质量重置 | `docs/architecture/decisions/ADR-0021-backend-architecture-quality-reset.md` |
| ADR-0022 | 已接受 | 经典领域驱动分层、自动装配与分页契约 | `docs/architecture/decisions/ADR-0022-classic-ddd-autoconfiguration-and-page-contract.md` |
| ADR-0023 | 已接受 | 分层模型配置治理与数据库密文凭据 | `docs/architecture/decisions/ADR-0023-model-configuration-governance-and-encrypted-credentials.md` |
| ADR-0024 | 已接受 | M2 真实模型验证切换至火山方舟 | `docs/architecture/decisions/ADR-0024-volcengine-ark-m2-provider-transition.md` |
| ADR-0025 | 已接受 | M2 火山方舟 Coding Plan 受控模型目录 | `docs/architecture/decisions/ADR-0025-volcengine-coding-plan-m2-model-catalog.md` |
| ADR-0026 | 已接受 | M2 方舟标准推理与多模态嵌入协议 | `docs/architecture/decisions/ADR-0026-volcengine-ark-standard-inference-multimodal-embedding.md` |
| ADR-0027 | 已接受 | 平台管理范围与三前端应用外壳 | `docs/architecture/decisions/ADR-0027-platform-management-scope-and-frontend-shells.md` |
| ADR-0028 | 已接受 | 受治理数据源、同步产物与质量生命周期 | `docs/architecture/decisions/ADR-0028-governed-data-source-lifecycle.md` |
| ADR-0029 | 已接受 | 已发布同步产物与索引来源快照 | `docs/architecture/decisions/ADR-0029-published-sync-artifact-index-snapshots.md` |
| ADR-0030 | 已接受 | M3 对象存储与结构化数据集首版接入边界 | `docs/architecture/decisions/ADR-0030-m3-object-storage-and-structured-dataset-ingress.md` |
| ADR-0031 | 已接受 | 领域聚合所有权与操作边界 | `docs/architecture/decisions/ADR-0031-domain-aggregate-operation-boundary.md` |
| ADR-0032 | 已接受 | M3 结构化数据集 CSV 同步与质量规则 | `docs/architecture/decisions/ADR-0032-m3-structured-dataset-csv-synchronization.md` |
| ADR-0033 | 已接受 | 登录状态必须由令牌恢复 | `docs/architecture/decisions/ADR-0033-token-derived-authentication-context.md` |
| ADR-0034 | 已接受 | M3 平台对象存储同步与私有产物规则 | `docs/architecture/decisions/ADR-0034-m3-platform-object-storage-synchronization.md` |
| ADR-0035 | 已接受 | 来源快照作用域的索引分块标识 | `docs/architecture/decisions/ADR-0035-source-scoped-index-chunk-identity.md` |
| ADR-0036 | 已接受 | 助手草稿测试与查询反馈边界 | `docs/architecture/decisions/ADR-0036-assistant-draft-test-and-query-feedback-boundary.md` |
| ADR-0037 | 已接受 | 助手可信回答执行边界 | `docs/architecture/decisions/ADR-0037-assistant-trusted-answer-execution-boundary.md` |
| ADR-0038 | 已接受 | 查询诊断最小事实投影 | `docs/architecture/decisions/ADR-0038-query-diagnostic-fact-projection.md` |
| ADR-0039 | 已接受 | 查询诊断事实可得性补充 | `docs/architecture/decisions/ADR-0039-query-diagnostic-fact-availability.md` |
| ADR-0040 | 已接受 | 冻结聊天模型路由与回退调用轨迹 | `docs/architecture/decisions/ADR-0040-frozen-model-route-and-fallback-trace.md` |
| ADR-0041 | 已接受 | 平台请求上下文与租户生命周期边界 | `docs/architecture/decisions/ADR-0041-platform-request-context-and-tenant-lifecycle.md` |
| ADR-0042 | 已接受 | 初始租户管理员的身份凭据边界 | `docs/architecture/decisions/ADR-0042-initial-tenant-administrator-identity.md` |
| ADR-0043 | 已接受 | 平台租户治理授权与启动边界 | `docs/architecture/decisions/ADR-0043-platform-tenant-governance-grant.md` |
| ADR-0044 | 已接受 | 历史租户初始管理员的显式回填边界 | `docs/architecture/decisions/ADR-0044-historical-tenant-initial-administrator-backfill.md` |
| ADR-0045 | 已接受 | 历史租户生命周期状态的合法回填 | `docs/architecture/decisions/ADR-0045-historical-tenant-lifecycle-state-reconciliation.md` |
| ADR-0046 | 已接受 | 租户生命周期认证切换 | `docs/architecture/decisions/ADR-0046-tenant-lifecycle-authentication-cutover.md` |
| ADR-0047 | 已接受 | 新租户初始管理员选择契约 | `docs/architecture/decisions/ADR-0047-initial-tenant-administrator-selection-contract.md` |
| ADR-0048 | 已接受 | 统一浏览器登录入口与角色化工作区 | `docs/architecture/decisions/ADR-0048-unified-browser-login-and-role-workspace.md` |
| ADR-0049 | 已接受 | 平台租户生命周期读取投影与角色验收夹具 | `docs/architecture/decisions/ADR-0049-platform-lifecycle-read-projection.md` |
| ADR-0050 | 已接受 | 冻结评测运行与发布决定边界 | `docs/architecture/decisions/ADR-0050-evaluation-run-and-release-decision.md` |
| ADR-0051 | 已接受（方案 A） | 私有评测数据集与首批质量发布门禁 | `docs/architecture/decisions/ADR-0051-private-evaluation-dataset-and-quality-gate.md` |
| ADR-0052 | 已接受（方案 A） | 评测与发布制度的租户授权边界 | `docs/architecture/decisions/ADR-0052-evaluation-and-release-tenant-authorization.md` |
| ADR-0053 | 已接受（方案 A） | 私有评测数据集条目编码与服务端校验 | `docs/architecture/decisions/ADR-0053-private-evaluation-dataset-entry-encoding.md` |
| ADR-0054 | 已接受 | 评测运行的受控模型路由摘要 | `docs/architecture/decisions/ADR-0054-evaluation-model-route-fingerprint.md` |
| ADR-0055 | 已接受（方案 A） | 私有评测执行的明文内存边界 | `docs/architecture/decisions/ADR-0055-private-evaluation-execution-plaintext-boundary.md` |
| ADR-0056 | 已接受（方案 A） | 评测期望证据稳定标识 | `docs/architecture/decisions/ADR-0056-evaluation-evidence-stable-identifier.md` |
| ADR-0057 | 已接受 | 企业账户、角色权限与导航能力投影 | `docs/architecture/decisions/ADR-0057-enterprise-access-control-and-navigation-projection.md` |
| ADR-0058 | 已接受 | 厂商连接、模型探测与可用性供应链 | `docs/architecture/decisions/ADR-0058-provider-and-model-readiness-supply-chain.md` |
| ADR-0059 | 已接受 | 统一企业控制台、服务端分页与注意力保护 | `docs/architecture/decisions/ADR-0059-unified-console-pagination-and-attention-protection.md` |
| ADR-0060 | 已接受 | 厂商连接凭据所有权与公开写入契约 | `docs/architecture/decisions/ADR-0060-provider-connection-credential-ownership-and-public-write-contract.md` |
| ADR-0061 | 已接受 | 平台账户生命周期与平台角色治理 | `docs/architecture/decisions/ADR-0061-platform-account-lifecycle-and-role-governance.md` |
| ADR-0062 | 已接受 | 租户历史直接权限退役与显式角色迁移 | `docs/architecture/decisions/ADR-0062-tenant-direct-permission-retirement.md` |
| ADR-0063 | 已接受 | M6 本地身份基线重置与浏览器验收边界 | `docs/architecture/decisions/ADR-0063-m6-local-identity-baseline-and-browser-evidence.md` |
| ADR-0064 | 已接受 | 本地空数据库的租户生命周期认证切换 | `docs/architecture/decisions/ADR-0064-local-empty-database-lifecycle-authentication-cutover.md` |
| ADR-0065 | 已接受 | 平台模型目录管理与已发布部署快照绑定 | `docs/architecture/decisions/ADR-0065-platform-model-catalog-administration-and-deployment-snapshot.md` |
| ADR-0066 | 已接受 | 平台范围模型供应链 | `docs/architecture/decisions/ADR-0066-platform-scoped-model-supply.md` |
| ADR-0067 | 已接受 | 三角色产品边界与能力投影 | `docs/architecture/decisions/ADR-0067-three-role-product-and-capability-projection.md` |
| ADR-0068 | 已接受 | 运营投影与用户信息边界 | `docs/architecture/decisions/ADR-0068-operations-projection-and-user-message-boundary.md` |
| ADR-0069 | 已接受 | 本地固定平台管理员初始化凭据 | `docs/architecture/decisions/ADR-0069-local-platform-administrator-initialization.md` |
| ADR-0070 | 已接受 | 平台供应商权威模型发现与管理员能力确认 | `docs/architecture/decisions/ADR-0070-authoritative-platform-model-discovery.md` |
| ADR-0071 | 已接受 | 本地平台管理员凭据与数据完全重制 | `docs/architecture/decisions/ADR-0071-local-platform-administrator-full-reset.md` |
| ADR-0072 | 已接受 | 租户知识库所有权、工作区边界与可恢复生命周期 | `docs/architecture/decisions/ADR-0072-tenant-owned-knowledge-base-workspace.md` |
| ADR-0073 | 已接受 | 平台拥有租户管理员生命周期与人员身份互斥 | `docs/architecture/decisions/ADR-0073-platform-owned-tenant-administrator-governance.md` |
| ADR-0074 | 已接受 | M6 可信 RAG 产品与研发基线重建 | `docs/architecture/decisions/ADR-0074-m6-trusted-rag-product-and-engineering-baseline.md` |
| ADR-0075 | 已接受 | 租户功能上限与普通成员对话功能控制 | `docs/architecture/decisions/ADR-0075-tenant-and-member-feature-control.md` |
| ADR-0076 | 已接受 | 租户配额与登录安全配置原型 | `docs/architecture/decisions/ADR-0076-configurable-tenant-quota-prototype.md` |
| ADR-0077 | 已接受 | OpenDesign 原型事实源与截图存储边界 | `docs/architecture/decisions/ADR-0077-opendesign-prototype-source-boundary.md` |
| ADR-0078 | 已接受 | 平台账户与租户开通职责拆分原型 | `docs/architecture/decisions/ADR-0078-account-entry-and-tenant-setup-prototype.md` |
| ADR-0079 | 已接受 | M6 原型承接、顺排实施与真实会员验收 | `docs/architecture/decisions/ADR-0079-m6-sequential-delivery-and-prototype-acceptance.md` |
| ADR-0080 | 已接受 | 租户与初始管理员原子开户 | `docs/architecture/decisions/ADR-0080-atomic-tenant-onboarding.md` |
| ADR-0081 | 已接受 | 统一文档控制面与可验证历史退役 | `docs/architecture/decisions/ADR-0081-document-control-plane-and-history-retirement.md` |
| ADR-0082 | 已接受。依据用户明确要求执行《后端上下文依赖边界整改计划》；仅决定本次编译边界，不改变产品范围和里程碑结论 | 后端上下文与扩展的编译依赖边界 | `docs/architecture/decisions/ADR-0082-backend-context-dependency-boundary.md` |
| ADR-0083 | 已接受 | 租户管理员设置页的资格与凭据边界 | `docs/architecture/decisions/ADR-0083-tenant-administrator-settings-scope.md` |
| ADR-0084 | 已接受。用户于 2026-09-28 选择在本切片聚合现有来源的只读用量，允许下调至不低于核实用量；既有 ADR-0079、ADR-0080 和固定 OpenDesign 原型决定其余产品边界 | 租户资源配额独立编辑与已用量下限 | `docs/architecture/decisions/ADR-0084-tenant-quota-settings-and-observed-usage.md` |
| ADR-0085 | 已接受。2026-09-28。依据已接受的 ADR-0075、ADR-0079、WP-0017 和 OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 的固定提交 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`；本决策只冻结这些输入在生产中的保存和执行边界 | 租户可用功能的独立配置与即时撤销 | `docs/architecture/decisions/ADR-0085-tenant-feature-availability-and-revocation.md` |
| ADR-0086 | 已接受。2026-09-28。依据已接受的 ADR-0048、ADR-0079、WP-0017 和 OpenDesign 固定提交 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`；本决策把既定产品值落到生产登录与保存边界，不改变平台登录策略 | 租户登录策略独立保存与执行 | `docs/architecture/decisions/ADR-0086-tenant-login-security-policy.md` |
| ADR-0087 | 已接受。2026-09-29。依据用户要求完成 WP-0017 的完整账户操作、已验收 OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 固定提交 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`，以及 ADR-0048、ADR-0061、ADR-0073、ADR-0078、ADR-0079、ADR-0080、ADR-0083。本决策只冻结 `platform-accounts:0–4`；`platform-roles:0` 另行交付 | 平台管理员账户目录与完整账户操作 | `docs/architecture/decisions/ADR-0087-platform-administrator-account-operations.md` |
| ADR-0088 | 已接受。2026-10-01。依据用户继续推进 WP-0017 的授权、已接受 ADR-0067、ADR-0075、ADR-0079 与 ADR-0087，以及 OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 固定提交 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`。本决策承接既定角色和原型，不增加角色、权限或持久化结构 | 平台固定角色只读视图与账户导航 | `docs/architecture/decisions/ADR-0088-platform-fixed-role-view-and-account-navigation.md` |
