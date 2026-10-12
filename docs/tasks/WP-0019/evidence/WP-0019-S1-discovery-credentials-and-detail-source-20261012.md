# WP-0019 S1：方舟发现凭据原型候选与只读详情源码证据

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0019 |
| 验证提交 | c23b2960ae658c12202c9303e7971320ce872225 |
| 原型候选提交 | d5deddaa1955814c5f08795b0b1ca58a7e6ac784 |
| 输入提交 | f24dd2df2189b9286ccddb91a51177e811f5dd03 |
| 验证范围 | S1 原项目方舟凭据区、两页原型回归、连接只读详情源码；非 S1 完整验收 |
| 日期 | 2026-10-12 |

## 决定与原项目定位

用户本轮“同意并继续推进任务”，授权先纠正原型缺少方舟独立发现凭据的问题，再继续 S1。ADR-0070 已接受推理 API Key 与控制面 AK/SK 的用途分离；这次修订恢复该协议约束，不是修改原型来迁就生产。十一页、二十九操作和 S1～S6 归属不变。

唯一来源仍为 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`。只读查询原项目 HTTP 元数据确认 `resolvedDir` 为仓库 `docs/prototypes/wp-0016`，开始时与已接受固定版本 `35571b0f823390bd6b80f5771e512b11be9403a4` 无差异。直接修订此注册目录，没有创建副本或修改项目绑定；原项目 HTTP 返回的两份源文件与本地 SHA-256 相同。

当前可审阅入口：[原项目连接目录](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=success&review=0)，选择“新增供应商连接 → 火山方舟”。端口是本次观察值，后续从原项目取得当前地址。候选固定为 `d5deddaa1955814c5f08795b0b1ca58a7e6ac784`；后续源码提交没有改变两份原型界面源文件。已提交候选供人工确认，尚未收到对新版本的接受，不能取代旧接受事实。

OpenDesign Cloud MCP 本轮返回 `Transport closed`；原项目 HTTP 仍可访问。桌面工具报告 Mac 锁屏，已请求解锁，未进行桌面窗口验收；没有把这两个环境现象认定为产品或审批容量失败，没有重启应用、Docker 或清理缓存。

## 原型变更与红绿证据

方舟保留原推理密钥，新增独立发现认证区，AK 和 SK 均为遮蔽输入、方舟必填、其他厂商隐藏并禁用。详情仅增加“独立 AK/SK 已配置（不回显）”状态。三项秘密不进入原型返回值或持久化快照；切换厂商／能力、取消、Escape 和保存失败均清空输入，关闭恢复新增按钮焦点。不使用真实密钥、原型状态或模拟探测冒充厂商事实。

状态反例修正测试存储参数后，三项新增测试真实失败：缺少发现凭据仍保存、缺少独立发现配置状态、其他厂商接受用途不匹配的凭据。浏览器反例因独立字段不存在失败。局部修复后：

| 验证 | 结果与实际范围 |
| --- | --- |
| `node --test docs/prototypes/wp-0016/scripts/*.test.mjs` | 178 项通过，包括三项新反例；没有运行写入旧静态验收报告的脚本 |
| `provider-presets.test.mjs` 与 `model-governance-state.test.mjs` | 14 项定向状态测试通过 |
| `platform-connection-credentials.browser.test.cjs` | 15 场景通过；凭据隔离、清空、刷新、三尺寸、键盘、reduced-motion，及两页七态三尺寸 42 组合 |
| 既有 `model-governance.browser.test.cjs` | 12 场景通过；既有模型治理操作及十一页三尺寸回归 |

新专项尺寸为 `390×844 / 1024×900 / 1440×1000`。200% 文字测试对实际计算字号翻倍，并断言输入字体由 16px 变为 32px；没有将仅修改根字号误记为固定字号已放大。逐项检查输入文字、控件边界、弹窗横向溢出及键盘可达。已通过图像工具查看临时的普通与 200% 文字截图，凭据区可滚动到达，标签换行，底部操作可用；这是 Agent 视觉检查，不是用户人工接受。

历史索引中被修订的三项记录改为 `update`，保留原来源提交、原 SHA-256 和引用；逐项先从原 Git 对象验证摘要。没有改变历史校验器或旧验收报告，650 项历史资料仍由完整校验覆盖。

## 只读连接详情源码

新增 `GET /api/v1/platform/model-supply/connections/{id}`，返回既有安全摘要。可信平台会话决定身份，先检查 `MODEL_PLATFORM_WRITE` 再解析 ID 和读取平台仓储；不存在使用既有安全错误。外部租户、主体和未知查询参数拒绝，不读取凭据、不生成 ID、不发出厂商调用、不启动写入事务或产生发布事实。沿用已有 controller／service／repository，没有新增模块、Maven 依赖或迁移。

同一 MVC 测试先以 `200` 与实际 `404` 的差异复现缺少详情入口；修复后控制器与应用服务共 15 项通过。另覆盖无权时禁止仓储 I/O、非法和不存在 ID、外部范围拒绝、安全投影及零写入调用。未知参数测试验证真实控制器抛出拒绝；平台既有 `FoundationExceptionHandler.invalidRequest` 负责错误映射，没有为了该测试新增平台 Web 依赖，也没有宣称本轮已在部署 HTTP 中验证该映射。

完整 `./mvnw -q verify` 退出 0：346 个测试报告、1106 项，失败／错误／跳过均为 0；其中 model-runtime 为 63 文件、206 项。完整日志 SHA-256 为 `0d84e72cd8fc3ee402c44dc078f738672baecd94dd4a8d91bdcfd01887381c45`。`make architecture-guard` 的 50 项守卫单测及后端／前端门禁通过；原型修订后的 `make project-verify` 81 项及 30 任务验证通过。此轮没有生产 SPA 代码变更，不将原型测试记为生产前端验收。

## 评审与证据位置

Local Codex 已显式加载工程助手 `wayfinder`、`tdd`、`code-review`，使用当前插件 `2.2.0-rc.1+codex.local-20260929-051321` 的对应 SKILL.md、TDD `references/security-seams.md`，结合 Charter、ROADMAP、STATE、WP-0019 及其全部十二项 ADR 以内联方式执行。没有创建 Runtime 或 StageRunResult，阶段结果校验不适用，没有复用 WP-0017 的 Runtime。

代码图谱探索连接 service／controller／view，影响分析 `createPlatformModelConnection` 涉及 20 个符号、`PlatformProviderConnectionService` 涉及 42 个符号。图谱部分探索返回无关候选，确定符号的结构提纲后按精确范围补读。影响分析使实现复用既有安全投影和装配，未扩大到部署、租户配置或运行时协议；图谱不作为编译／测试证明。

Agent 对固定差异自评，无剩余本差异内发现，不伪称独立人工评审。详情与最终原型回归五文件 diff SHA-256 为 `4919d3860ddba966c4225946146e0978b0c12be90b0bc01861ec45a76f466403`。两个源码提交均逐文件核对测试时摘要与 Git blob 一致。

日志、差异、摘要清单和截图均位于权限 0700 的 `/private/tmp/wp0019-s1-ark-1egfic3m`，不进入仓库；未使用真实账户或厂商凭据，真实秘密未输出或另行落盘。临时原型截图没有凭据输入。临时资料不保证长期存在，以上完整提交、可执行测试和日志摘要提供恢复与复验定位。

## 部署事实与下一动作

本轮开始只读确认所有 Compose 服务健康。前端运行 revision `8ade6bbe15a53fdf73a1fea794e642632f59b835`、镜像 `sha256:7652c5de0335b5ffbf8f94a59ab7d587c7d446cc154482389bb74efac1356e54`；后端三个运行进程的 revision 均为 `46fb0dd5f0149e7bac155b87a8aa9a9f21d16914`，control-plane 镜像 `sha256:a4bf11ac45b22546c655bf1860216f4f2dc08d43fbe6a01e34f40a89a80c02dc`。运行版本与本轮源码提交分开，未部署新代码。

下一动作是接受本次原型候选后继续 S1 五厂商预设、生产创建表单、详情和探测／发现／发布 UI、正式 `SLICE=platform-connection` 入口，再完成受控真实厂商合同、最新 Compose 浏览器、只读数据库事实和生产原型对照。S1 五操作尚未整体闭合；S2～S6、WP-0019 整包与 M6 均未完成。没有重跑创建租户的旧验收、操作生产数据库、重置数据或提前推进其他业务切片。
