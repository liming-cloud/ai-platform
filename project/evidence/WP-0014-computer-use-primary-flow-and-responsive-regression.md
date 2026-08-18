# WP-0014 操作电脑主流程与响应式回归证据

## 边界

- 里程碑与工作包：`M6 / WP-0014`，保持进行中，不进入 `M7`。
- 本次唯一纵向切片：使用真实 Chrome 和操作电脑插件完成从 `platform.root` 到专用租户管理员、普通用户、知识索引和确定性模型供应链的主流程，同时修复紧凑导航无障碍名称。
- 不把自动测试、浏览器局部通过或本地确定性模型等同于人工 UI 接受。
- 不处理 TLS、Certbot、生产部署或 Nginx TLS。

## 基线与基础设施

- 分支：`codex/trusted-rag-foundation-v1`。
- 基线提交：`50a7f6046c27468304a1c0d6f07190d493ca0d1f`。
- 开始时本地数据库已按前一切片精确备份并重置为唯一 `platform.root`；备份位置、摘要和恢复验证见 `project/evidence/WP-0014-complete-ui-redesign.md`。
- 本次回归期间 control-plane、query-runtime、ingestion-worker、PostgreSQL、Redis、MinIO 和 console-web 均保持 healthy。
- 统一入口：`http://localhost:13443`。

## 紧凑导航无障碍红绿 TDD

真实窄窗口回归发现：导航文字被视觉隐藏后，六个导航按钮在辅助功能树中没有名称；Tooltip 说明不会自动成为按钮名称。

首次测试只匹配到分组的 `aria-label`，属于无效红灯，未据此实施。收紧为只匹配 `NavigationButton` 内部 `Button` 后，定向测试出现 1 项预期失败；最小修复为导航按钮增加 `aria-label={item.label}`。

验证命令：

```bash
cd frontend
npm test -- --run packages/design-system/src/shell/ProductShell.ui.test.ts
npm run typecheck
```

结果：修复后 1 个测试文件、3 项测试全部通过，TypeScript 项目构建通过。真实 Chrome 辅助功能树随后稳定显示“运营总览”“成员与权限”“数据”“模型”“治理”“问答”等名称。

## 平台管理员主流程

使用 `platform.root` 完成以下真实页面操作：

1. 运营总览：空库统计、固定时间范围、刷新和空状态均可理解，没有后端消息、错误码、追踪标识或内部 ID 泄露。
2. 平台账户：建立 `tenant.admin` 和 `tenant.user`；两个账户均未绑定平台角色，`platform.root` 保持唯一平台管理员。
3. 租户建立：建立 `M6 验收租户（m6-review）`，首位管理员选择 `tenant.admin`；候选列表不包含 `platform.root`。
4. 租户生命周期：建立后状态为“准备中”，随后经确认对话框激活为“活动”。
5. 平台模型供应链：建立 `M6 确定性模型连接`，使用 `memory://m6-review` 和仅供本地开发的无外部权限测试值；列表不回显 Endpoint 或 API Key。
6. 模型发现与供应：验证连接后发现 `deterministic-chat` 和 `deterministic-embedding`；两者分别建立草稿、执行能力探测、发布部署并发布平台目录。

等待用户确认租户建立期间，原平台会话自然过期；首次最终提交返回 401，数据库确认没有产生租户。重新登录后以相同信息重新执行并成功，不能把这次自然过期登记为应用写入成功。

## 租户管理员与普通用户主流程

使用 `tenant.admin` 完成：

- 运营总览：显示 1 名租户管理员、空知识／模型／助手／查询状态和可恢复提示。
- 成员与权限：将既有平台账户 `tenant.user` 建立为普通用户；目录显示 2 名启用成员和固定中文角色。
- 数据：建立 `M6 验收知识库`，上传并发布合成退款政策文本；建立并启用租户范围 Chat 和 Embedding 配置。
- 索引：使用 `M6 租户嵌入配置` 构建并发布活动索引，页面显示 6 个文本分块。
- 治理：查询用量、脱敏审计和受限评测空状态均可理解，未展示问题正文、回答正文或内部定位信息。
- 问答：明确提示当前没有已发布助手，不允许在缺少助手版本时提交问题。

使用 `tenant.user` 重新登录后，导航只显示“数据”和“问答”；问答页同样显示缺少已发布助手的可恢复空状态。

本地数据库回归后事实计数：

| 事实 | 数量／状态 |
| --- | --- |
| 全局账户 | 3 |
| 租户生命周期 | 1 个 `ACTIVE` |
| 租户成员／固定角色绑定 | 2 / 2 |
| 平台连接／平台部署／平台目录 | 1 / 2 / 2 |
| 租户模型配置 | 2 个 `ACTIVE` |
| 知识空间／文本来源 | 1 / 2 |
| 索引版本／分块 | 1 个 `ACTIVE` / 6 |
| 助手／查询运行 | 0 / 0 |

## 物理窗口、缩放与键盘回归

