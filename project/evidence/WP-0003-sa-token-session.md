# WP-0003 Sa-Token 会话适配器证据

## 适用反例矩阵

| 场景 | 预期 |
| --- | --- |
| 有效令牌和完整服务端会话 | 恢复原始可信请求上下文 |
| 令牌会话中的租户字段损坏 | 失败关闭 |
| 显式注销后继续使用旧令牌 | 失败关闭 |
| Sa-Token 登录标识与会话成员标识不一致 | 失败关闭 |

本切片尚未处理密码、HTTP 错误包装和浏览器异步身份替换，因此不把这些场景伪装为已验证。

## 红灯证据

```bash
./mvnw -q -pl backend/contexts/organization-access -am test
```

首次执行因 `SaTokenSessionGateway` 和稳定会话异常不存在而编译失败。

## 绿灯证据

```bash
./mvnw -q -pl backend/contexts/organization-access -am test
make backend-test
```

结果：

- 使用 Sa-Token 1.45.0 真实核心实现建立令牌映射和服务端 Token-Session。
- 四项认证状态反例全部通过。
- 令牌对象的字符串表达不输出令牌内容。
- Sa-Token 只存在于 `adapter` 包；`api`、`application` 和 `domain` 的框架隔离由 ArchUnit 验证。
- 全后端清理测试通过。

## 六维质量判断

- 正确性：真实 Sa-Token 状态而非模拟调用顺序完成红绿验证。
- 复杂度：应用层只有一个三方法会话端口，框架细节集中在单一适配器。
- 冗余：可信身份字段复用既有值类型，没有建立第二套认证模型。
- 性能：会话恢复只执行一次令牌映射和一次 Token-Session 读取；权限集合有界复制。
- 架构：框架依赖被 ArchUnit 限定在适配器。
- 测试质量：覆盖有效、损坏、撤销和标识不一致，不依赖异常文本或内部调用顺序。

## 边界

当前尚未接入 Redis DAO、成员持久化、密码验证和 HTTP 登录端点。进入下一切片后，令牌状态必须由 Redis 承载，成员事实必须来自 PostgreSQL。
