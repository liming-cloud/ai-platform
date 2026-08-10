# WP-0014：租户厂商连接凭据生命周期证据

## 范围

本证据只覆盖 ADR-0060 的租户 `ProviderConnection` 凭据创建与轮换，以及 ADR-0058、ADR-0059
要求的最小连接探测和供应链控制台入口。平台共享连接、凭据复用、任意 Endpoint、模型部署能力验证和
正式 HTTPS 浏览器验收均不在本切片范围。

## 红绿路线

1. 前端客户端测试先调用不存在的 `createProviderConnection`、
   `rotateProviderConnectionCredential` 与 `probeProviderConnection`，得到 `TypeError` 红灯。
2. 补齐同源会话客户端后，24 项 `ControlPlaneClient` 回归转绿，证明创建只写入受控业务字段与一次性
   `apiKey`、轮换只写入 `expectedVersion` 与新 `apiKey`、探测只写入连接修订、模型代码与受控超时。
3. 纯领域反例证明停用连接不能轮换凭据；轮换已验证连接后版本递增、状态回到 `DRAFT`，并清空成功探测
   摘要。应用反例证明无 `model:write` 不执行写入，真实 PostgreSQL 反例证明凭据写入失败时连接和幂等
   记录与凭据记录一起回滚。

## 安全与一致性边界

- HTTP 创建／轮换请求只接受一次性 `apiKey`；`CredentialReference` 由服务端根据连接标识分配，既不在
  请求体、响应体、前端状态、审计摘要或日志中出现。
- `ProviderConnectionCredentialProvisioning` 和
  `ProviderConnectionCredentialRotation` 返回不可变领域操作结果。应用层只恢复令牌派生上下文、执行权限和
  乐观修订检查、编排事务并提交结果；仓储只映射和原子提交。
- 新秘密由 `provider_connection_credential` 信封加密存储；运行时才按内部引用短暂解密。旧环境引用保持为
  兼容性读取路径，用户首次显式轮换后才转入连接自有密文，不会把旧秘密重新发送给浏览器。
- 轮换使连接修订递增、探测摘要失效；旧修订的模型发现和成功能力验证不能继续作为可用性依据。成功能力
  验证有效期仍固定为 ADR-0060 已确认的 `PT24H`。

## 控制台与部署烟测

- `ModelProviderWorkspace` 使用服务端分页表格显示连接和发现历史，不将多条记录平铺为卡片。
- 新建连接、轮换 API Key 和最小连接探测均在 `Dialog` 中按需打开；API Key 在提交前后立即清空，列表只
  回读安全摘要。只有状态为 `VERIFIED` 且已配置凭据的连接可以触发模型发现。
- 使用基础、真实模型和浏览器验收三层 Compose 覆盖启动本机环境；所有服务健康，
  `https://127.0.0.1:13443/` 返回 `200`。本机 TLS 采用 `--insecure` 仅验证同源入口可达，不安装 macOS
  System keychain CA，也不将其视为正式证书或浏览器 Cookie 证据。
- 当前执行环境没有可调用的浏览器控制连接器，因此未登记人工浏览器表单操作；这不影响自动测试通过，
  但 M6 不能据此标记为人工评审完成。

## 可复现验证

```bash
./mvnw -q -pl backend/contexts/model-runtime -am clean test
cd frontend && npm test && npm run typecheck && npm run lint
python3 scripts/quality/backend_architecture_guard.py verify
docker compose --env-file deploy/.env \
  -f deploy/compose/compose.yml \
  -f deploy/compose/compose.real-model.yml \
  -f deploy/compose/compose.browser-acceptance.yml up --detach --build
curl --insecure --silent --show-error --fail --output /dev/null \
  --write-out 'HTTPS 控制台状态=%{http_code}\n' https://127.0.0.1:13443/
git diff --check
```

以上定向回归、前端类型检查／Lint、架构门禁和 Compose 健康烟测均通过。Maven 日志包含既有 Spring
条件装配分支、MyBatis Mapper 扫描和 Testcontainers 警告；没有测试失败。

## 后续

下一切片只能继续 ADR-0058 的“已验证连接 → 不可变模型发现 → 草稿部署 → `PT24H` 能力验证”供应链，
并补可写会话下的浏览器回归；不得以本证据关闭 WP-0014、M6 或开启 M7。
