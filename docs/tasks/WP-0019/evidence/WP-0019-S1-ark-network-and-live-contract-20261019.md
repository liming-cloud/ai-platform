# WP-0019 S1：方舟出站网络修复与最小真实发现合同

执行日期：2026-10-19（Asia/Shanghai）。本次仅闭合已授权的本机网络修复，并执行一次新方舟生产适配器合同。**网络与认证可用；非空发现、生产端到端与 S1 尚未通过。** 没有进入 S2～S6，不提升 WP-0019 或 M6 完成事实。

## 输入、授权与边界

- 起始提交 `9844b4fdf542e159ebeee320e1ade9f0df00039e`，main，工作区干净；Charter、ROADMAP、STATE、工作包及十二项直接 ADR 已复核，`make project-status` 通过。产品前沿仍为 WP-0019／S1。
- 用户指定本地 `AccessKey.txt` 为方舟独立发现来源；普通文件、当前用户所有、0600、Git 忽略。`pwd.txt` 继续为已验证登录来源，无需重新提供密码。没有打印、提交或计算凭据摘要。
- 用户先批准 `ark.cn-beijing.volcengineapi.com`，查明真实 CNAME 链后另行批准 `common-dispatch.top.cn-beijing.volcsdns.com` 与 `origin.volcengine-opentop-tobiaas.lftobiaas.volcsdns.com`。只增加三个精确 fake-IP 排除项，不增加通配符，不改全局 IPv6、代理规则、TLS 或 SSRF。
- 合同使用当前生产 `VolcengineArkPlatformProviderModelDiscoverer`、签名器和 `PublicHttpsEndpointPolicy`。源文件与运行后端 `e0224fa9847fdaf297b3ad896e37ae7796e29a50` 比较无差异；外部驱动的固定源码标识为 `d583893cd28e435d0d39d1726b11628fb854afac`，不把文档 HEAD 当运行版本。未部署或重启 Docker，前端及后端运行版本沿用[前轮部署证据](WP-0019-S1-deployed-disabled-and-form-parity-20261019.md)。

## 复现、精确修复与回归

首个精确域名补丁通过 JS 与 Mihomo 原生校验，热重载返回 204。IPv4 恢复公网地址，但系统解析仍混入 `fdfe:dcba:9876::4`；生产策略真实拒绝，凭据解析次数 0。直接 AAAA 与系统查询证明，假地址来自上述未排除的 CNAME 别名，不能将根域名成功视为整个解析链已恢复。

第二份候选仅增加两个经批准别名；移除新增行后与当时原文件逐字节一致。JS 与原生校验通过，备份后原子替换 `profiles/Script.js` 和 `clash-verge.yaml`，通过现有 Unix 控制接口热重载，返回 204。系统解析只含公网地址；同一生产策略从 `REJECTED` 转为 `ALLOWED`，没有使用 IPv4 强制、地址绑定、跳过 TLS 或修改安全断言。[对应版本的 Mihomo 实现](https://raw.githubusercontent.com/MetaCubeX/mihomo/v1.19.29/dns/middleware.go)在 A／AAAA 分支前检查精确主机过滤；本地观测确认了别名查询的实际影响。

两批原始配置分别保存在 `/private/tmp/wp0019-ark-live-20261019-kjiq94yl/network-backup/`、`cname-network-backup/`，目录 0700、文件 0600。回滚按相反顺序恢复对应两个文件，再通过现有控制接口加载；备份可能含本地网络秘密，不纳入仓库或公共摘要。没有清空 DNS 缓存、重启应用、重启 Docker 或删除数据。

## 一次真实合同的结果

| 项目 | 实际结果 |
| --- | --- |
| 动作／范围 | `VOLCENGINE_ARK_CONTROL_PLANE`／`ListEndpoints`，`cn-beijing`；一次逻辑发现，分页与限额由生产适配器负责 |
| 生产出站策略 | `ALLOWED` |
| 适配器结果 | `succeeded=true`，`VOLCENGINE_ARK_DISCOVERY_OK` |
| 候选数 | **0**；不得推导模型能力、候选分类或“该账号全区域没有模型” |
| 合同断言 | `AUTHENTICATED_EMPTY`，进程退出 2；**非空成功合同未通过**，不是跳过后报绿 |
| 秘密处理 | 仅解析 AK／SK 两个发现引用，已关闭；不解析推理 Key，不回显原始响应或授权头 |
| 外部／业务影响 | 推理 0、发布 0、业务数据库写入 0；没有自动重试，不重复旧连接发现或修订 4 发布 |
| 正式生产端到端 | `NOT_RUN`；独立适配器合同不替代平台表单、会话、保存与发现流程 |

执行前针对性 Maven 回归为 `VolcengineArkPlatformProviderModelDiscovererTest` 3 项与自动配置 1 项，失败／错误／跳过均为 0。命令为：

```text
./mvnw -q -pl backend/extensions/model-providers/provider-openai-compatible -am -Dtest=VolcengineArkPlatformProviderModelDiscovererTest,OpenAiCompatibleProviderAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

外部 `ArkLiveContract.java` 驱动以生产类路径编译，先执行无凭据 `--preflight`，放行后才以受保护文件执行一次 `--live`。结果为 `live-ark-contract.json` 与 `live-execution-summary.json`。十项非秘密源码／结果清单在 `safe-evidence-digests.json`，摘要 `4b976f90a555a9a1a922f15b2e86a507bc54002c86dbdd92dd6b5cc23ae304bd`；排除凭据及摘要、完整网络配置／备份、授权头和截图。

## 剩余前沿

1. 网络和当前 AK／SK 认证已验证，不再以这两项为阻断。当前精确区域只得到零候选，非空解析合同尚缺事实；未经授权不扫描其他区域、创建厂商资源或扩大调用。
2. 既有平台连接仍是旧 `OPENAI_MODELS_API`，没有独立发现配置；当前正式创建方舟连接需要推理 Key 与独立 AK／SK，现有材料只覆盖后者。没有解密数据库推理 Key、伪造凭据或改造接口绕过表单。正式生产发现尚未执行，不能把本轮适配器结果当作页面端到端通过。
3. 下一步继续 S1 分页禁用外观、其余页面级七态及真实空态的原型对照；不删除现有对象造空库。当前唯一原型仍为原 OpenDesign Cloud 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`、已接受 `cce686e4634ca3c523e4c8aeffd95752f1a04c3a`，没有改动或副本。
4. 最近已查的 `9844b4fd` [GitHub Actions](https://github.com/liming-cloud/ai-platform/actions/runs/39193933865)步骤启动 0，账户付款／spending limit 导致未运行；不是产品测试失败。后续提交须绑定自己的 CI 结果。

Local Codex 使用工程助手 `wayfinder` 的当前安装技能与 HITL 规则，内联执行，无 Runtime／StageRunResult，阶段校验不适用。代码图谱先定位生产发现服务、适配器、秘密容器和出站策略，据此限定为既有公开端口合同；没有修改生产源代码或公共契约。
