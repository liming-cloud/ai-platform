# WP-0014 平台权威模型发现 Chrome 与无障碍续证

## 范围与结论

本证据绑定实现提交 `2d5b0135c`，关闭 ADR-0070 在平台模型供应链页面遗留的登录后 Chrome、响应式、
真实 200% 缩放、键盘导航和 `prefers-reduced-motion` 续证。OpenAI-compatible `GET /models`、
火山方舟 `ListEndpoints`、未知能力管理员确认、凭据用途分离及供应商响应脱敏的后端证据分别见：

- `project/evidence/WP-0014-authoritative-openai-model-discovery.md`；
- `project/evidence/WP-0014-authoritative-volcengine-ark-model-discovery.md`。

本次浏览器续证没有向外部供应商发起真实发现请求，也没有新增或记录供应商凭据；权威 HTTP 契约、真实
PostgreSQL 和公开错误边界由上述证据与本次全量回归共同保证。本证据不关闭 M6 人工产品评审，不激活 M7。

## 红绿 TDD

### 登录输入无障碍名称与旧账户提示

- 红灯：Chrome 的语义定位无法通过“登录名”获取输入框，平台占位符仍引用已退役账户；新增静态契约后
  `BrowserSessionGate.ui.test.ts` 为 1 项失败、3 项通过。
- 绿灯：为租户编码、登录名和密码输入补齐稳定 `aria-label`，平台占位符改为不包含账户示例的通用提示；
  定向用例 4 项全部通过。

### 真实 200% 缩放下的表单完整性

- 红灯：真实 200% 缩放时，新建连接 Dialog 高于可视区且底部操作不可达；只增加普通 class 规则后，
  Fluent 运行时样式仍把 `overflow-y` 计算为 `visible`，更新后的定向测试保持红灯。
- 绿灯：公共 Dialog class 冻结 `max-height: calc(100dvh - 2rem) !important` 和
  `overflow-y: auto !important`；定向页面契约 7 项全部通过，真实浏览器可滚动到 AK/SK 与底部操作区。

### Escape 关闭后的键盘焦点

- 红灯：键盘以 Escape 关闭新建连接 Dialog 后，焦点落到 `BODY`，任务上下文丢失。
- 绿灯：关闭路径统一在下一帧把焦点还给“新建平台连接”触发按钮；普通动态和减少动态两种设置均通过。

### 架构包预算

- 红灯：首次 `make validate` 揭示 `domain.platformsupply.model` 已有 15 个类，超过既有预算 12；该超额在
  本次修改前的 `7c7b70962` 已存在，不能通过提高预算掩盖。
- 绿灯：仅将平台发现的候选、分类和观察值三个不可变值对象移动到
  `domain.platformsupply.model.discovery`，补充中文 `package-info.java`，不改变公开行为、持久化或 HTTP 契约；
  架构门禁恢复为超预算包 0。

## Chrome 证据

环境为 macOS、真实 Google Chrome、本地 Compose 同源入口 `http://localhost:13000`，使用已授权的本地平台
管理员建立浏览器会话；证据不记录口令、Cookie、token 或浏览器存储。

- `1024×768`：页面和新建连接 Dialog 均完整，`scrollWidth === clientWidth === 1024`，无横向溢出。
- `390×844`：精确视口覆盖后 `scrollWidth === clientWidth === 390`，底部导航完整；Dialog 为
  `x=0`、`width=390`、`y=78`、`height=688`、`bottom=766`，无文档级横向溢出。
- 真实 200%：通过 Chrome 自身缩放 UI 从 100% 调至 200%，辅助功能树显示“缩放比例：200%”；设备像素比
  从 2 变为 4，页面逻辑视口为 `1088×526`，无横向溢出。Dialog 的 `clientHeight=493`、
  默认 `scrollHeight=662`、`overflow-y=auto`，选择火山方舟发现协议后
  `scrollHeight=864`，可滚动到独立 Access Key ID、Secret Access Key 与底部操作区。
- 键盘：实际 Tab 顺序覆盖运营总览、平台租户、平台账户、平台模型供应链、角色菜单、新建与刷新按钮；
  Enter 打开 Dialog 后焦点在连接名称、厂商、发现协议、Endpoint、区域、API Key、取消、创建之间闭环；
  Escape 关闭后焦点返回触发按钮。
- 减少动态：通过 macOS 系统设置实际开启“减弱动态效果”，Chrome 的
  `matchMedia('(prefers-reduced-motion: reduce)').matches` 为 `true`；页面和 Dialog 没有大于 1 毫秒的
  transition／animation，Escape 在 50 毫秒内关闭并恢复焦点。完成后系统设置恢复关闭，Chrome 恢复 100%。
- 最终镜像重建后控制面重启使旧会话失效；重新登录并刷新
  `/platform/model-supply?page=1` 后标题、新建按钮和供应链工作区均存在，无横向溢出。
- Chrome 日志仅有浏览器扩展异步消息通道关闭提示，没有应用异常、厂商原始响应、厂商错误码或内部凭据标识。

## 自动与基础设施验证

- 定向后端：
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -q -f backend/pom.xml -pl contexts/model-runtime -am test`，
  真实 PostgreSQL/Testcontainers 全部通过，退出码 `0`。
- 架构门禁：`python3 scripts/quality/backend_architecture_guard.py verify`，超预算包、SQL、UUID、运行时越界、
  Sa-Token 越界、旧 `api`／`adapter` 和 `runtimes` 文件均为 0。
- 前端全量：`cd frontend && npm test`，31 个测试文件、145 项测试通过；`npm run typecheck`、
  `npm run lint` 和 `npm run build` 均通过，生产构建转换 2181 个模块。
- 全量门禁：
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home make validate`，
  项目治理、全仓库 Maven、真实 PostgreSQL、前端、演示、真实模型验收、恢复、容量和交付门禁全部通过。
- Compose：从实现提交对应源码重建 `control-plane`、`query-runtime`、`ingestion-worker` 和 `console-web`，
  镜像标识分别为 `96b01812a84b`、`076d4f99a8fb`、`a9ceb4f260d6`、`eedd7e7c1813`；与 PostgreSQL、
  Redis、MinIO 合计 7 个服务全部为 `healthy`。
- 工作区：`git diff --check` 通过。

## 代码图谱与影响面

- 聚焦探索 `BrowserSessionGate`、`PlatformModelSupplyWorkspace` 和 `PlatformProviderModelDiscovery` 的登录、
  Dialog、发现聚合及适配器路径。
- 对 `PlatformProviderModelDiscovery` 执行深度 3 的影响分析，共识别 76 个受影响符号，覆盖发现服务、
  PostgreSQL store、OpenAI-compatible／火山方舟适配器和测试。
- 影响分析使架构修复收敛为三个发现值对象的子包移动，没有提高包预算、改动聚合行为或扩大公共契约。

## 工程助手插件证据

- 当前运行环境：Codex；能力来源：本地 `engineering-assistant` 插件。
- 路由并显式加载：`engineering-assistant:tdd`。
- 使用的控制面：该技能的 `SKILL.md`；按可观察行为、公共 seam、真实协作者、最小增量、负向路径和可复核
  证据六个维度完成红绿循环。
- 本切片没有生成 `StageRunResult` JSON，阶段结果校验器不适用；验证结论由上述测试、Chrome、Compose 和
  Git 提交共同承载。

## 结论

ADR-0070 的平台模型供应链已完成从权威发现契约到登录后 Chrome、响应式、真实 200%、键盘和减少动态的
续证；页面不泄露供应商原始响应、错误码或内部凭据标识。M6 与 `WP-0014` 继续保持进行中。
