# WP-0017：租户目录 OpenDesign 修订接受

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0017 |
| 范围 | `platform-tenants` 的事实文案、四列目录、状态、资格摘要与局部响应式修订 |
| 修订基线 | cf1e37688caa27f0a3e82a44912524a6e4e9804f |
| 已接受原型提交 | 9a1747609d41bf546e78486954539cd4b79fbbb9 |
| 接受依据 | 用户 2026-10-01 明确要求“修订并接受 OpenDesign 目录设计”；ADR-0090 |
| 日期 | 2026-10-01 |
| 结论 | 目录原型修订已验证并接受；生产目录切片尚未闭合，WP-0017 整包与 M6 未完成 |

## 唯一设计来源与修订范围

原 [OpenDesign 项目](http://127.0.0.1:49175/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)，项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，入口 [platform-tenants](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenants&state=success&review=0)。原项目仍写回 `docs/prototypes/wp-0016`，没有新项目或可编辑副本。浏览器链接随本机 OpenDesign 会话变化，项目、入口、完整提交及以下摘要共同固定设计。

1. 删除没有事实源的“驻留区域”列和区域搜索，保留“租户／状态／租户管理员／操作”四列。名称、编码完整显示，目录只提供查看详情及已交付开户的建立入口。
2. 摘要为“租户目录 · 生命周期摘要”“安全边界异常：尚未核实”，页头为“只读目录”。删除陈旧的 12 个活动租户和异常 0；不新增监控或区域配置。
3. 状态与筛选使用“待启用／活动／已暂停”。管理员按合成成员事实区分“已分配／未分配”，停用资格仍归原租户；未知状态不冒充活动。搜索上限 64，状态筛选有明确的可访问名称。
4. 保留主题、导航、标题、标准分页与七态。目录名称、编码允许完整换行，事实行按内容增长；窄屏控件为吸顶导航预留滚动空间。新增 CSS 仅作用于 `main[data-screen="platform-tenants"]`，未更改其他页面或共享规则。

| 原项目文件 | 最终 SHA-256 |
| --- | --- |
| full-platform.js | `8ac4ea977b1b8fdbffa50171a6365896730e5ae1d80256acc2515fd0cf17a2ce` |
| page-fixtures.json | `5e0bb8ec3db2713966615958050a5bf42a79dc84f00a9e352fec8a32e8a2e24e` |
| prototype.css | `6a0559abb3f40ced67559302ec1e64a3ac6e4e91ab5d49d55a74e4df7d31e53a` |

原 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f` 继续绑定原页面／操作清单和历史人工接受；共享移动布局的 `47c4f6f31823617633ce0bac98074208781f5565` 不被改写。新提交只替代目录设计的上述范围，tokens、页面清单、操作账本及模拟存储结构未修改。

## 实际验证与失败保留

- 最小设计契约首次为 3 项预期失败、1 项通过；最终目录契约、分页及全页面渲染测试合计 **9/9**。调用原产品渲染函数，不复制模板作为断言来源。
- 真实 Chrome／Playwright 完成 **11/11**：三尺寸正常与 200% 文字、三尺寸七态共 21 个状态组合、43 行合成夹具的 10/20/50/100 分页、跳转、筛选、空结果、刷新与历史、零资格摘要、键盘详情导航、建立入口取消和 reduced-motion。逐控件中心命中，完整名称／编码、事实行零重叠、文档级横向溢出为 0。不是生产浏览器验收。
- 人工查看三尺寸正常、三尺寸扩字及 reduced-motion 临时截图。最初人工发现名称省略和编码／状态拥挤，补充严格断言得到 **7 项失败**；局部换行、行高及滚动留白修订后全部通过，没有降低容差或移除步骤。
- 对基线的 **77 个非目标页面、539 组七态输出**逐字比较，差异为 0；其他页面夹具不变。CSS 保留基线全部原文，仅追加目录选择器，未扩大到共享组件。
- 首轮观测误用了另一背景色，已依据未修改的 `tokens.css` 确认原基线 `#f5f4ed`；空结果说明行也被误计为业务行，修正为业务行选择器并保留总数 0 断言。失败日志保留，不记为产品通过。
- 自动生成尝试实际返回认证失效，`deliverableValid=false`、无产物；另一入口缺少配置，未启动。没有把运行的表面成功状态作为验收。实际修订通过 OpenDesign 原项目文件编辑接口完成，不安装或升级运行环境。

所有日志、比较脚本和截图只在 0700 的 `/private/tmp/wp0017-tenants.Nby8Vthj`，截图没有进入仓库。关键非秘密证据摘要：

| 临时证据 | SHA-256 |
| --- | --- |
| opendesign-directory-red.tap | `b7490345f65dfbe9c4e0d4443840defeef84450c22b02f196ef9903dc3f0b184` |
| opendesign-directory-layout-red.tap | `43ef19f136adb58549911f42d71f0d9ea62bd142ff13f23b117a8941eb438f85` |
| opendesign-directory-green-accepted.tap | `0a1e24ce3fa1067b2ab13a04a1721719850ca88760a738f510732d5cfb73862f` |
| opendesign-directory-browser-accepted.tap | `e1cb1c55d9754221dba98a016573c4742b8cc813b42b8b7f73d94fbc97134d37` |
| opendesign-directory-scope-accepted.json | `35ba1d86f05e0551ad65e746dac6e4f4c7fd5d18e6d2327a65cc7a5ec8fbc519` |

可复验命令：`node --test docs/prototypes/wp-0016/scripts/tenant-directory-design.test.mjs docs/prototypes/wp-0016/scripts/directory-pagination.test.mjs docs/prototypes/wp-0016/scripts/stage-three-render.test.mjs`。浏览器使用相同原项目链接作为 `PROTOTYPE_BASE_URL`，`DIRECTORY_ARTIFACT_DIR` 指向新的 `/private/tmp` 目录，以实际 Playwright `NODE_PATH` 运行 `node --test --test-concurrency=1 docs/prototypes/wp-0016/scripts/tenant-directory-design.browser.test.cjs`；截图路径校验拒绝仓库目录。

## 治理、方法与生产前沿

`make project-verify` **69 项**及控制面验证、`make architecture-guard` **38 项**及实际门禁、`project-status`、`decisions-sync`、`resume-context` 通过。手工列表分页债务仍为 3，不描述为全仓无债务。历史校验先正确拒绝保留原型正文变化；核对原始 Git 对象和 SHA 后，只把两项 `keep` 改为现有 `update`，为 CSS 现有更新补充依据。全部 650 项来源、原摘要、旧证据与校验逻辑保留。STATE、fixture、前后端业务源码均未修改，本轮不部署、不访问生产凭据或数据库。

Codex 本地工程助手显式加载 `wayfinder`、`tdd`、`code-review`，按内联任务执行；没有 Runtime 或 `StageRunResult`，阶段结果校验不适用。代码图谱探索 `renderPlatformTenants` 并分析 4 个受影响符号，定位页面装配与输出；因此只修订目录渲染、夹具条目及目录 CSS，没有变更其他页面的状态标签或导航。

生产的查询与异步归属修复仍由[原执行证据](WP-0017-tenant-directory-execution-20261001.md)绑定 `90a28e1bed18fbbda628cb7831ee84982f847adb`，旧失败与部署事实不改写。下一动作是按本已接受版本承接生产表格、搜索、筛选、完整分页与只读导航，然后分别完成实际部署、功能、登录、只读事实和原型一致性。`platform-tenants:0–1` 保持未验证，本包仍为 17/21 操作、9/12 页面通过；不转入平台概览、三类入口切换或 WP-0018，不把原型接受作为生产交付。
## 空结果结构补充（2026-10-01）

实际过滤为空时发现四列表格被 `boundedDirectoryMarkup` 插入 `colspan="20"`，表头挤窄。此为原型自身 HTML 结构缺陷，不是生产视觉例外；依据本次用户已授权的目录修订，先增加独立失败断言，再通过同一原项目编辑接口，仅将租户目录空行恢复为实际四列。其他目录保留现状，按各自承接切片复核，未扩大此次设计范围。

最新固定提交为 `d36c7f46d0187c0421d4989c12f601d5c190d73f`。`object-workspaces.js` SHA-256 为 `80e5d53d47ee6daacd8e08b5f18f2cd4962c256347ad14d3f17248686180ccab`；上文三份目录正文与样式摘要未变。新的契约／渲染 **10/10**、浏览器 **12/12** 通过，新增实际空结果四列宽度检查。对 77 个非目标页面的七态、空查询及无匹配查询共 **1078** 个共用分页输出比较，差异为 0；不将首次缺少合法状态或完整合成快照的观测脚本错误计为通过。

原始 Git 对象及旧摘要核实后，历史索引该文件使用现有 `update` 处置，650 项来源仍可恢复。代码图谱确认此函数影响 4 个符号，修复按租户目录既有标记限域。补充日志和截图继续只在原临时目录；旧接受、旧失败和旧部署事实未改写。生产需对照此最新提交，完成最终部署验证后才关闭目录切片。
