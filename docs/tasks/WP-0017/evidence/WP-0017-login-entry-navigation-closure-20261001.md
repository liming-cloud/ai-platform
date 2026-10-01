# WP-0017：三类登录入口导航部署与原型一致性收口

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0017 |
| 验证范围 | `tenant-login:1`；三类入口导航、已有身份保留及导航原型一致性 |
| 实施基线 | e35aebbda3cf3ecb86040aa06fd07abd0d815546 |
| 合同提交 | 3cc3eee70f4068e4efc9ec023d4648dc8aff6b76 |
| 验证提交 | ee34520f3f00098634fd749992aa533c81eda139 |
| 合同／日期 | ADR-0091；2026-10-01 |
| 结论 | 三入口导航独立切片闭合；普通成员完整登录、WP-0017 整包及 M6 未完成 |

## 交付与实际运行

按用户“保持 M6 范围，先完成登录入口切换”实施，已验收 OpenDesign 保持不变。匿名 `/login`、`/tenant/login`、`/ask` 各展示另外两个原生链接，顺序与原型一致；点击不认证、不登出、不增权，密码不继承到下一页、地址或浏览器存储。已有会话访问错角色管理员入口仍显示身份不匹配；进入问答路径保留原有 HttpOnly 会话及服务端角色、工作区和能力。普通成员建立和完整问答登录仍由 WP-0018、WP-0024 负责，平台概览的 M8 依赖未实施。

| Compose 服务 | 实际 OCI revision | 镜像 ID | 状态 |
| --- | --- | --- | --- |
| console-web | `ee34520f3f00098634fd749992aa533c81eda139` | `sha256:ad41bb3be7f15e0056ceb2e760ce55bb27f957f63ee8b0945e551185ba54e947` | healthy |
| control-plane | `90a28e1bed18fbbda628cb7831ee84982f847adb` | `sha256:cc5ca1f77a04a197de00578c5d9c510ac37d315c8dbbbf9ccb73c12d54cfdc18` | healthy |
| query-runtime | `ef11d6aad0ffb8d8a656d5a17a3477d93b95a5c8` | `sha256:4a847f9631eee38204096f9ea66c159dcfcfbb25f17406ffcecab473795d5c5d` | healthy |

镜像从固定源码构建并写入 revision，仅对 console-web 执行正常 Compose `up --detach --no-build --no-deps --wait`。四入口烟测通过，最终 7 服务健康。后端、查询进程、数据卷和 OpenDesign 没有更新，不用文档收口 HEAD 替代运行版本。

## 真实浏览器与只读业务事实

真实 Chrome／Playwright `1.62.1` 在最终镜像串行执行：

- `make acceptance-work-package WP=WP-0017 SLICE=login-entries`：**18/18**。三入口直接访问、六个切换方向、密码不继承、前后退／刷新、两种既有身份保留、三入口键盘操作及三尺寸链接裁切／遮挡检查通过，业务 API 写请求 **0**。匿名导航同时证明没有自动认证或退出；已有会话只在内存比较，不保存 Cookie、Token 或其摘要。
- `make acceptance-work-package WP=WP-0017 SLICE=login`：**15/15**。平台／租户真实认证、指定租户、错角色、刷新、Cookie/CSRF、退出、键盘、文字放大和 reduced-motion 通过。没有重跑开户或准备新业务对象，旧凭据文件没有覆盖。

前后使用同一 `REPEATABLE READ READ ONLY` SQL，13 项非秘密事实逐字相同，SHA-256 均为 `9a96acd6703ba0b64ba45105a111d4d00a9755fce5aceb5909ce96fd46ccf096`。目标 tenantId `2104457357567197185`、subjectId `2104457357567197186`：生命周期 `ACTIVE/1`、主体 `ACTIVE/0`；管理员及启用管理员各 1、其他租户管理员资格 0；配额／功能／安全版本各 0、生命周期事件 1。全库租户 70、身份 189、平台账户业务审计 126。没有读取密码、摘要或秘密字段。

