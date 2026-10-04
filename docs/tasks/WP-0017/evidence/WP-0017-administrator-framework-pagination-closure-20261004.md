# WP-0017：管理员目录框架分页与部署原型一致性收口

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0017 |
| 验证范围 | 管理员目录及分配候选的框架分页、现有管理员页控件、真实部署只读回归；不新增业务操作 |
| 实施基线 | 708de1e1ff908587a3efb699faa1dd6c1052105c |
| 验证提交 | b33cb8e1bc536d71337c3d631df6017df9e28dd1 |
| 后端源码／运行提交 | a23ea28dca8dd32a36f235948a5e867ed1769def |
| 依据／日期 | ADR-0022、ADR-0079、ADR-0083、ADR-0088；2026-10-04 |
| 结论 | 当前独立任务闭合；WP-0017 整包及 M6 未完成 |

## 问题、反例与改动

此前两个 Mapper 返回 `List`，XML 手工拼接 `LIMIT/OFFSET`。将精确递减基线先收紧至 5 后，真实 `make architecture-guard` 拒绝两个原语句及 7 条超预算，构成架构缺陷的红灯；没有用测试编译错误冒充产品失败。新增四项 PostgreSQL 合同回归在改动前后均通过，证明需保留原有行为，而非宣称原分页结果错误。

仅把 `PlatformTenantAdministratorMapper#selectAdministratorPage`、`#selectCandidatePage` 改为 `IPage`，由 `PostgresPlatformTenantAdministratorDirectory` 创建 infrastructure `Page`，使用既有自定义 count statement 和 `getRecords/getTotal`。移除两处手工分页，保持原投影、租户／权限和资格谓词、状态筛选、大小写及字面量搜索、固定排序与同范围总数；分页框架类型没有进入 application。写入、锁、领域、API、数据库结构及会话策略未改动。

