# WP-0009 共享基础与平台 Web 证据

## 变更边界

- 新增 `backend/shared/shared-kernel`。
- 新增 `backend/shared/shared-contract`。
- 删除 `platform-kernel` 反应堆模块并把标识与租户基础类型直接迁入 shared-kernel。
- 新增 `platform-web` 自动配置、统一异常处理和安全错误响应。
- 固定 MyBatis-Plus 分页插件为拦截器链最后一个。

未保留旧包、旧模块或转发类型。

## 影响分析

代码图谱显示 `EntityIdGenerator` 有 53 个直接调用点，三级影响 391 个符号；
`TraceIds` 有 4 个生产调用方。因此本切片机械更新全部 Java 导入和 Maven 依赖后执行
全反应堆测试，没有建立双包兼容层。

## 红灯

共享模块测试首次执行时，因领域标记、统一异常、分页契约和错误响应尚不存在而编译
失败。platform-web 测试首次执行时，因自动配置和异常处理器尚不存在而编译失败。

持久化自动配置测试把分页插件期望改为最后一个后，首次运行明确观察到旧顺序为：

```text
OptimisticLockerInnerInterceptor
PaginationInnerInterceptor
BlockAttackInnerInterceptor
```

与已批准规范不一致。

## 绿灯

以下命令通过：

```bash
./mvnw -q -pl backend/shared/shared-kernel,backend/shared/shared-contract test
./mvnw -q -pl backend/platform/platform-web -am test
./mvnw -q -pl backend/platform/platform-persistence -am \
  -Dtest=PlatformPersistenceAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q test
```

已验证：

- `TenantId` 是共享值对象。
- 泛型 `Entity` 与 `AggregateRoot` 保持标识类型约束。
- 统一异常只保留错误码允许的安全详情。
- 错误编码格式和类别不一致时拒绝创建异常。
- PageQuery 固定页码、页大小和最大偏移边界。
- PageResult 正确派生总页数和前后页且条目不可修改。
- 未识别异常只返回 `COMMON.SYSTEM.0001`，不返回原始消息和类型。
- platform-web 在缺少标识生成器时失败，并允许应用显式覆盖。
- 持久化拦截器顺序为乐观锁、危险操作阻断、分页；分页固定 PostgreSQL、
  `overflow=false`、`maxLimit=100`。
