# WP-0019：S1 重发现一致性与连接页面源码验证

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0019 / S1 |
| 验证提交 | fc970f9ec5a07da1eb41cc80ec84f31d93469f48 |
| 输入提交 | d6c0cd4aad061a95dfc7f51beced1bdd6b56284b |
| 原子提交修复 | 4434e4ff3bf89c503bf764cf8af881025f197cbf，已包含于验证提交 |
| 日期 | 2026-10-12 |
| 接受依据 | ADR-0095；原型接受记录绑定 d5deddaa1955814c5f08795b0b1ca58a7e6ac784 |
| 验证范围 | 后端源码、真实隔离 PostgreSQL、MockMvc、前端构建及合成输入的隔离 Chrome；不是部署或 S1 生产验收 |
| 完成边界 | 本批源码子任务已验证；S1 五操作、WP-0019 整包与 M6 仍未完成 |

## 结果与不变量

重发现成功和失败均刷新连接修订、验证时间及安全结果。失败回到 DRAFT，不能沿用旧 VERIFIED 或当前发布标签。厂商 HTTP 位于事务外；连接 CAS、发现根记录和候选在同一 PostgreSQL 事务提交。停用、轮换或其他并发更新后到达的旧响应不能覆盖连接或留下可消费候选；记录失败回滚连接变化。

连接目录和详情新增批量配置事实投影。仅通过用途槽的 EXISTS 判断推理认证及独立发现 AK/SK 是否齐备，通过当前连接修订和 VERIFIED 判断发布记录是否属于当前版本。查询不选择密文、指纹、私有引用或秘密，不解密凭据；“已配置”不代表认证有效或厂商可用。平台权限先于事实源调用；对象、版本或事实缺失时失败关闭，不合并不同修订的结果。精确登记 `PlatformProviderConnectionMapper.xml#selectFacts` 为必要多表查询，未提高 Java 内联 SQL 或包预算基线。

生产页面源码承接五厂商、十一组能力意图及地址联动，读取服务端预设目录。百炼空间标识按 DNS 标签约束，固定厂商地址只读，方舟分别提交推理 Key 和独立发现 AK/SK。切换厂商、能力或空间，以及失败、取消、Escape、身份替换均清空一次性输入；不写浏览器持久化。表单卸载时同步失效，迟到响应不能导航到旧主体对象。

详情有精确 ID 和候选页深链接，读取当前修订的发布及候选事实；探测后才显示发布动作，失败不保留旧通过／候选，发布不创建部署或租户授权。分页切换清除旧请求等待状态。新页面取得标题焦点，避免已卸载模态遗留 aria-hidden；保留模态焦点约束。无来源的旧记录不猜测厂商、模型能力或 OpenAI 协议。RERANK 仍仅为 S1 意图，真实能力和端口属于 S2。

## 红绿与完整验证

| 检查 | 真实结果 |
| --- | --- |
| 重发现最小失败测试 | 12 项中 4 项失败：时效不刷新、失败沿用 VERIFIED、停用后迟到候选、发现记录失败未回滚连接；修复后通过 |
| 配置事实反例 | 原接口固定回显“已配置”，9 项中 1 项失败；改用事实投影后通过，并覆盖缺失／错对象／错版本拒绝 |
| 路由及五厂商反例 | 原详情 URL 无法恢复；原表单不具备五厂商目录，真实隔离浏览器失败，局部实现后通过 |
| 详情异步反例 | 25 项通过后，切换候选页残留“正在探测”失败；修复请求归属及等待状态后通过 |
| 身份切换反例 | 新页面按钮仍被旧模态标记 aria-hidden，无法再次操作；非秘密 DOM 定位日志保留。新页面焦点归属及同步表单清理后通过，未改为非模态或删除断言 |
| 旧记录协议反例 | 26 项通过后，无预设的非 OpenAI 连接被误标，目标断言失败；依据明确 providerKind 呈现，未知协议显示待确认 |
| 真实 PostgreSQL | 配置用途槽／AK-SK 成对状态、当前修订发布、停用后失效，以及原子提交／CAS／回滚通过；只运行 Testcontainers 独立数据库 |
| 完整后端 | `./mvnw -q verify` 退出 0；347 个测试文件、1124 项，失败／错误／跳过均为 0 |
| 完整前端 | `make frontend-test` 退出 0；52 文件、267 项，类型、lint、构建通过。旧二厂商静态断言改为当前合同及实际交互覆盖，数量不能跨范围直接比较 |
| 隔离浏览器 | `NODE_PATH=/Users/lingou/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules node scripts/quality/platform-connection-form.browser.cjs` 退出 0；29 项通过，含十一组联动、秘密清理、模态键盘、精确保存导航、探测／发布／失败及迟到响应 |
| 架构 | `make architecture-guard` 退出 0；50 项测试、前后端守卫通过，后端依赖边界违规 0，Java 内联 SQL 文件仍为 14 |
| 治理 | `make project-status`、`make project-verify` 通过；81 项治理测试、30 个任务及 650 项历史资料保持有效。记录后 `make decisions-sync`、`make project-verify`、`make resume-context` 通过，恢复输出 9971 字节并包含当前 S1 及下一动作 |

