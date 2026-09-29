# WP-0017：平台管理员完整账户操作切片部署后执行证据

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0017 |
| 验证范围 | `platform-accounts:0–4` 的创建与首次登录、重置、停用与恢复、空态及服务端目录 |
| 验证提交 | `11bb5abfec42b74eec127a622978fe8bcd07703e` |
| 后端运行 revision | `9cd1f8ff5fea06c7d0b33b6a5a641c587464d1d4` |
| 前端运行 revision | `11bb5abfec42b74eec127a622978fe8bcd07703e` |
| 决策 | ADR-0048、ADR-0079、ADR-0080、ADR-0083、ADR-0087 |
| 部署与验收日期 | 2026-09-29 |

**实际部署的账户操作、独立登录和只读数据库事实均通过。390px 行操作相对固定原型的可操作布局差异待用户决定；决定前不将原型一致性或本切片标为已闭合。WP-0017 整包与 M6 均未完成。**

## 固定原型与实际部署

唯一产品原型依据为 OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`、人工接受提交 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`、入口 `platform-accounts`。[本机原型入口](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-accounts&state=success&review=0) 仅供本机预览，固定项目与提交才是恢复标识；未改动 OpenDesign。

| 运行对象 | 实际 OCI revision | 容器镜像摘要 |
| --- | --- | --- |
| `console-web` | `11bb5abfec42b74eec127a622978fe8bcd07703e` | `sha256:11dfba9790c5e7549c02b1beac488e1ce28e8c5f18ea783014cf73a251fbbdc3` |
| `control-plane` | `9cd1f8ff5fea06c7d0b33b6a5a641c587464d1d4` | `sha256:fc8583b6119ce2eb72fddc08f9fc9596bd2dd36f7310e1a348769d8cc562db86` |

最终两容器均为 `running/healthy`，`make deploy-smoke` 四入口通过。后端运行版本不是当前仓库 HEAD。只更新控制面和前端，未重启数据服务、删除卷、重置账户或改写历史验收对象。

## 功能、登录与只读事实

使用新的 0700 临时目录及 0600 输入/输出文件，在上述最终 Compose 镜像运行 `make acceptance-work-package WP=WP-0017 SLICE=accounts`，**14/14 场景通过**。覆盖平台治理登录、只展示管理员的目录、创建取消零写入、重复登录名拒绝、正式接口原子建立、首次独立平台登录且无租户资格、重置前会话撤销确认、旧密码与旧会话失效、停用阻断登录、恢复不复活旧会话、真实对照账户、服务端搜索/状态/页大小/跳页及 URL 恢复，并覆盖三档视口与 200% 文字。以本轮新建管理员和既有隔离租户管理员执行 `SLICE=login`，**15/15 场景通过**，包括错误角色入口、刷新、CSRF、退出、键盘和 reduced-motion。

本轮合成主账户主体 `2104836035329392641`；验收通过正式接口另建 11 个分页对照账户。真实 PostgreSQL 的 `REPEATABLE READ READ ONLY` 事务只查询目标 ID 的计数、状态、版本和非秘密审计：12 个请求 ID 对应 12 个身份、12 个可用身份、12 个活动 `PLATFORM_ADMINISTRATOR` 绑定及 12 个授权修订；主账户为 `ACTIVE/version 3`，管理员绑定为活动。主账户 `CREATE/version 0`、`RESET_PASSWORD/version 1`、`DISABLE/version 2`、`ENABLE/version 3` 各有一条成功事实；12 个账户各有一条成功创建事实。未读取凭据、密码摘要、Cookie 或 Token。合成对象保留以供追溯，不删除或覆盖。

源码验证：后端完整 `./mvnw -q test` 通过，当前 336 份 Surefire 报告合计 1,022 项，失败、错误、跳过均为 0；最终前端 `make frontend-test` 通过 46 文件、231 项及类型检查、Lint、构建；`make architecture-guard` 通过，`scripts.quality.test_work_package_acceptance` 与 `scripts.quality.test_m6_implementation_map` 合计 32 项通过。源码测试不替代上述实际部署结论。

## 固定原型视觉对照

同一独立 Chrome 对固定 OpenDesign 和最终部署页在 390/1024/1440px 分别截图，并对创建、重置、停用确认弹窗的桌面与移动视口人工查看。截图及运行日志仅在 `/private/tmp`，仓库无截图；视觉指标 JSON 的 SHA-256 为 `55a8e9d1d5ce2d9e3b5ead6965a63f6bc84d3dedf15109bcb022d47e9538c511`。暖色画布、16px 正文、Georgia/中文宋体标题、`R` 与“可信 RAG”、标题/汇总/搜索/状态/表格/分页的次序和位置、创建入口与弹窗结构均与固定原型对照。实际身份和数量与原型模拟对象不同，未复制演示数据。

重置弹窗先发现勾选框和短按钮偏移，局部修正后在 390/1440px 的勾选区上沿及两按钮位置相对固定原型差约 0.1px；最终浏览器验收还检查勾选框相对位置、18px 尺寸、14px 单行操作按钮及 54/82px 宽度。人工截图复核按钮没有换行。演示密码说明按 ADR-0083 使用生产安全说明，不呈现公开演示口令。尚未实现的其他导航功能留到对应切片验证，未伪造入口。

390px 固定原型的账户行“更多”虽然可见，中心实际命中分页文字层而无法点击；最终生产页中心命中自身、可通过键盘展开，但为了保持可操作布局，分页相对固定原型下移约 42px。此前 ADR-0083 的同类例外只适用于 `platform-tenant-admins`，因此本差异尚待本切片的明确决定，不能直接宣称视觉全同或借旧例外通过。问题已由真实浏览器命中测试复现，未修改原型。

首次最终版本的严格按钮断言发现“取消”因字号修正缩至 50px，预期为 54px；相关失败临时证据保留，随后仅修正该宽度并在新提交、新镜像上重跑全部验收。该失败不是账户业务写入或服务端测试失败。

下一独立产品切片预定 `platform-roles:0`；其合同、角色目录和其他未实现操作不包含在本证据。`platform-tenant-detail:0` 仍待合同，普通会员、模型、知识接入和查询属于后续包。WP-0017 整包与 M6 均未完成。
