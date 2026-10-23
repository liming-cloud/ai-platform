# S2 无外部账号的完整 HTTP 逻辑回归

| 字段 | 值 |
| --- | --- |
| 责任范围 | WP-0019／S2，完整逻辑回归与业务冲突修复 |
| 输入提交 | b67081ff64309143710250fba89ee586eb94cb8c |
| 验证源码 | 1cc979ae9de6a00a03bfc178b5d587facb8a6004 |
| 日期 | 2026-10-23 |
| 结论 | 核心链路隔离验证通过；真实厂商联调、正式发布赋权与相应生产原型流程仍待补 |

用户明确“账号暂未提供的，需要保证功能逻辑没有问题可推进下一步”。账号缺失只延期依赖该账号的真实联调，不阻塞独立实现与逻辑验收；不降低 ADR-0095／0100 的真实模型可用性、发布授权及切片完成门禁，不进入 S3。

## 已验证的逻辑与修复

新增启动模块的 `PlatformSupplyWorkflowApiTest`，使用正式 HTTP 控制器、真实平台／租户会话、加密凭据存储、生产 Spring AI／独立 RERANK 适配器及 Testcontainers PostgreSQL／Redis。仅厂商传输替换为本机 HTTPS 合成服务；临时证书保留主机名验证，只放行随机环回端口的两个精确 URI，类结束恢复 JVM 信任并删除密钥库。没有修改生产 TLS 或 SSRF 校验。

S1 的已发布连接／发现用隔离种子准备，合成 Key 通过真实加密写端口保存；不重复 S1 验收。全部 S2 部署、验证、发布和撤权由正式接口产生，不直接插入 S2 通过或授权事实。

- Chat、Embedding、RERANK 各自完成：幂等创建草稿 → 固定三个样本 → VERIFIED → 独立原子发布 → 仅选中租户可见 → 撤权立即不可见 → 停用保留历史。每类恰好三次本机协议调用，不重试外呼。
- 独立数据库断言：完整三样本摘要、预留终态、次数与费用上界、验证审计、唯一发布。活动授权与拟授权分开，其他租户不能冒充平台。
- 缺报价零外呼且零预留；错误向量维度一次即停；两者均不能发布或留下供应授权。
- 真实失败测试发现：未验证发布、旧修订发布返回 500。同一供应仓储中的重复／不存在授权拒绝仍使用相同泛化异常，连同这条反例纳入精确业务冲突回归。局部复用既有 `MODEL_RUNTIME.CONFLICT.0008`／`0004`，改为 HTTP 409；不全局捕获 `IllegalStateException`，持久化和审计故障仍保留原失败处理。原五条数据库反例改为精确业务异常，其余回滚、唯一性和授权断言保留。

临时材料集中在 `/private/tmp/wp0019-s2-logic-20261023-ygqob143`，不含真实凭据文件或其摘要。`workflow-execution-3.log` 保存真实 500 红灯；前两次执行的 HTTP 替身／报价／信任准备错误及编译弃用警告单独保留，不作为产品缺陷证据。最终固定 diff 为 `reviewed-source.patch`，SHA-256 `38a1d3cd71c2df82d70e3081a7fe144d028d62f7de4c87e5490cea44fa847661`。

## 验证与运行版本

| 检查 | 结果 |
| --- | --- |
| 定向回归 | 新 HTTP 链路 5 项、真实供应数据库 32 项、原控制器 3 项，失败／错误／跳过均 0；`targeted-summary.json` |
| 完整后端 | `./mvnw -q clean test`：363 类／1,316 项，失败、错误、跳过均 0；`backend-final-all-2.log`、`backend-final-summary.json`，退出 0，且报告全部晚于本轮开始时间 |
| 架构及控制面 | `make architecture-guard`、`make decisions-sync`、`make project-status`、`make project-verify`、`make resume-context` 退出 0；81 项治理测试、30 任务及 650 项历史对象校验通过 |
| 部署与只读事实 | 三个后端容器均运行前述源码提交，七服务 healthy；`make deploy-smoke` 四入口通过；直接及前端代理匿名只读均按合同返回 401 |