代码图谱探索 Mapper、Directory 和既有 `PostgresPlatformAccountStore` 分页路径；Mapper 深度 2 影响分析返回 57 个符号，包含写入存储及自动配置，因而限定为两个读取方法，保留锁与写入接口。新增回归覆盖第一页／第二页／越界总数、搜索后越界及 `%`、`_`、`\` 字面量；候选正例通过隔离 PostgreSQL 测试，未为生产验收创建候选身份。

真实部署与原项目对照先发现表头 16px／12px 内边距、摘要标签和颜色、短按钮、禁用态存在差异；初次修正后又由 390px 及 200% 对照发现移动短按钮内边距和表头辅助技术语义不一致。分别保留失败日志并局部修正为原型的表格 15px、表头 700／8px 内边距、“可用账户”、短按钮及禁用配色；移动表头使用原型的视觉隐藏规则保留五列语义。CodeGraph 的 `TenantAdministratorSettings` 探索将样式限定在现有页面，没有修改共享主题、导航、原型或业务操作语义；停用／恢复管理员资格仍遵守 ADR-0083。

精确基线由 7 递减为 5，仅删除上述两个身份，其他质量预算不变。剩余债务：WP-0018 的成员／自定义角色目录、WP-0019 的可用模型目录／配置目录、WP-0020 的受治理文件来源目录。合法的事件续传和领取任务协议未迁移，不声称全后端无分页债务。历史[守卫证据](WP-0017-pagination-guard-closure-20261002.md)保留其原日期和 7 条存量事实。

## 实际部署与唯一原型

| 进程 | 实际 OCI revision | 实际镜像 |
| --- | --- | --- |
| console-web | b33cb8e1bc536d71337c3d631df6017df9e28dd1 | `sha256:ecf701a7dbcb2e8361405b6396776015e1d330847f7c59e2c5ac9468f12ecf61` |
| control-plane | a23ea28dca8dd32a36f235948a5e867ed1769def | `sha256:759e03c371cd640c041d69cb95a7a66c9c1da8139dd7b8e0c679e99df994a1ac` |
| query-runtime，未更新 | ef11d6aad0ffb8d8a656d5a17a3477d93b95a5c8 | `sha256:4a847f9631eee38204096f9ea66c159dcfcfbb25f17406ffcecab473795d5c5d` |

通过现有 Dockerfile 构建，仅以 Compose `up --no-deps --no-build --wait` 更新前端和控制面。最终七服务均为 running／healthy，四入口 smoke 通过。首次前端构建遗漏必填 `APP` 参数被正常拒绝，补齐 `APP=console-web` 后成功；不是产品测试失败。查询及摄取进程未更新；摄取镜像没有 revision 标签，不推断其源码提交。文档收口提交不作为运行版本。

唯一权威为 OpenDesign 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`：[项目入口](http://127.0.0.1:49175/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)、[管理员原页](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-admins&state=success&review=0)。本轮核实项目注册目录仍是仓库原项目，项目 API 与原页均返回 200；没有建立副本。原页／台账固定 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`，共享移动布局 `47c4f6f31823617633ce0bac98074208781f5565`，最新目录接受版本 `d36c7f46d0187c0421d4989c12f601d5c190d73f`；本轮原型资产差异为零。

## 真实验证与限制

| 验证 | 结果 |
| --- | --- |
| 两个目录 PostgreSQL 适配器回归 | 9/9，无失败、错误或跳过；包含新增 4 项，改动前后通过 |
| 控制面完整 Maven reactor 测试 | 18 模块、1015 项，失败／错误／跳过均为 0；含组织上下文、依赖、扩展、自动配置及 HTTP 身份验证；不等同全部后端启动模块验证 |
| `make frontend-test` | 48 文件／246 项，类型检查、lint、构建通过 |
| 最终实际部署只读目录浏览器 | 16/16；平台范围、两名既有管理员翻页／越界／过滤／总数、候选空结果、非法参数、页面恢复、跳页焦点、取消弹窗及拒绝越权均通过，业务写入 0 |
| `make acceptance-work-package WP=WP-0017 SLICE=login` | 15/15，平台／租户独立入口、三尺寸、200% 文字、键盘、reduced-motion、CSRF、刷新退出及错误入口；复用受保护凭据，没有 SETUP 或开户 |
| 原项目与生产只读视觉对照 | 7/7：390／1024／1440 正常及 200% 文字，另 390 reduced-motion；静态标题／导航／摘要／表头／筛选／分页及短按钮字体、颜色、行高、内边距等断言通过，几何容差仍为原 1px；溢出及分页裁切均为 0，键盘及五列表头语义通过 |
| 临时截图人工复核 | 查看三尺寸的实际页／原页及 200% 文字；实际页没有裁切。真实登录名、租户及资格由合同读取，不复制原型合成人名；ADR-0083 已接受的三项限定差异不扩大 |
| PostgreSQL 事实 | 17 组计数／状态查询，REPEATABLE READ READ ONLY，`transaction_read_only=on`；验收前后结果逐字及摘要相同 |
| `make architecture-guard` | 48 项；精确 XML 普通列表债务 5，依赖边界违规 0 |
| 收口控制面 | project-status、project-verify、decisions-sync、resume-context 均通过；69 项、29 任务、650 个历史定位；恢复输出 11417 字节，小于 12KiB |

Maven 复现命令：`./mvnw -q -pl backend/contexts/organization-access,backend/platform/platform-persistence,backend/bootstrap/control-plane-bootstrap -am test`。统计仅使用本次起始时间后的 Surefire 报告。生产目录以既有租户 `2093674235049279490` 的可用／停用管理员各 1 人验证，候选实际为 0，未把测试库候选正例写成生产样本。视觉及登录复用既有合成租户 `2104457357567197185`、身份 `2104457357567197186`；没有删除、覆盖或建立业务对象。

租户权限反例首次脚本误期待 403，实际是 401；沿 CodeGraph 的 `SaTokenPlatformContextArgumentResolver` 与既有 `AuthenticationApiTest` 核实平台 Cookie 契约后，仅纠正为精确 401，没有修改授权代码或用 401／403 宽泛断言。最终原型对照的一次并行执行在 1024px 放大场景等待超时，原因未证实；保留日志，以相同断言顺序复验全部 7 组通过，不删除步骤、不放宽字体或裁切条件。当前没有自动审批容量阻塞或未执行的本任务必要场景。

数据库检查只读租户、身份、资格及配置版本和全局计数；70 个租户、189 个身份、126 条平台账户审计、88 个成员事实／资格关系、101 个平台绑定等业务事实均未改变。登录会话的创建和退出不当作业务零写入对象。未读取密码摘要，未记录密码、Cookie 或 Token；未重启 Docker、清缓存、重置卷或操作历史验收对象。

## 日志、评审与恢复

详细执行脚本、日志和截图只在本轮 0700 目录 `/private/tmp/wp0017-admin-pagination-q0ebdzja/`，凭据文件沿用独立 0600 输入且未覆盖。截图不进入仓库；下列摘要均不涉及凭据：

| 外部产物 | SHA-256 |
| --- | --- |
| architecture-red.log | `7dbe7f1e61703d4274499c2edffb5504e5f6a992fa7b95bbb5ac8b56e3e0adbc` |
| directory-parity-red.log | `55dcf166328a8a979871f0965e10fa755c42ceadffa461c4873ada499b3470f2` |
| backend-summary.json | `5dc2c594fb2a5485545491888838f8cb8c8ae2482ef95c64d9e032a97c0b0805` |
| frontend-mobile-final.log | `b81f7b01f400a2720c83aa8fc73ff2ade2a90ce382c67f23e3777652864ee81a` |
| readonly-final-image-summary.json | `4c7d643fc8e9b1f769cdfbaa72cad7e673b7677910eeae4466a680dc24502caf` |
| parity-final-retry.cjs | `6d2560311b9edcabdef107ec59173dc18c86fd65ab4aaa1d90f2fcd8dee6e70c` |
| parity-retry-metrics.json | `22a532c1883aaa77d4440e84703137bfba7eaf1ad949a509e9a9e995c5952c3c` |
| parity-final.log，超时原记录 | `9f2c3da8c613e484caaec47eb69008c2c55bf30a77b452045c71eee781b6fdde` |
| parity-final-retry.log | `b25fb5745162ee05321cdf7a36f10c1f8562f03a36ed2ba1e32eeea8efa33eb8` |
| login-final.log | `a02e3385d8d71662a87218aef304d8262d639f05127dcfd65d64d55adbc41b92` |
| facts.sql | `56dfadb7c037470f5c9dce81f7162af0e2d4a29442df46d0512ecb01732dfae6` |
| facts-before.txt／facts-final.txt | `adb4de59c0c5fb86402b9a6507176b68af1cbbb63a6d7456ae6b4854d98b3c45` |
| runtime-final.json | `7c48fbd70f60bf50b6e438b6d67c29db99b886952a8ed25ebcb62f4d963902fb` |
| project-verify-final.log | `6b5dcbec6482f0ee33d634c7178fafb396a8be41bff63073804e36d12e4cf05d` |
| architecture-closure.log | `943f5c73a1df5d21b715572f9807006b1ec497b40716320322a5c4a3c3b35b1b` |
| resume-final.log | `8bde60ce5ef27f5cde3b9cbaf41468ea36c32dd0156cf15c33374d7689e31e39` |

固定 diff 评审覆盖两个源提交及本任务控制面，关注 count SQL 的相同安全谓词、分页框架只在 infrastructure、越界总数、写锁不变及局部样式；未发现剩余阻断。当前 Codex 本地工程助手 `2.2.0-rc.1+codex.local-20260929-051321` 显式加载 `engineering-assistant:wayfinder`、`tdd`、`code-review` 及安全 seam 参考；控制输入为根 AGENTS、Charter、ROADMAP、STATE、WP-0017 及全部 12 项 ADR。内联任务未生成 Runtime／StageRunResult，阶段结果校验不适用，不复用旧 Runtime 声称全绿。

当前任务恢复入口保持 WP-0017，STATE 及 M6 实现矩阵不变，仍为 **20/21 操作、11/12 入口**。管理员分页独立任务已闭合；`platform-overview:0` 所需 M8 运维事实及目标页仍未实现，用户要求保持原型且保持 M6 范围，下一步先确认依赖和工作包边界。其他五条分页债务、普通成员、完整问答登录各归后续工作包，不自动实施；不将本轮验收描述为 WP-0017、66 页生产或 M6 完成。最终 Git 推送与 CI 另按交付提交核实，不把旧提交绿灯作为本次结果。
