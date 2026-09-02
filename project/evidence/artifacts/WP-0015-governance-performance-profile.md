# WP-0015 治理命令性能 profile

## 候选

- 基线提交：6f4601ce1eaf71be67556f5e3361c269c634679b
- 候选摘要：sha256:f1fbf3efc646cab7f66e978a4b00c23754cb0dffd614fda159aba08dfba58ae7
- 日期：2026-09-02
- 环境：本地 macOS、仓库 Python 3、单进程

## 观察

| 项目 | 单次观察值 | 门禁 |
| --- | --- | --- |
| 14 项 project_governance 单元测试 | 0.053 秒 | 全部通过 |
| make project-verify | 0.106 秒 | 全部通过 |
| make resume-context 输出 | 3,512 bytes | 不超过 12 KiB |

## 边界

这是控制面命令的单次本地 profile，只证明本次治理变更没有引入可见的恢复上下文膨胀或测试等待。它不是生产吞吐、RAG 检索延迟、首 token 延迟或容量结论，不得用于 WP-0018 至 WP-0020 的性能验收。