## 固定原项目与导航视觉范围

唯一来源为[原 OpenDesign 项目](http://127.0.0.1:49175/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)，项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，入口 [tenant-login](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-login&state=success&review=0)。原入口与操作固定 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`，共享移动布局 `47c4f6f31823617633ce0bac98074208781f5565`；当前原项目资源逐字匹配最新已接受版本 `d36c7f46d0187c0421d4989c12f601d5c190d73f`，本轮没有原型修订。

| 原项目资源 | SHA-256 |
| --- | --- |
| full-platform.js | `8ac4ea977b1b8fdbffa50171a6365896730e5ae1d80256acc2515fd0cf17a2ce` |
| prototype.js | `915695859beb29cde8b34a050b85c20117a000c27cdccfad0df24923127349d9` |
| prototype.css | `6a0559abb3f40ced67559302ec1e64a3ac6e4e91ab5d49d55a74e4df7d31e53a` |
| tokens.css | `ad0cd644358eb724f3aa2261b2d86fe357bf10e7603bc3d21295132177d55385` |
| page-inventory.json | `bb53681c82e4b2f445316107ad689590660472aab550e7fc930aac72f9fd07c8` |
| ACTION-LEDGER.json | `77ff6446f4707f0eb2fd46d3d546d1f638270565281a756ba9507713f3a4316f` |

每资源直接从原项目读取，与上述固定 Git 对象及工作树相同，没有复制为另一可编辑项目。

最终 **21 组导航对照**：三入口 × 三尺寸正常／200% 文字，以及三个 390px reduced-motion。比较导航高度、两个链接相对几何、文字／顺序、字体、字号、字重、行高、颜色、下划线、导航外距／内距、边框及间距；几何容差保持 **1px**，样式精确相同。链接中心命中、无横向溢出，范围内差异 **0**、写请求 **0**、页面异常 **0**。两个管理入口另外核对导航与前区块的间隔，全部通过。

问答整页属于 WP-0024，父容器与说明区尚未承接。本次仍保存其 7 组父容器间隔测量，但不将这些不同语义的前区块当成导航组件的同一几何对象；导航本身的全部字段继续严格比较。该界定在实施前已由 ADR-0091 固定，不新增视觉例外，也不宣称问答整页一致或 `login:0` 完成。管理登录整版结论仍沿用其原独立切片范围，本记录只证明新增导航。人工查看最终移动扩字、平板／桌面管理页及问答导航对照，确认链接完整换行且可操作；截图仅 `/private/tmp`。

## 失败与局部修正

新增受控入口测试先 **2 项失败**；真实旧部署在首个导航场景失败，证明缺少问答链接。新增组件实际 SSR 测试验证三个入口的两个链接及顺序，沿用原认证选择和失效逻辑，未修改后端。

第一候选 `2fede5abcfdefc03406ec7c6a05dcd32220758f8` 已通过导航与登录，但视觉对照保留 60 条差异：管理入口间隔少 16px、链接颜色解析及超出本片的问答父容器测量。只修正生产导航网格、外距和色值；实际构建将字面 `color-mix` 预计算为精度不同的 `oklab`，改为引用既有色值变量后，构建保留表达式并与原型精确匹配。最终镜像重新完整执行 18、15 与 21 组验收；不以第一候选绿灯代替最终结果、不删除失败日志或放宽裁切／字号／颜色断言。

初次 Shell 检查因工作目录处于 frontend、数据库检查因猜测用户名称而失败，均为执行问题，未算通过。随后从根目录验证，数据库使用容器实际用户／数据库且不输出环境，得到上述真实只读结果。没有绕过自动审批；本轮不存在容量拒绝造成的未执行项。

## OpenDesign 预览容器报错

用户报告的 `Cannot GET .../preview/<scope>/` 在 OpenDesign **0.21.1** 可复现。容器注入的 `base` 指向目录，原型的原生 `?screen=...` 链接因此请求没有 `index.html` 的目录；安装包的 GET 路由要求 scope 后存在文件路径。带文件名返回 200，新 scope 仍不能解决查询导航。正式 `get_project` 返回的 `raw/index.html` 原项目入口不注入该基址，真实 Chrome 点击同一链接返回 200。原项目与资源始终存在，MCP 读取已恢复。

已核对并提供正式原项目预览，不修改原型、metadata、第三方安装包或服务路由。内嵌容器缺陷仍未修复；版本绑定的复现与兼容说明在 `/private/tmp/wp0017-opendesign-preview-k4x73785/`，未向第三方发送。该服务缺陷不作为产品测试失败或修改设计的理由。

## 源码、控制面和方法

| 验证 | 结果 |
| --- | --- |
| `make frontend-test` | 最终源码 48 文件／246 项；typecheck、lint、测试、构建通过 |
| Node 工作包配置测试 | 34/34 通过 |
| Python 工作包验收入口 | 16/16，包含导航切片及 SETUP 拒绝 |
| Shell／Node 语法、固定 diff | 通过 |
| `make architecture-guard` | 38 项及实际门禁通过；依赖违规 0，既有手工分页债务 3 不变 |
| `make project-verify`／`make resume-context` | 69 项、29 任务及 650 项历史定位通过；恢复输出 12115 字节，未超过 12 KiB |
| fixture／截图 | 相对实施基线 fixture 差异 0，仓库截图新增 0 |

CodeGraph 先探索 `BrowserSessionGate`、`LoginFrame`、`ConsoleApp`、`loginWorkspaceFromPath` 与固定回跳；对入口组件和选择函数执行影响分析，分别返回 2／4 个关联符号。结果使修复限定于三个呈现入口的共享链接组件，不改变会话状态机、角色判定、回跳函数或后端接口。Cookie/CSRF、错角色及现有身份回归由真实浏览器证明，图谱不替代测试。

运行环境为 Codex 本地工程助手 `2.2.0-rc.1+codex.local-20260929-051321`，显式加载 `wayfinder`、`tdd`、`code-review`、`diagnosing-bugs`，及 wayfinder 的 HITL、TDD／评审的 security-seams 参考；使用根 AGENTS、Charter、ROADMAP、STATE、当前包及 ADR-0091 控制范围。正式 Runtime／StageRunResult 未生成，阶段结果校验不适用，未用历史 Runtime 或 simulation 宣称质量通过。

## 证据定位

日志、指标、诊断及截图仅在 0700 的 `/private/tmp/wp0017-login-entries-8z3hbcia/`；既有 0600 凭据只由受控校验器读取，不写回或复制。主要非秘密摘要：

| 产物 | SHA-256 |
| --- | --- |
| login-entries-final.log | `52f3a1dea18380641c9632a927de5e7173ef5e362993b9d1f8e46d36ac38e2aa` |
| login-regression-final.log | `4d246a9fdbd0676de9dea2201b7b08ba4cba4a9075ed435daa973ab78c8d21c0` |
| frontend-final-test.log | `13392f45ec8b4e3be1df61e6f2a61e08853176e0bfb0fbe007a5460ba10bb7c1` |
| login-entry-parity-final.cjs | `3e41f24a5c5e8760514666b01ea6acaf49d0750c5ddad140529154461de20db8` |
| login-entry-parity-final-metrics.json | `6a7788281abbdd25d50c889a73511c0fcce7eea1166ece8753a787be78f02864` |
| login-entry-parity-final-diff.json | `4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945` |
| running-final.json | `1a09e72bd7c4dfa52ae523e0cb5c40c37ccc30f318a94ac85e077b83e43f24fc` |

只更新 `tenant-login:1` 为 verified。WP-0017 为 20/21 操作、11/12 入口已验证，仍剩 `platform-overview:0`、分页门禁退出条件与整包验收。WP-0024 的普通成员完整登录状态不变，M6 未完成。
