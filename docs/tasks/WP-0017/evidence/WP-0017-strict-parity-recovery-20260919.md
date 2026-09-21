# WP-0017 严格原型一致性恢复与截图治理

日期：2026-09-19。复核基线：`50d15bb8319d3ee850eba6276d782a2a819bdcd9`。

## 结论与整体边界

用户明确要求产品结果与已验收 OpenDesign 完全一致，仓库禁止截图，推进时同步修正触及路径的代码与架构并回顾整体目标。原子开户前次 11 个开户、15 个登录及 15 项数据库事实仍是所绑定部署的功能证据；前次文字中对移动导航、页头辅助文案、间距的自行例外不满足本次要求，严格一致性重新打开，未通过前不进入下一切片。

当前仍为 M6 / WP-0017。整体顺序为平台身份与租户开通 → WP-0018 成员 → WP-0019 模型 → WP-0020 知识接入及后续顺排工作包，最终回到 WP-0014 人工验收。本轮只处理当前切片的一致性和证据治理，不开始独立管理员、设置、普通会员或后续业务。整包、66 页生产实现和 M6 均未完成。

真实身份、事务、数据、安全及不可伪造的状态继续遵循 ADR-0079/0080；原型模拟数据不作为生产事实。不得通过修改原型或放宽验收来消除差异。

## OpenDesign 固定基线

