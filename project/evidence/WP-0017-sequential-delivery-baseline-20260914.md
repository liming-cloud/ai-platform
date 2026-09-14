# WP-0017：顺排实施与原型承接基线

## 范围和结论

本证据只覆盖控制面重排和原型承接守卫，不表示 WP-0017 或 M6 生产能力完成。用户已确认顺排编号、实际平台验收会员及执行完整计划；未创建新开发任务、实际会员或修改原型。

调查基线：分支 codex/trusted-rag-foundation-v1，HEAD 83015cede7016f2aefda1f91fb388b24726c923f。lastVerifiedCommit a708fdfc67a043648b2aede81a8989db852c1a64 是其祖先。5 项用户暂存的夹具重命名保持原样，不纳入本次提交。

## 变更

- ADR-0079 接受 WP-0017～0026 顺序、旧编号映射、原型承接、已确认登录策略及真实会员验收责任；旧 WP17/18/19/20 转入 WP20/22/24/26。
- 78 页/155 操作逐项映射：66 M6 页；132 组生产承接操作、3 组演示专用、3 组用户暂缓、1 组禁止、16 组后续阶段。操作分组不等于 API 数量。
- 来源索引重建/同步索引归 WP-0022，快速发布归 WP-0023；EXTRACTED 不伪装 READY，评测/助手/会员复用同一流式执行能力。
- STATE、总控、路线图、追踪与有效前端说明同步；不删除历史过程或人工验收事实。

## 测试和证据

实际红灯：新增 scripts/quality/test_m6_implementation_map.py 后执行 python3 -m unittest discover -s scripts/quality -p 'test_m6_implementation_map.py'，因承接文件不存在失败。之后补最小矩阵通过；进一步验证漏页、漏操作、重复、错误来源、误分类和跨包错误归属。

最终验证：`make project-status` 通过，前沿为 WP-0017；`make project-verify` 的 35 项测试与 26 个工作包校验通过；`make architecture-guard` 通过，当前仍有 15 处 Java 内联 SQL、3 处手工分页，未将存量债务写成已清零。`make resume-context` 输出 5,562 字节，低于 12 KiB；`git diff --check` 通过。上述只验证顺排基线，不表示 WP-0017 业务验收通过。

独立评审复现了不存在的 40 位 SHA 和借用原型人审证据两类假完成漏洞，新增失败用例后修复。守卫核验引用提交实际存在且属于当前 HEAD 历史，并要求 `INDEX.md` 的 `## WP-xxxx` 段落按责任包、证据路径和提交绑定生产验收；只存在文件或原型报告不够。共 21 项矩阵正反例与 14 项原有治理回归通过。

## 工具与边界

Codex 已安装工程助手，实际路由 wayfinder/proceed/inline，显式加载 engineering-assistant、wayfinder，承接守卫使用 tdd。使用 Charter、STATE、当前 WP、相关 ADR 和原型台账；未产生 StageRunResult，校验不适用。

代码图谱读取 TenantProvisioningService、PlatformAccountEstablishmentApplicationService、PostgresPlatformAccountStore，确认当前仍为已有身份开户、无角色平台建号和手工分页；后续按小切片治理，不借本证据宣称生产功能完成。

## 提交

本文件随实现提交；索引将在实际提交产生后绑定准确 SHA，不使用未来或虚构提交。