- 物理 `1024×768`：通过。Chrome 窗口截图实际尺寸为 1024×768；桌面侧栏、页头、成员表格和操作区可见。
- 物理 `390×844`：未完成。当前 macOS Chrome 的物理窗口最小宽度为 500px，操作电脑插件不能把窗口继续缩到 390px；未用 CSS 推断、DevTools 模拟或旧截图替代。
- 物理 `500×844`：作为当前工具可达到的最窄补充回归通过；导航收敛为底栏且仍有可访问名称。
- Chrome `200%`：在物理 `1024×768` 下通过；地址栏辅助功能树明确显示“缩放比例：200%”，问答页标题、说明、空状态和底栏仍可操作。
- 极端 `500×844 + 200%`：只登记为压力风险，不计为通过；有效 CSS 宽度约 250px，长标题区域出现物理裁切。
- 键盘：`Tab`／`Shift+Tab` 能在成员操作间移动焦点，`Enter` 打开角色对话框，焦点进入对话框，`Escape` 关闭并返回页面。
- `prefers-reduced-motion`：通过 macOS“辅助功能 → 动态效果 → 减弱动态效果”真实开启后，在 Chrome 执行成员到数据页面切换，没有白屏或空白中间帧；完成后已把系统设置恢复为原先的关闭状态。

## 截图证据

| 文件 | SHA-256 | 说明 |
| --- | --- | --- |
| `project/evidence/artifacts/WP-0014-members-1024x768.jpeg` | `fd27c493a6aa5c5689b567b56b9e608c35294c363fedd537e1d75655c230df08` | 物理 1024×768 成员目录 |
| `project/evidence/artifacts/WP-0014-members-500x844.jpeg` | `111eb54b5a783c3aeb7c1483773f633111f2a13aa2ec4eaf54666c5d87bad676` | 当前工具最窄物理窗口 |
| `project/evidence/artifacts/WP-0014-members-keyboard-focus-500x844.jpeg` | `c39d742cf629648ff2c94568beb214bad32ea86529f9453e3476e599324d20e8` | 键盘焦点 |
| `project/evidence/artifacts/WP-0014-members-500x844-zoom-200.jpeg` | `f3b1591dd325e3aa7df60075ab30ce713d5d4098f15959bce907eb63662decff` | 极端 500×844、200% 裁切风险 |
| `project/evidence/artifacts/WP-0014-tenant-user-query-1024x768-zoom-200.jpeg` | `a02a7a648df934a47a93eab885ecbe14a8c1b09343925c2bc4d51a2c767ee12a` | 物理 1024×768、Chrome 200% |
| `project/evidence/artifacts/WP-0014-macos-reduced-motion-on.jpeg` | `9e22c380ce1db269ba4fba19ef38f366effe4cbeaa4990ef703a35f29a3c4da8` | macOS 减弱动态效果开启 |
| `project/evidence/artifacts/WP-0014-data-reduced-motion-zoom-200-500x844.jpeg` | `41787493c09e0eee2044b89db35e7aebb3f4a099985c1b1fb3735e39b507646d` | reduced-motion 页面切换压力组合 |
| `project/evidence/artifacts/WP-0014-tenant-user-query-empty-1024x768.jpeg` | `15e0c1e0e952a64382f728233e402407ab5477963f83f5ef821e7a62fdb0d5fc` | 普通用户问答空状态 |

`WP-0014-members-500x844-zoom-200.jpeg` 只保留为极端裁切风险证据，不作为验收通过证据。

## 自动门禁

执行：

```bash
make validate
```

结果：退出码为 0。项目治理验证、后端架构债务门禁、前端产品门禁、Maven 全量测试、真实 PostgreSQL／Redis／MinIO／pgvector 集成路径和运维质量脚本均通过；前端 typecheck、lint、26 个测试文件的 129 项测试及生产构建通过。

提交前执行：

```bash
make project-verify
```

结果：退出码为 0；9 项项目治理测试通过，14 个工作包校验通过，当前前沿仍为 `WP-0014`。

## 未闭环问题与下一切片

1. 租户导航没有助手建立、版本冻结、测试和发布入口。后端已有助手能力，但当前 SPA 无法从页面建立已发布助手；可信问答、引用、拒答、反馈和受限评测因此不能完成 UI 主流程。这是下一纵向切片的首要产品缺口。
2. 首份文本已发布但在索引前离开向导后，返回页面不能选择并继续既有文本来源，只能重新上传。为完成索引，本次建立了第二个同内容合成文本来源；不得把该恢复体验登记为通过。
3. 物理 390×844 仍待能够把 Chrome 窗口宽度降到 390px 的真实浏览器环境人工复核。
4. 当前只是共享壳、账户、租户、成员、数据、模型和部分空状态的逐页证据，不能声明全 SPA 已达到人工验收标准。

## 代码图谱与工程助手

- 当前插件运行环境：Codex 桌面端；能力来源为本机 `engineering-assistant` 插件技能和代码图谱 MCP。
- 显式使用：`engineering-assistant:repo-context`、`engineering-assistant:diagnosing-bugs`、`engineering-assistant:tdd`；操作页面显式使用 `computer-use:computer-use`。
- 代码图谱探索 `ProductShell`／`NavigationButton` 并对导航修改执行影响分析；结果把修复限制为共享按钮的无障碍名称和对应 UI 测试。
- 模型主流程前再次探索确定性供应商探测、发现、部署和运行时装配；结论证明 `memory:` Endpoint 不访问网络，确定性 Chat／Embedding 能用于本地完整供应链，但不能代表真实模型质量。
- 本切片不产生独立 `StageRunResult`，阶段结果校验不适用；红绿命令、真实浏览器、数据库事实和项目门禁共同构成证据。

## 当前结论

紧凑导航缺陷已修复，平台到租户的账户、租户、成员、模型供应链、数据发布和索引主流程已在真实 Chrome 完成；物理 1024×768、200%、键盘和 reduced-motion 已续证。助手管理入口、首份文本恢复路径和物理 390×844 仍未闭环，因此 `M6 / WP-0014` 必须继续保持进行中，不得进入 `M7`，也不得请求人工接受。