隔离浏览器创建独立页面和合成客户端，不登录生产、不访问真实厂商或业务数据库，不读取用户浏览器资料；其结果明确标记 productionAcceptance=false。它验证真实 React 组件的交互，不能替代真实会话 HTTP、Compose 浏览器或视觉对照。

非目标失败同样保留：首次 Escape 检查在界面提交前取值，改为等待关闭与实际焦点条件后通过；新增发布夹具暴露独立测试库外键清理顺序，补齐测试清理；一次误用系统 Maven 被锁定版本门禁拒绝，改回仓库 Wrapper。没有降低视觉容差、关闭版本校验、修改生产数据或跳过失败场景。

## 原型、部署与未执行范围

唯一产品设计来源仍为 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`。本轮通过项目工具确认原注册目录，两条原型入口 HTTP GET 均为 200；这不是视觉验收。按已接受版本 `d5deddaa1955814c5f08795b0b1ca58a7e6ac784` 的目录、详情、凭据区及共享令牌实施；未修改原型。与接受版本的原型目录差异仅为此前已登记的专项测试脚本，产品 HTML、JS 和 CSS 未改动。

本次服务会话可查看[连接目录](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=success&review=1)和[连接详情](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=success&review=1)；端口仅在当前原型服务会话有效，长期定位以上述项目和固定版本为准。没有保存或提交截图。

本轮没有部署或外呼厂商。最近登记的前端运行 revision 为 `8ade6bbe15a53fdf73a1fea794e642632f59b835`、镜像 `sha256:7652c5de0335b5ffbf8f94a59ab7d587c7d446cc154482389bb74efac1356e54`；后端为 `46fb0dd5f0149e7bac155b87a8aa9a9f21d16914`、control-plane 镜像 `sha256:a4bf11ac45b22546c655bf1860216f4f2dc08d43fbe6a01e34f40a89a80c02dc`，详见[上轮既有连接复验](WP-0019-S1-preset-source-and-existing-connection-check-20261012.md)。这些是上轮观察，本轮未复验或改变，不能将本次 HEAD 当作运行版本。

有效平台凭据此前已复用，无需用户重新提供。既有方舟连接的 DNS 命中保留测试网段，被安全出站策略拒绝，且只配置推理 Key、缺少独立发现 AK/SK；尚无真实厂商成功合同。当前不是审批容量不足，没有把环境拒绝记作厂商测试通过，也没有放宽 SSRF、导出厂商密钥、重设密码或重置 Docker／数据。

仍需建立正式 `WP=WP-0019 SLICE=platform-connection` 验收入口，完成受控真实厂商合同、最新 Compose 真实会话与精确对象事实，以及两页／弹窗七态、390／1024／1440、200% 文字和专项原型对照。功能与视觉分别通过后才收口 S1，不能进入 S2 或提升二十九项生产完成状态。

## 可复核产物、图谱与评审

详细日志、失败定位及固定 diff 位于权限 0700 的 `/private/tmp/wp0019-s1-consistency-2wg1xaxj`。仓库外 `source-validation-manifest.json` 绑定当前源码和 20 项选定产物，其 SHA-256 为 `9853ed1f1e96be868c39798f89399b16adbc82516dec438bcddfe855120afe8b`。最终后端日志摘要为 `873a7eeca8e5e191782feaadfd51c10c1364ae822200d351307569b5daf79148`，前端为 `36a359dbab30b2cce3ea10883f7c42dc16b34dd88d6c468f39ee45bdcde194cd`，29 项浏览器日志为 `e5bedf4f181c74dd68ee874299b25452bf792e3a47e1241054e925c9fde227f9`。临时产物不保证永久存在；本记录保留提交、命令、范围与结果用于重跑。

Local Codex 显式加载本地个人版工程助手的 `engineering-assistant:wayfinder`、`engineering-assistant:tdd`、`engineering-assistant:code-review` 及安全 seam 约束，按内联子任务执行；未创建 Runtime 或 StageRunResult，阶段结果校验不适用，不复用旧 RAG Runtime。

代码图谱探索重发现服务、真实事务适配器、连接服务／Mapper、目录、表单、详情及路由。对发现存储、PlatformProviderConnectionService、PlatformProviderConnectionMapper、PlatformModelSupplyWorkspace 执行影响分析；服务和 Mapper 分别定位 37、14 个影响符号。据此保留出站事务外边界、同事务 CAS 及批量只读事实端口，没有扩大到运行能力或扩展重构。当前安装的 Fluent/Tabster 仅作只读诊断，未修改第三方包。

固定差异评审范围为输入提交至上述验证提交，包含原子修复及页面批次。最终页面批次 diff 摘要 `bd1586925dfc66a5951d2f6ab4472018e34cf226ed09292c7db583d3a553bd7b`；已处理评审发现的旧协议误标与模态生命周期问题，无未处理的范围内阻断发现。真实验收缺口明确保留，不能据此宣称产品闭环。精确路径暂存，检查既有三项凭据值未进入差异；fixture、部署及原型源码均未改变。
