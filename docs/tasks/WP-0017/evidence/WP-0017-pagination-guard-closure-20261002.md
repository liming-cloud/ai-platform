# WP-0017：分页守卫漏检整改与精确债务收口

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0017 |
| 验证范围 | 上下文 Mapper XML 普通列表分页守卫、精确存量及责任登记；不验证新业务功能 |
| 实施／盘点基线 | 3f53adf197da385389aa93e83a0685cb39eb212c |
| 验证提交 | bfa6faec4d2af2c9187f5951ef19e65f018cfaa9 |
| 依据／日期 | ADR-0022、ADR-0079、ADR-0081；2026-10-02 |
| 结论 | 守卫漏检退出条件闭合；七条既有分页债务仍待各所属切片整改，WP-0017 整包及 M6 未完成 |

## 缺陷、反例与修复

旧检测同时依赖语句名含 `Page/After` 与绑定名 `limit`，且未在 `</select>` 终止，造成命名漏检和跨语句误计。真实原 `PlatformAccountMapper#list` 可从 `6d8e752c29bbd0f49b8ea7465d6ff056ac889202:backend/contexts/organization-access/src/main/resources/mapper/organization-access/PlatformAccountMapper.xml` 恢复，包含 `OFFSET #{offset} LIMIT #{limit}`；完整文件 SHA-256 为 `fe8219e221e19ecfdad4dd65c750b83bbe855f612294ded2f8bc9acba63354a1`。该业务路径此前已改为框架分页，本轮没有恢复旧实现。

首轮真实测试出现六项失败：`list`、其他方法／参数名、FETCH 三个子例，跨语句误计、内层投影／注释误计、同数量替换放行。修复后又用独立反例发现“参数名本身被当作 SQL 关键字”，一项失败后修正。固定 diff 评审进一步发现包装子查询漏检和 PostgreSQL 美元字面量误报，四个子例先失败再修正。最终全部转绿，没有删除反例或放宽已有断言。

按 XML 元素独立读取 `select`，展开当前 Mapper 的 `include` 片段；解析失败、缺失或循环引用失败关闭。屏蔽注释、字符串、美元字面量和绑定占位符后检查 `LIMIT/OFFSET/FETCH`，不依赖命名。普通括号、子查询和 CTE 的分页仍计入；只排除关联 `JOIN LATERAL` 的固定单行投影，以及已登记的 `EVENT_REPLAY`／`SKIP_LOCKED` 内部协议。

沿用 `schemaVersion=1` 和 `capture/verify` CLI，增加 `manualListLimitStatements` 精确身份清单，接入既有递减比较；删除旧债务允许，新语句即使替换旧语句且数量不增也被拒绝。数量预算由旧 **16 收紧为 7**，其他预算逐项不变。旧扫描输出 **3** 只证明旧正则匹配数，不能当作真实存量或本轮减少债务的依据。

## 当前存量与责任

基线及验证提交中业务源码逐字相同。扫描覆盖 `backend/contexts/*/src/main/resources/mapper/**/*.xml`：**43 份 XML、104 条 select、9 条有界查询，其中 7 条普通列表债务、2 条合法非列表**。七条 Mapper 都返回 `List` 并显式接收 offset 和 limit/pageSize，调用端已有页码与总数，但未用 `IPage`。`SECURITY_PROJECTION` 只授权复杂安全投影，不能豁免手工分页。

下表 XML 全路径为 `backend/contexts/<上下文>/src/main/resources/mapper/<上下文>/<Mapper>.xml`；机器基线保存完整 `路径#statementId`。

| 上下文／Mapper#statementId | 当前调用 | 责任任务 |
| --- | --- | --- |
| organization-access／PlatformTenantAdministratorMapper#selectAdministratorPage | PostgresPlatformTenantAdministratorDirectory.page | WP-0017 |
| organization-access／PlatformTenantAdministratorMapper#selectCandidatePage | PostgresPlatformTenantAdministratorDirectory.candidates | WP-0017 |
| organization-access／TenantMemberDirectoryMapper#selectPage | PostgresTenantMemberDirectory.page | WP-0018 |
| organization-access／TenantCustomRoleDirectoryMapper#selectPage | PostgresTenantCustomRoleDirectory.page | WP-0018，既有基础设施债务 |
| model-runtime／ControlledModelCatalogEntryMapper#selectPublishedAvailablePage | PostgresModelConfigurationRepository.findPublishedCatalogPage | WP-0019 |
| model-runtime／ModelConfigurationMapper#selectVisiblePage | PostgresModelConfigurationRepository.findVisiblePage | WP-0019 |
| knowledge-data／GovernedFileDataSourceReadMapper#selectPage | PostgresGovernedFileDataSourceReadAdapter.list | WP-0020 |

共同退出条件：在 infrastructure 改用 `Page/IPage`，删除目录的手工分页，保留分页前权限／租户筛选、固定排序和同范围总数；以各仓储／Mapper 的页边界、隔离及过滤反例验证，再递减精确基线。当前包管理员目录还须保留搜索、状态、候选资格与生产接口行为，实施后独立部署回归；本轮没有整改这七条业务实现。WP-0018 自定义角色项不授权新增或启用范围外功能；若退役，须先核实调用者、替代和契约依据。

