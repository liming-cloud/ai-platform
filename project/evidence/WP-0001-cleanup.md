# WP-0001 项目清理证据

## 清理基线

清理前的受版本控制文件分布：

| 范围 | 文件数 | 处理 |
| --- | ---: | --- |
| `backend/` | 1335 | 删除 |
| `frontend/` | 77 | 删除 |
| `deploy/` | 13 | 删除 |
| 旧实现文档 | 10 | 删除 |
| 旧实现校验脚本 | 3 | 删除 |
| 合计 | 1438 | 删除 |

删除内容由 Git 历史保留，可从清理前提交 `dafac7732` 及更早提交恢复查阅。

## 保留边界

当前分支保留：

- `project/` 项目控制面。
- `docs/adr/` 已接受决策。
- `docs/product/` 产品设计。
- `docs/design/` 前端设计。
- `docs/architecture/` 领域、后端、接口和数据设计。
- `docs/planning/` 交付计划。
- `scripts/project_governance.py` 与对应单元测试。
- 根说明、规则、治理构建入口和治理流水线。

## 本地文件保护

- 没有自动删除未受版本控制的 `deploy/.env`。
- 没有读取或输出其中内容。
- 没有删除 `.idea/`、`.run-artifacts/` 和其他可能属于用户的本地文件。
- 已清理旧 `backend/` 和 `frontend/` 下可重建的 `target/`、`node_modules/` 和测试产物。

## 代码图谱证据

清理前通过代码图谱确认：

- 旧仓库包含 1398 个已索引源码文件。
- 旧后端由多个上下文、共享模块、基础设施模块和单一启动模块组成。
- 旧前端菜单依赖后端授权菜单契约。
- `AiPlatformApplication` 的影响范围集中于旧启动模块和对应测试。

以上能力只作为旧需求和反模式证据，不进入当前绿地源码边界。

## 自动验证

```bash
make project-verify
make project-status
make resume-context
git diff --check
```

项目治理脚本在 M0 阶段会拒绝重新受控的 `backend/`、`frontend/`、`deploy/` 和旧实现文档。
