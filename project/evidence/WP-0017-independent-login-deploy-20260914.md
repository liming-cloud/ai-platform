# WP-0017：独立登录切片与真实部署验收

## 交付边界

本次独立交付平台 `/login` 与租户管理员 `/tenant/login`。两个入口复用现有浏览器 Cookie、CSRF、会话恢复与服务端角色合同，登录后进入各自授权工作区。登录 URL 只选择入口，不授予权限；错误身份不能显示受保护内容，已有会话可返回当前工作区或主动退出切换。

原型依据为 OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 中已验收的登录结构、Claude 暖纸色和陶土色令牌。仅修改登录局部排版、样式与路由，不复制原型状态。截图只用于临时目视检查，未入仓库；原型与原型代码未改动。

实现提交 `4ba2ba8bd67bf70591ba92dc2e58703858a1814c`，验收修正 `dd9bf244334ae7fe11690c2ded3056f69fb5bf64`，最终修复及部署提交 `f9ba767bf2b20716b2c04e812b45e616fe037f53`。基线为 `b1bca21e33c2d00d79f20573d3a82dff9cc376cf`。保留用户已暂存的 5 项 fixture 移动，未纳入上述提交。

本切片不代表 WP-0017 整包或两个原型入口的全部操作分组完成。原子租户开户、平台账户职责调整、独立设置和普通会员入口仍未交付；原型生产责任矩阵保持未完成状态。知识库、模型和 RAG 问答不在本次范围。

## 先失败，再修复

- 旧部署真实浏览器无法从两个独立入口互达，验收明确失败。
- 独立评审发现退出位于账户菜单的 `menuitem`，旧验收错误查找 `button`。5 项新增定位用例先失败，修复后 12 项脚本用例通过。
- 租户工作区的导航与标题同名，全页文本定位有歧义；加入同名导航后 1 项失败，改为限定 `#page-title` 后通过。Fluent Text 不是 heading，替身明确使用普通文本。独立内存变异检查：退回全页文本定位产生 1 项失败；退回 heading 定位产生 2 项失败。
- 真实部署首次布局验收发现登录 Input 被 Fluent 原子样式覆盖为 32px，44px 断言失败。只提高登录局部选择器优先级，最终浏览器复验通过；未添加全局 `!important` 或改变其他业务页面。

## 可复现验证

| 命令 | 实际结果 |
| --- | --- |
| `make project-status` | M6 / WP-0017，最后验证提交为当前 HEAD 祖先 |
| `make frontend-test` | 187 项通过；类型检查、ESLint、生产构建通过 |
| `./mvnw -q -pl backend/contexts/organization-access -am test` | organization-access 216 项，0 失败、0 错误、0 跳过；命令返回 0 |
| `node --test scripts/quality/work-package-login.test.cjs` | 12 项通过 |
| `python3 -m unittest scripts.quality.test_work_package_acceptance` | 6 项通过；未知工作包、未知切片、缺失凭据均拒绝，不跳过 |
| `make architecture-guard` | 28 项回归与门禁通过；原扫描仍为 15 处内联 SQL、3 处手工分页，不声称全仓清零 |
| `make project-verify` | 35 项测试、26 个工作包校验通过 |
| `make deploy-smoke` | 四个应用入口健康检查通过 |
| `make acceptance-work-package WP=WP-0017 SLICE=login` | 15 个真实部署场景全部通过，0 页面未处理异常 |

浏览器为本机 Chrome，Playwright 锁定 `1.62.1`，每个场景使用新的无持久 profile 上下文，不拦截或模拟 API。目标仅允许本机 `13000`，凭据必须来自当前用户拥有的普通 `0600` JSON 文件，不接受符号链接。复验需提供已经存在的两类管理员凭据：

```bash
NODE_PATH=/Users/lingou/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules \
FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE=/绝对路径/受控验收凭据.json \
make acceptance-work-package WP=WP-0017 SLICE=login
```

凭据文件结构为 `platform` 下 `loginName/password`，`tenant` 下 `tenantCode/loginName/password`；真实值不得提交。无 `SLICE=login` 时整包命令明确拒绝，不产生“整包通过”。

15 个场景包含：入口互达；两角色各自 390/1024/1440 宽度、200% 文字放大、键盘顺序；两角色在 reduced-motion 开启和关闭时登录、真实角色确认、授权默认路由及业务资源内容、刷新恢复、缺 CSRF 写入拒绝为 403、退出后会话为 401；已有身份误入另一入口后保留会话、返回工作区或退出切换；错误角色入口的新登录被拒绝且不遗留可用会话。另通过可见 Chrome 临时截图检查桌面和移动排版，并恢复浏览器原尺寸。

每个场景记录末次真实导航的 DOMContentLoaded、load 与 transferSize。15 个串行样本的 DOMContentLoaded 约 12.8～84.6ms，HTML transferSize 均为 1991 字节。这不是业务可交互时延、并发压测或 RAG SLA，不宣称相对旧版本的性能改善。