两个合法查询分别为 `knowledge-data/IndexVersionMapper#claimableJob`（事务内 `FOR UPDATE SKIP LOCKED LIMIT 1` 领取一个租约）及 `trusted-query/QueryRunEventMapper#findAfter`（按租户、运行、单调事件序号有界续传）。二者不迁移为页码分页。文件来源目录内部还有最新发布投影的 `LATERAL LIMIT 1`，与外层目录分页属于同一 statement，只计一条债务；当前 `<sql>` 片段没有隐藏分页，片段检测另有测试覆盖。此范围没有向量候选查询；Java JDBC 内联分页是另一口径，现有 15 文件内联 SQL 门禁不变，不能据本盘点声称全后端无债务。

## 历史基线处置纠错

首次 `make project-status` 真实拒绝“保留历史文件正文被改写：scripts/quality/baselines/WP-0008-backend.json”。原因是 GOV-0001 将仍需维护的递减基线登记成冻结原文的 `move`。依 ADR-0081 及文档控制规范，核实 `e7a1d673229acf8c7932dcae85699e517f2adab5:project/architecture-debt/WP-0008-backend.json` 存在且原摘要 `b2d4cd2c30a092780f9b11b249ff108fac611e1ac7104ab489349b3661382262` 一致后，仅将该项处置改为 `merge`，现行目标不变；与同目录复杂 SQL 可维护清单一致。

历史来源提交、原路径、摘要、650 项历史资料及历史任务绑定均不改写，校验器代码没有放宽。旧基线可用 `git show e7a1d673229acf8c7932dcae85699e517f2adab5:project/architecture-debt/WP-0008-backend.json` 精确恢复；原迁移仍由 Git 历史追溯。修复后全部历史与治理校验通过。

## 验证、原型与交付边界

| 验证 | 结果 |
| --- | --- |
| 后端架构守卫单测 | 41/41；含命名、参数、语句边界、片段、包装、字面量、合法协议及身份替换／移除 |
| `make architecture-guard` | 48/48，后端与前端实际门禁通过；分页存量 7，依赖边界违规 0 |
| `make project-status`、`make project-verify` | M6／WP-0017；69 项、29 任务、650 项历史定位通过 |
| `make decisions-sync`、`make resume-context` | 唯一索引及恢复预算通过；产品前沿与当前执行均不切换 |
| 固定 diff／工作区保护 | 独立评审无剩余阻断；fixture、业务源码、部署、实现矩阵和原型文件差异 0；无截图 |

只修改质量工具与文字控制面，不构建／部署、不访问数据库、不运行会创建业务对象的验收。生产功能和视觉结论仍由[三入口部署证据](WP-0017-login-entry-navigation-closure-20261001.md)及各已闭合切片证明，本轮不宣称重新完成浏览器或原型验收。前端运行 `ee34520f3f00098634fd749992aa533c81eda139`、控制面后端 `90a28e1bed18fbbda628cb7831ee84982f847adb`、查询进程 `ef11d6aad0ffb8d8a656d5a17a3477d93b95a5c8` 的记录保持独立，不用本轮治理提交替代。

OpenDesign 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 保持唯一权威；原页／台账固定 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`，共享移动布局 `47c4f6f31823617633ce0bac98074208781f5565`，目录修订 `d36c7f46d0187c0421d4989c12f601d5c190d73f`，原项目链接见上述部署证据。没有修改原型或新增一致性例外。

CodeGraph 先探索守卫区域；首次广义查询主要返回业务 Java，未提供目标检测正文，随后按已知精确脚本补读。`backend_architecture_guard.scan` 影响分析定位到 `scan/capture/verify/cli`，据此保持 CLI 及显式写入边界；独立盘点用 Mapper 到目录适配器的调用关系区分普通分页和内部协议。控制面冲突另追踪 `HistoryStore.verify`，只纠正单项处置，不改历史校验规则。图谱用于定位和影响，不代替上述测试。

环境为 Codex 本地工程助手 `2.2.0-rc.1+codex.local-20260929-051321`，显式加载 `engineering-assistant:wayfinder`、`tdd`、`code-review`；使用根 AGENTS、Charter、ROADMAP、STATE、WP-0017 及其 12 项 ADR，纠错追加 ADR-0081／文档控制规范。内联工作未生成 Runtime／StageRunResult，阶段结果校验不适用。

## 外部日志定位

日志只在 0700 的 `/private/tmp/wp0017-pagination-31ojw_vj/`，未读取凭据或保存秘密。主要日志及机器盘点摘要：

| 产物 | SHA-256 |
| --- | --- |
| red.log | `078eab09b9e1b18cc67fa2bec4183eced0820416c8eb2cc2c017ce79b1db9df1` |
| parameter-red.log | `578467e0b76e12969425611118b48bdbee06a15b10a8f2bcf4a1af0ceae9c119` |
| review-red.log | `539d679af99b410e7a7f8d0daae30a5527538bdeb789b951138bf1135a0502d4` |
| green.log | `f5ce8ad1de025c21ccccc5c6e4a3fbce08e978524b37a81efbf1114c6aef5d9c` |
| architecture.log | `b0ac70b4729fe9f6967ba697b89249903d19c677aeea16772721cb688ac821d8` |
| scan.json | `71941cfb5c198089baf395fc5166231c420e43d07dcddb724639cfdd3485fdbd` |

最终推送及 CI 以最后交付 SHA 另行核对，不复用上一提交的绿灯。本轮只关闭分页守卫退出条件；矩阵仍为 20/21 操作、11/12 入口已验证。下一独立任务为本包两条管理员目录的框架分页整改；平台概览仍依赖未实施的 M8 事实与目标页，WP-0017／M6 不标完成。
