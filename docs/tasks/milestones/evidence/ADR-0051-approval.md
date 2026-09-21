# ADR-0051 人工接受记录

## 决策

用户于 2026-08-05 接受 ADR-0051 方案 A：evaluation-operations 拥有私有、版本化且首批仅限
`SYNTHETIC` 的 `EvaluationDataset`；`quality-gate-v1` 冻结 `Recall@10`、引用正确率与证据不足时
正确拒答率，并以失败关闭的 `EvaluationRun` 事实供 ADR-0050 的 `ReleaseDecision` 消费。

## 授权范围

- 可在 WP-0014 内按测试先行实现私有评测数据集、质量门禁、最小运行事实和与草稿修订匹配的发布决定。
- 实现必须证明评测正文只在 evaluation-operations 私有存储中存在，且跨租户、空样本、错误引用、错误拒答、
  不可计算指标及执行失败均不能产生通过结论。

## 仍不授权

本次不授权非合成评测数据、正文读取接口、货币预算、人工例外、延迟或容量 SLA、审计导出、平台运营工作台、
M7 或 M8 实现。ADR-0048 的真实部署 HTTPS Cookie、跨页签注销与自然到期证据仍须在正式证书基础设施中完成。
