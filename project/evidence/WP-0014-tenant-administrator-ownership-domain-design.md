# WP-0014 租户管理员所有权与人员范围资格领域设计

## 结果

本切片完成 ADR-0073 提议和 M6 规范领域模型，未修改生产代码、数据库、HTTP 或前端。推荐方案为：

- 平台管理员独占 `TENANT_ADMINISTRATOR` 的建立、分配、替换、停用和恢复；
- 租户管理员只治理当前租户普通成员；
- 活动平台管理员绑定与启用租户成员资格互斥，历史撤销／停用事实可共存；
- 候选读取减少无效选择，所有写命令仍在共同主体锁下复核资格；
- 多租户成员资格继续允许，租户角色按租户独立，平台角色不产生租户内容能力。

ADR-0073 仍为“提议，等待人工接受”。在接受证据登记前，不得修改生产权限语义。

## 当前实现事实

| 路径 | 当前事实 | 缺口 |
| --- | --- | --- |
| 新租户建立 | `TenantProvisioningService` 锁定活动 `SubjectIdentity`，原子建立租户、初始管理员成员资格和管理员角色。 | 未检查目标是否拥有活动平台角色。 |
| 普通成员候选 | `TenantMemberCandidateMapper.xml` 只排除停用账户和当前租户已有成员。 | 活动平台管理员仍会进入候选。 |
| 普通成员建立 | `TenantMemberEstablishmentApplicationService` 锁定主体并检查同租户成员资格。 | 未复核活动平台角色。 |
| 租户成员状态 | `TenantMemberStatusApplicationService` 只检查 `access:manage` 和最后访问管理员。 | 可停用、启用同级租户管理员。 |
| 租户角色绑定 | `TenantRoleBindingApplicationService` 允许绑定所有已发布租户角色。 | 租户管理员可提升普通用户为管理员。 |
| 平台角色绑定 | `PlatformRoleBindingApplicationService` 锁定主体并检查重复角色。 | 未检查目标是否已有启用租户成员资格。 |
| 初始管理员候选 | `JdbcPlatformAccountCandidateDirectory` 已排除活动平台管理员。 | 过滤使用 Java 内联 SQL，且命令侧仍未复核。 |

三条关键写路径都以 `organization_access.subject_identity ... FOR UPDATE` 或可扩展为同一主体锁为起点，
因此跨范围互斥可在现有 `interfaces -> application -> domain`、`infrastructure -> application/domain`
边界中实现，不需要把用户、角色和权限拆成另一个平行后端或把聚合合并为巨型账户模型。

## 代码图谱与影响分析

编码前使用代码图谱探索以下关键符号和路径：

- `TenantProvisioningService`、`TenantProvisioningStore`、`TenantProvisioning`；
- `TenantMemberCandidateDirectory`、`TenantMemberCandidateMapper`；
- `TenantMemberEstablishmentApplicationService`、`TenantMemberEstablishmentStore`；
- `TenantMemberStatusApplicationService`、`TenantRoleBindingApplicationService`；
- `PlatformRoleBindingApplicationService`、`PlatformRoleBindingStore`、`PlatformRoleBinding`；
- `SubjectIdentity`、`TenantMembership`、`PlatformRoleBinding`。

图谱确认平台初始管理员候选已排除活动平台管理员，但普通成员候选、成员命令和平台角色命令没有形成
双向互斥；租户角色和状态命令只依赖 `access:manage`，确实允许同级管理员治理。非局部影响集中在
organization-access 的候选投影、四条写用例、三个持久化端口、会话撤销协作和对应 HTTP／SPA，不需要
改变 knowledge-base、model-hub 或 query-runtime 的聚合所有权。该结论使方案从“新增账户类型或拆服务”
收敛为“共同主体锁下的跨聚合资格领域服务”。

## 方案比较与决策边界

| 方案 | 收益 | 代价／风险 | 结论 |
| --- | --- | --- | --- |
| A：平台拥有管理员，活动范围互斥 | 最小权限清晰；平台账户不进入租户内容面；可复用现有主体锁。 | 修订 ADR-0072 的双重活动身份语义；需迁移预检。 | 推荐，等待人工接受。 |
| B：允许双范围身份，只切换会话 | 代码变化最少。 | 保留权限聚合、候选误配和产品解释成本。 | 不推荐。 |
| C：新增不可变账户类型 | 数据库可直接分类。 | 与角色／成员事实重复，转换不可逆。 | 不推荐。 |
| D：租户管理员治理同级 | 延续现状。 | 与人工验收判定相反，存在同级提权和停用风险。 | 拒绝。 |

若方案 A 被接受，它只在双重活动身份处取代 ADR-0072 第 2 条；知识库仍由租户拥有，平台管理员仍无
正文能力。`SubjectIdentity` 继续是唯一凭据根，撤销平台角色或停用租户成员后可以经过显式命令转换范围，
不创建新的账户种类。

## 后续红绿实施顺序

1. 纯领域测试固定人员范围互斥、平台拥有管理员、租户只治理普通成员和最后管理员规则。
2. 扩展端口，在统一主体锁后读取对侧活动事实；真实 PostgreSQL 证明并发平台绑定／成员启用只有一侧成功。
3. 收紧普通成员、初始管理员和平台角色候选，并证明分页列表与计数共用资格谓词。
4. 建立平台范围租户管理员目录与生命周期 HTTP；收紧租户成员和角色 HTTP，完成会话撤销失败回滚。
5. 重构相应 Fluent UI 任务并使用平台管理员、租户管理员和普通用户完成 Compose／Chrome 六项矩阵。

## 验证

本切片只产生设计与控制面文件，验证命令为：

```bash
git diff --check
make project-verify
```

阶段结果校验不适用：本次未产生 `StageRunResult` JSON 或等价阶段运行产物。

## 工程助手插件证据

- 当前运行环境：Codex 桌面本地仓库会话。
- 能力来源：当前环境已安装的 `engineering-assistant` 插件限定技能。
- 路由技能：`engineering-assistant:domain-modeling`。
- 显式加载：`SKILL.md`、`strategic-modeling.md`、`tactical-modeling.md`、`model-artifact.md`、
  `scenario-traceability.md`、`integration-consistency.md`、`adr-format.md`。
- 使用流程：先分离代码事实与人工选择，再更新唯一 M6 规范领域模型，最后以提议 ADR 承载难以逆转的
  角色所有权和跨范围互斥选择。
- 阶段结果校验：不适用，原因如上。