三个后端均经正式 Dockerfile 构建并用真实 Compose 更新应用容器，未重启 Docker。实际镜像 SHA-256：

| 服务 | 镜像 SHA-256 |
| --- | --- |
| control-plane | 15ab518a2c51d93bd850fd2d647f98364cb3dfffc43a33edb6fdd6262fb2c707 |
| query-runtime | 774c8e7b406f888ba5efa2571be535b6fb5aa0fa921e0b5466b5a61689d9a5c3 |
| ingestion-worker | e6b042da89d3a4074405b8f827463f71942ca402bcb40dc03b95b36095410929 |

`deployment-before.json`／`deployment-after.json` 逐服务绑定 OCI revision、镜像和容器 ID；PostgreSQL／Redis／MinIO 及前端容器 ID、镜像均未变。前后 `REPEATABLE READ READ ONLY` 核对的 13 类业务计数与草稿状态一致：连接 3、发现 13、候选 787；唯一部署 `2112408560880058369` 仍为 DRAFT／版本 0，验证、预留、验证审计、发布、供应授权、供应审计、目录、配置和配置授权均 0。未尝试正式发布或更改既有业务行。

临时材料清单 `evidence-digests.json` 共 41 项／3,219,904 字节，SHA-256 `0a6c62ddf127bf2f1454e07b475dd616aa2a8c89ab0cc1e81721a0e59da59313`；不含清单自身和此后的控制面复核、Git／CI 回执。临时 TLS 密钥库已删除，截图和浏览器缓存均未生成。650 项历史资料另以固定原 Git 对象与 SHA-256 独立逐项复核，见 `history-current-verification.json`，不改写过去的验收绑定。

首轮完整回归因隔离 PostgreSQL 被分配到原型正在使用的 `58138` 而失败：`lsof` 确认 OpenDesign Helper 监听该端口，原项目 HEAD 请求 HTTP 200，日志中的 JDBC 端口相同。原型服务保持运行；单独复验重新分配 `58614`，原失败的 10 项全部通过。最终完整回归单独重跑，只统计本轮开始后生成的报告，不将尚未重新运行模块的旧报告混入。

前端保持 `c36450c101e63eabd42db52be4c73fb9c8533c38`，OpenDesign Cloud 唯一原项目为 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，[平台部署](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-deployments&state=success&review=1)，已接受版本 `f3216dffe717e194e96fa20b4afee7983a9ff420`。本次未修改页面、原型或模拟状态，不重复已通过的布局验收，不新增截图。已有生产与原型证据仍绑定各自原日期和版本；隔离 HTTP 通过不冒充真实厂商或发布后完整原型验收。

## 剩余事项与下一步

核心逻辑子任务可独立闭合。后续先推进不依赖外部账号的 S2 工作；目录搜索仍有此前“名称／摘要”与演示“整行匹配”的语义待决，不从本次账号安排推导公共契约接受。真实联调在账户具备后补齐有效能力来源、账户费率与独立输入 Token 上界，并对精确目标、修订、次数和费用取得调用授权；正式发布／赋权另行授权。继续复用 `pwd.txt`，不要求重发平台密码，不创建云端资源，不改既有业务数据。

S2 仍为 2／4、WP-0019 7／29；切片、工作包和 M6 未完成。[源码 CI 39880508838](https://github.com/liming-cloud/ai-platform/actions/runs/39880508838) 已实际 success，仅治理 job，不替代本地后端或真实厂商证据。当前没有已确认的 CI 账单阻断；最终控制面提交推送后再核对其精确 CI，不沿用源码绿灯。

工程助手环境为 Local Codex 个人插件 `2.2.0-rc.1+codex.local-20260929-051321`；显式加载 `engineering-assistant:wayfinder`、`tdd`、`code-review` 及安全 seam，固定 diff 内联自审记录于 `fixed-diff-review.json`。代码图谱探索控制器、能力编排、真实样本传输及供应仓储；服务影响 36 个符号、仓储影响 40 个符号，定位后仅修改既有异常映射，没有新 Maven 依赖。未创建 Runtime／StageRunResult，阶段校验不适用。
