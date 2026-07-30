# WP-0011 浏览器验收续证与运行前置条件

## 范围与绑定

本记录绑定实现提交 `3f116ba34`。它修复受治理数据控制台验收中两个已复现的契约缺口，并如实登记本地 Compose 的浏览器验收进展与尚未满足的运行前置条件；不表示 WP-0011 或 M3 已完成，也不构成进入 M4 的人工评审结论。

- 本轮只推进 WP-0011；没有新增数据源类型、外部端点、对象位置、凭据输入或索引状态机语义。
- 所有浏览器操作均使用既有本地演示知识管理员会话与合成数据。未读取、输出或提交 API Key、主密钥、CSV 正文、对象键、内部位置或追踪标识。

## 红绿修复

- 红灯：将结构化数据集浏览器上传产生的 `text/csv`（未显式携带 `charset`）代入 `GovernedStructuredDatasetControllerTest`，登记请求因控制器将其误判为非 UTF-8 CSV 而被拒绝。
- 绿灯：提交 `3f116ba34` 后，未声明字符集的 `text/csv` 进入既有严格 UTF-8 字节解码；显式声明非 UTF-8 字符集仍关闭失败。该改动不放宽 CSV 正文编码和 RFC4180 输入校验。
- 红灯：请求不存在的控制面读取路由时，全局异常处理器错误返回通用 `500`。
- 绿灯：同一提交将 `NoResourceFoundException` 映射为不暴露路由和内部异常的稳定 `404`；控制台据此显示可恢复的模型运行时未启用提示，而非伪造索引创建成功。

## 本地浏览器验收

- FILE：在本地 Compose 中，以既有知识管理员会话完成合成 FILE 的登记、探测、同步与显式发布。页面只呈现数据源状态、同步产物和质量结论；正文、对象位置、摘要和凭据均未出现。同步完成本身不会使产物自动发布。
- STRUCTURED_DATASET：以浏览器实际产生的 `text/csv` 上传合成 CSV，登记成功并进入 `READY`；页面没有回显 CSV 正文或私有对象位置。随后以仅使用 LF 行结束符的临时输入尝试同步，按严格 RFC4180 输入契约关闭失败。该失败不是多记录规范化规则的变化：成功解析后的多记录规范化文本仍固定以 `\n\n` 连接。
- 索引：在默认无受控模型运行时配置的本地 Compose 中，创建索引操作得到已脱敏的可恢复提示。该结果只证明缺失依赖时失败关闭和界面恢复，不证明 Embedding 构建、索引发布或重建成功。
- OBJECT_STORAGE：默认部署未提供平台对象范围和正文映射，因此对应 HTTP 入口按条件装配缺席。没有用硬编码范围、对象标识、内部位置或前端参数伪造浏览器成功。

## 自动化与全量验证

在提交 `3f116ba34` 上执行并通过：

```bash
python3 -m unittest scripts.quality.test_backend_architecture_guard
python3 scripts/quality/backend_architecture_guard.py verify
./mvnw -q -pl backend/contexts/knowledge-data -am -Dtest=GovernedStructuredDatasetControllerTest,FoundationExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
make project-verify
make validate
```

`make validate` 通过后端全量测试、前端类型检查、lint、43 项前端测试与构建，以及演示、模型验收和运维质量脚本。

## 尚未满足的 M3 验收条件

1. 平台运维须在不向本工作区暴露密钥的前提下，提供已授权、已启用且可用的 M2 `EMBEDDING` 运行时配置，才能完成 FILE 与 STRUCTURED_DATASET 的真实索引构建、发布和重建浏览器闭环。
2. 平台运维须在部署侧配置冻结的 OBJECT_STORAGE 范围与平台正文映射，并以合成对象完成登记、同步、发布及索引浏览器闭环；知识管理员接口不得接收或枚举这些内部事实。
3. 上述全部自动化、真实持久化、运行和浏览器证据齐备后，M3 仍须经人工评审明确通过，方可进入 M4。