- 项目：`249f81f2-6f1a-46ff-988c-da3cdedb35e7`。
- 已验收原型：`21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`，入口 `index.html`。
- [OpenDesign 项目与文件](http://127.0.0.1:64093/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)。
- [当前预览](http://127.0.0.1:64074/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html)。

以上地址由本轮 OpenDesign 工具返回，生命周期为当前本地服务会话；失效时使用项目 ID、入口和固定提交重新获取，不把预览链接冒充历史生产验收截图。

## 截图清理与可追溯性

从当前树移除 41 张历史截图；它们全部位于 `project/evidence/artifacts/`。清理前未发现额外未跟踪截图。历史证据中的路径和摘要仍描述历史执行，并不表示当前树继续保存这些图片。本次不改写 Git 历史。需要复查时，可从下列 Git 对象恢复到 `/private/tmp`，不得恢复入库。

清理前提交为 `50d15bb8319d3ee850eba6276d782a2a819bdcd9`。如下清单只保留文字元数据：

| 历史路径 | Git blob |
| --- | --- |
| `project/evidence/artifacts/WP-0014-data-reduced-motion-zoom-200-500x844.jpeg` | `7957ce9ed83e57512c3a88c74bb3181cd8203de8` |
| `project/evidence/artifacts/WP-0014-login-prototype-1920.png` | `131f5e3c231e4b0adb7a187b30a6692a43539dee` |
| `project/evidence/artifacts/WP-0014-m6-ui/login-1440x900.jpg` | `19f2f9c52876ac9ff26604a9d39af9fb147c7f56` |
| `project/evidence/artifacts/WP-0014-m6-ui/login-390x844.jpg` | `94d7915f4cd52b7ce6a323515b363def8475c648` |
| `project/evidence/artifacts/WP-0014-m6-ui/login-platform-1024x768.jpg` | `08247287ae34405addb57cb4f3518ed5c100d926` |
| `project/evidence/artifacts/WP-0014-macos-reduced-motion-on.jpeg` | `a83b0ef13a5fac85ac69e0c4bbb47ad5af907c58` |
| `project/evidence/artifacts/WP-0014-members-1024x768.jpeg` | `247a672161cc3c7c5db837bbb722cd39f8b9efc7` |
| `project/evidence/artifacts/WP-0014-members-500x844-zoom-200.jpeg` | `ad8e0ca43b8be3c5dc29501ec4e4647eea0aced9` |
| `project/evidence/artifacts/WP-0014-members-500x844.jpeg` | `86d2ad4d7946fdb10e52b1e271716bc716679b2b` |
| `project/evidence/artifacts/WP-0014-members-keyboard-focus-500x844.jpeg` | `c64c7acc630e9a9ec9744310de2c0ee416e41a28` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/ordinary-user-ask-1024x768.png` | `aeb7f29742d9576359ca26e0ebe45ca5df1c8266` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/platform-tenants-1024x768.png` | `98ce8766afb00d94b455e4392cffc74fc3fb588b` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-assigned-1024x768.png` | `63d5cab716b5e500db0614e3fd3f12c43ff38bc3` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-dialog-1024x768-fixed.png` | `a6238e00b417728d67c8460c6e9aa6b800f06cd1` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-dialog-1024x768.png` | `0ed99ac54398c2886a01b28d47080d886d0aba08` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-dialog-390x844-final.png` | `1cfa1879ff3ab071d3e2a22c103b0b716d7a56ba` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-dialog-390x844-fixed.png` | `deb86dc9efc4d0d13fd12bf60f4381f28885ef91` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-dialog-390x844.png` | `8338bc607e7327c9c962855e14626c5f8a39eda6` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-dialog-real-200-percent-fixed.png` | `4bcbe7ecb93d98b57b8616f5078e75fd7e208482` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-dialog-real-200-percent.png` | `4ad7424af6976be40403ec5b1d406e272cbf2bff` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-last-admin-protection-1024x768-fixed.png` | `2fbd2b949d0fbd336854bbd8b8519abb9c69a686` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-last-admin-protection-1024x768.png` | `96f4402fc4104da23c19d3c8912085bc8819da03` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-last-admin-protection-390x844-fixed.png` | `7ac01e04bbb5ff480478dcfd1006ae8119ce03fb` |
| `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-last-admin-protection-390x844.png` | `73aa533ba16a7d4d573fe0be399a1f1f31a1b184` |
| `project/evidence/artifacts/WP-0014-tenant-user-query-1024x768-zoom-200.jpeg` | `06d6e9bff74d30f2e5de5e615a68345829cd3b1b` |
| `project/evidence/artifacts/WP-0014-tenant-user-query-empty-1024x768.jpeg` | `3208a4a5f16c01f9338ca36fa5e93d1e2800bdce` |
| `project/evidence/artifacts/WP-0016-open-design/assistant-release-disabled-desktop.png` | `85c484ca5d12915619921aedbbc13b3b68ecd470` |
| `project/evidence/artifacts/WP-0016-open-design/assistants-empty-mobile.png` | `ac353733dc8da2dec60cf22cd26ea99600a0cf6b` |
| `project/evidence/artifacts/WP-0016-open-design/assistants-success-desktop.png` | `5b9c06b5d8273a77216f4a3a2509de4fcded7e85` |
| `project/evidence/artifacts/WP-0016-open-design/conversation-mobile-keyboard-focus.png` | `4a9f37ab4b759f6a21debab1a23ce904fbeca09a` |
| `project/evidence/artifacts/WP-0016-open-design/conversation-partial-desktop.png` | `06e76618aad8dad6a2050f5ca111757a00c6b62a` |
| `project/evidence/artifacts/WP-0016-open-design/conversation-success-mobile.png` | `4a9f37ab4b759f6a21debab1a23ce904fbeca09a` |
| `project/evidence/artifacts/WP-0016-open-design/conversation-success-tablet.png` | `5edec27d340419dfda019d41fc531bc0a097a054` |
| `project/evidence/artifacts/WP-0016-open-design/conversation-tablet-reduced-motion.png` | `f3eb673ec50e8b199d8de09804391fe66af1f1d5` |
| `project/evidence/artifacts/WP-0016-open-design/index-detail-success-desktop.png` | `a8135566a00414183a31ce0bcabe446e94530060` |
| `project/evidence/artifacts/WP-0016-open-design/knowledge-directory-success-desktop.png` | `7da26b025f2cf5378a774645682ae7f7d018d74a` |
| `project/evidence/artifacts/WP-0016-open-design/knowledge-sources-denied-mobile.png` | `4c227a575f1073ca653f9c578ef9f78a271afb79` |
| `project/evidence/artifacts/WP-0016-open-design/knowledge-sources-error-tablet.png` | `a2baf7624d519ece4fa95dbcd8d9260b677749c0` |
| `project/evidence/artifacts/WP-0016-open-design/knowledge-sources-partial-desktop.png` | `b8331bf2dcd73d921b494ca1d9d22809520e888a` |
| `project/evidence/artifacts/WP-0016-open-design/login-success-desktop.png` | `048be1c7524e95edb0cc57c96e983e8aba804919` |
| `project/evidence/artifacts/WP-0016-open-design/login-success-mobile.png` | `a116e7a1eb54e429e60881d9fe341bbcdebf5495` |

## 门禁红绿与影响边界

新增两个反例：证据目录内未跟踪/被忽略截图，以及工作树中已不存在但索引仍跟踪的截图。旧门禁两个用例均失败，新增检查后治理单测 17 项通过；真实仓库检查按预期拒绝上述 41 张图片。产品图片资源及文字证据不被误禁。`.gitignore` 只作便利防护，`project-verify` 才负责检查索引与实际证据目录。

代码图谱已探索治理 `verify` 与 `ProductShell` 并执行影响分析。通用 `verify` 名称有多重匹配，不能把图谱返回的全部同名符号当作实际调用方；依据已定位的 `scripts/project_governance.py` 与治理测试作局部修改。共享界面修改必须继续检查真实调用方与前端回归，图谱的少量静态边不代表无跨应用影响。

本轮使用 Local Codex 提供的工程助手 `wayfinder` 和 `tdd`，依据各自 SKILL.md 执行内联导航及红绿。旧 Runtime 已完成的质量结论只绑定旧范围，不代替本轮结果；当前未产生新的正式阶段产物，阶段 schema 校验不适用。

## 尚待完成

原子开户与成功概览的严格结构、文案、间距、响应式对照，最终候选部署、11 个开户 / 15 个登录 / 精确对象只读事实及视觉复验仍须绑定新证据。不能使用本治理记录声明严格一致性完成。

## 治理收口验证

移除截图后 `make project-verify` 38 项通过，`make resume-context` 通过，当前树截图数量为 0。用户五项 fixture 暂存重命名与清理前二进制暂存差异逐字节相同。

依据工程助手 `code-review` 对固定差异复核：正确性由两个真实红灯及清理前/后仓库检查支持；复杂度为单一检查函数；没有第二套治理入口或重复状态；检查限定证据目录及 Git 文件清单，整个治理执行少于一秒；架构只扩展既有治理 seam；测试覆盖索引残留、被忽略文件与正常资源的区别。六维结论仅适用于本次截图治理，不代表尚未修正的生产视觉一致性。
