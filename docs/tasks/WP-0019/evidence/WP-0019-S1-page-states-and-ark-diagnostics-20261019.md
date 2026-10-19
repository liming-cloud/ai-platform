# WP-0019 S1：页面七态与方舟零候选调查

执行日期：2026-10-19（Asia/Shanghai）。当前已闭合零候选原因的只读调查；页面级七态与全景验收仍在执行。S1、WP-0019、M6 未完成，不进入 S2～S6。本文件集中维护本轮两个子任务的独立结果，不改变历史验收。

## 固定输入与保护边界

起始 main `3f520216333576b9da3c13b89215d06f2c20b4b0`，工作区干净。Charter、ROADMAP、STATE、工作包和直接 ADR 已复核，`make project-status` 通过；输入 `8ade6bbe15a53fdf73a1fea794e642632f59b835` 为祖先。前端实际运行 `7f05664fa77fb6aef3e948279a5657ce6d3ec610`、镜像 `sha256:b15305c253aa09f3a34b8b03d2ef602d442e5000e91e2026288f509c9d3b37b2`；三后端仍为 `e0224fa9847fdaf297b3ad896e37ae7796e29a50`，七服务健康。本次调查没有部署或重启服务。

原型为 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`、已接受 `cce686e4634ca3c523e4c8aeffd95752f1a04c3a`，MCP 已恢复连接。现有数据、原型与三个精确域名排除均保留；截图仅在 `/private/tmp`。`pwd.txt`、`AccessKey.txt` 为当前用户拥有、Git 忽略的普通 0600 文件，没有打印或计算凭据摘要。

## 方舟零候选调查：已执行

用户本轮授权继续排查零候选。先用代码图谱读取生产 `VolcengineArkPlatformProviderModelDiscoverer`：它要求 `Result.Items` 为数组且三个分页计数可读取，畸形结构返回失败，不会把任意 HTTP 200 当空列表。[官方 SDK 的响应模型](https://github.com/volcengine/volcengine-python-sdk/blob/master/volcenginesdkark/models/list_endpoints_response.py)声明同一组字段；[官方认证入口](https://docs.volcengine.com/docs/ark/base-url-and-authentication?lang=zh)提供相同区域、动作与签名协议。

外部一次性 Java 诊断复用生产 `VolcengineSignerV4` 与 `PublicHttpsEndpointPolicy`，保持官方 HTTPS、TLS、禁止重定向和十秒超时；只请求 `cn-beijing` 的 `ListEndpoints` 第一页、PageSize 100。AK／SK 与当前本地来源在内存中核对后以独占 0600 文件提供；不解析推理 Key，不读取数据库加密材料。只输出计数、结构布尔值和稳定状态，原始正文、授权头、Endpoint 标识与名称均不落盘。

| 事实 | 实际结果 |
| --- | --- |
| 出站与请求 | 生产策略 ALLOWED；HTTP 请求 1；200；厂商错误字段不存在；动作匹配 |
| 响应结构 | 与生产解析器要求匹配，PageNumber 1、PageSize 100 |
| 厂商返回计数 | **Items 0、TotalCount 0** |
| 结论 | 此凭据、此区域、无筛选条件下当前可见 Endpoint 列表为空；未发现生产解析丢弃候选，不推导其他区域／权限或公共模型广场没有模型 |
| 影响 | 推理 0、业务写入 0、发布 0；秘密数组清零；正式生产端到端 NOT_RUN |

因此前轮 `AUTHENTICATED_EMPTY` 与厂商真实计数一致，非空合同仍未通过。本轮没有切换到其他动作来填充候选、扫描区域、创建云端 Endpoint 或重复旧连接发现／修订 4 发布。

正式新建方舟连接仍要求推理 API Key 与独立 AK／SK，连接凭据由连接自身拥有（ADR-0060／0070）；公开请求不接受旧凭据引用。当前受检查的 `AccessKey.txt` 只有独立发现材料，`deploy/.env` 没有匹配的方舟／模型推理 Key 字段。不得通过解密旧连接、复制引用、提交假 Key 或修改表单绕过。下一步真实非空／生产流程仍受匹配材料与可见 Endpoint 事实约束，无需重新索取平台登录密码。

证据目录 `/private/tmp/wp0019-s1-page-states-20261019-ienw2udl` 为 0700。`ark-metadata.json`（0600）的 SHA-256 为 `8324001d47e5d57b2892d28094ccf2715552a9b5ee9dec15a7ff44953c922558`；安全辅助源码为 `ArkListMetadata.java`，编译日志、输入存在性及当前运行事实分别保存。凭据文件及其摘要不进入证据清单或仓库。

## 页面七态与全景：当前前沿

初始部署观察已复现空态页头／操作及错误恢复按钮差异，尚未修复或验收通过。实际目录有数据，未删除对象造空库；目录空态仅采用明确标识的隔离响应，详情空态可由正式精确资源 404 验证。加载、错误、拒绝及 partial 的受控故障与自然生产事实分开记录；disabled 显示与真实启动配置也分别结论。不得把页面片段通过提升为完整页面一致。

Local Codex 显式加载当前工程助手 `wayfinder`、`tdd`、`code-review` 及安全 seam 约束；内联任务无 Runtime／StageRunResult，阶段校验不适用。代码图谱定位两页读取投影、详情状态与厂商解析器；对两个页面组件执行一层影响分析，后续修改限定于 S1 页头、状态面板和局部样式。
