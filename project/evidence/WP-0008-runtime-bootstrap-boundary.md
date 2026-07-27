# WP-0008 运行入口与装配边界整改证据

## 结论

三个运行模块的主代码均只保留一个启动类：

```text
backend/runtimes/
├── control-plane/.../ControlPlaneApplication.java
├── query-runtime/.../QueryRuntimeApplication.java
└── ingestion-worker/.../IngestionWorkerApplication.java
```

跨上下文装配已经迁入独立模块：

```text
backend/bootstrap/
├── control-plane-bootstrap/
├── query-bootstrap/
└── ingestion-bootstrap/
```

- 管理控制器已归还助手、组织、知识、模型和评测运营上下文的
  `adapter/in/web`。
- 查询控制器已归还可信查询上下文的 `adapter/in/web`。
- 索引调度器已归还知识数据上下文的 `adapter/in/scheduling`。
- 跨上下文配置与全局异常处理器只存在于对应装配模块。
- 运行模块仅依赖一个装配模块，不再直接罗列所有业务上下文和扩展。
- 摄取进程补齐独立雪花工作节点配置，三个容器使用不同工作节点。
- 通用认证头解析从 Sa-Token 实现包下沉至安全核心，业务入站适配器不依赖
  Sa-Token。

## 验证

绑定提交：

`d9edc1fb0eeffea1d1df81dfc918500eb447ca07`

已通过：

```text
./mvnw -q -DskipTests test-compile：通过
ControlPlaneApplicationTest：通过
QueryRuntimeApplicationTest：通过
IngestionWorkerApplicationTest：通过
QueryApiExceptionHandlerTest：通过
QueryAssistantControllerTest：通过
QueryRunControllerTest：通过
scripts.quality.test_backend_architecture_guard：7 项通过
make architecture-guard：通过
git diff --check：通过
```

架构债务变化：

- Java SQL 注解文件：保持为 0。
- Java UUID 文件：保持为 0。
- Sa-Token 越界文件：保持为 0。
- 运行时非启动文件：从 41 降至 0。
- 超预算包：仍为 8，进入目录与领域整改切片处理。

## 未完成项

本证据只关闭运行入口、装配所有权和入站适配器归属问题。平铺目录拆分、领域增强、
全量验证和全量代码走查仍必须继续。
