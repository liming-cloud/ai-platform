# WP-0002 容器化运行基线证据

## 证据范围

- 使用 Docker Compose 编排 PostgreSQL、Redis、MinIO、三个后端运行入口和两个前端应用。
- 所有基础镜像同时锁定版本标签与内容摘要。
- 后端构建和运行镜像固定为 Amazon Corretto 26.0.2。
- 三个后端与两个前端应用仅监听本机回环地址，基础设施不向宿主机暴露端口。
- 五个应用容器均使用非特权用户、只读根文件系统、删除全部 Linux 能力并禁止权限提升。
- 数据库、缓存和对象存储使用 `foundation-v1` 独立卷与网络命名空间。
- 健康检查覆盖三个后端就绪端点和两个前端入口。

## 验证命令

```bash
make deploy-config
make deploy-up
make deploy-smoke
docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml ps
docker inspect <五个应用容器>
docker exec trusted-rag-foundation-v1-control-plane-1 java -version
```

## 验证结果

- Compose 配置解析通过。
- 三个后端、两个前端与三项基础设施共八个服务均为健康状态。
- 五个应用入口健康检查通过。
- 后端应用容器用户为 `10001:10001`，前端应用容器用户为 `101`。
- 五个应用容器的只读根文件系统均已启用。
- 五个应用容器均配置 `cap_drop=ALL` 和 `no-new-privileges=true`。
- 容器内 Java 版本为 `26.0.2`。
- 验证时间：2026-07-26T23:47:00+08:00。

## 已发现并关闭的问题

- Maven 官方构建镜像标签实际包含 JDK 26.0.1，被 Maven Enforcer 正确拒绝。未放宽门禁，改用经运行验证的 Corretto 26.0.2 精确镜像。
- 精简 JDK 镜像缺少 Maven Wrapper 解包工具，只在构建阶段补充 `gzip` 和 `tar`。
- PostgreSQL 18 更改了容器数据目录布局，持久卷已按官方入口要求挂载到 `/var/lib/postgresql`。
- 健康探测重试期间原本会打印暂态连接错误，现已静默重试并只在最终失败时返回错误。

## 边界

该证据证明本地单机容器运行基线，不代表生产集群部署、镜像签名、漏洞扫描、备份恢复、外部密钥服务或容量目标已经完成。运行镜像当前包含完整 JDK，后续工作包将使用 `jlink` 生成精确裁剪运行时。