## 实际部署与回退

本次仅重建 `trusted-rag-foundation-v1-console-web-1`，最终健康状态 `healthy`，启动时间 `2026-09-14T01:58:04.271175422Z`。

- 镜像：`trusted-rag/console-web:foundation-v1`。
- 镜像 ID：`sha256:fd6d8451a2f09c8848f6d2dcd90e29e08f37f7f820165eac4aede8df34e2317e`。
- OCI revision：`f9ba767bf2b20716b2c04e812b45e616fe037f53`。
- Node 与 nginx 基础镜像沿用 `deploy/images/frontend.Dockerfile` 的固定摘要，没有升级依赖。

```bash
docker build \
  --label org.opencontainers.image.revision=f9ba767bf2b20716b2c04e812b45e616fe037f53 \
  --build-arg APP=console-web -f deploy/images/frontend.Dockerfile \
  -t trusted-rag/console-web:foundation-v1 .
docker compose --env-file deploy/compose/.env.example \
  -f deploy/compose/compose.yml up --detach --no-build --no-deps console-web
make deploy-smoke
```

本机 `docker-credential-desktop` 阻塞了最初构建。只停止该次构建及其凭据子进程，使用临时无凭据 Docker 配置、当前已解析的 Docker socket 和公开固定摘要镜像完成 BuildKit 构建；未修改用户 Docker 配置或仓库 Dockerfile。复现机器若遇同类问题，应先检查凭据助手，不关闭安全防护或改用未锁定镜像。

后端、PostgreSQL、Redis、MinIO 与数据卷未重建。control-plane 仍使用 `sha256:fd7f5e3fdde17d10b0f916ca0767ca08647a854f91c2e1130afe46d42e3239ad`（8 月 31 日启动）；query-runtime 与 ingestion-worker 为原 8 月 30 日容器。它们支持本次既有会话合同，但不代表已部署当前 HEAD 的后端代码或上一分页切片。

如需回退前端，可将保留的旧镜像 `sha256:d34f528eb269236d378ed8121da1d6f30bfaf6db2eb2255c724be8783c816c90` 重新标记为 `trusted-rag/console-web:foundation-v1`，仅执行上述 `console-web --no-deps` 重建并复查健康。没有数据库迁移、删除卷或数据回滚。

合成验收数据通过既有正式接口依次创建主体、租户及激活；这是测试准备，不是新的原子开户功能。保留的主体 ID 为 `2099310617969033218`，租户 ID 为 `2099310620183625730`，名称为“WP-0017 登录切片验收租户”，状态 ACTIVE。未创建普通会员，未删除任何既有对象；凭据留在本机独占临时文件中，未写入证据或原型。

## 工程助手与质量结论

当前插件环境为 Codex，能力来自已安装的 `engineering-assistant` 插件限定技能及 MCP Runtime。实际加载 `engineering-assistant:engineering-assistant`、`engineering-assistant:tdd`、`engineering-assistant:code-review` 及安全 seam；路由到 `implement` 流程，运行 `run-2e8bdafd95774683`。

使用 Charter、STATE、WP-0017 与其 11 个 ADR、ADR-0071 当前本地身份基线、原型人审证据和实际源码。代码图谱探索 `BrowserSessionGate`、`ConsoleApp`、浏览器会话客户端与 `ProductShell/AccountMenu`；前两者执行局部影响分析。结论使修改限定在既有登录 seam，不新建认证 API、身份库或第二套会话客户端。

固定差异 `b1bca21e3..f9ba767bf` 的摘要为 `sha256:122f52bf5791b8127152c21953258551e2d05d4b555456d002d57752499b1586`。独立复核提出的定位缺陷及真实样式问题均已闭合；没有未解决阻断项。六维质量以实际测试、部署、导航采样和固定 diff 为依据：保留单一会话门禁与懒加载；移除登录页签冗余；权限与 CSRF 不变；fake 仅检验脚本定位，真实业务以部署浏览器证明。Runtime 对切片、红绿、实现、质量、评审五节点及六维 QualitySnapshot 校验通过，运行 completed；此结论严格限于登录切片。

## 后续独立交付约束

后续任务继续按“已接受合同 → 失败测试 → 单个业务闭环 → 固定提交镜像 → 真实 deploy 浏览器与反例 → 可回退证据”交付。每个切片必须列出前置状态、独立验收命令、实际镜像、保留/新增测试数据和回退边界；不能拿健康检查、原型测试或另一切片的绿灯替代当前业务验证。

下一切片为租户与初始管理员的原子开户：先冻结专属生产合同，再以重复登录名、事务整体回滚及新租户从本次独立入口登录作为闭环。随后分别交付租户管理员、配额、功能与登录安全的独立设置；前一切片证据提交后再推进。WP-0017 保持进行中，不切换 WP-0018。
