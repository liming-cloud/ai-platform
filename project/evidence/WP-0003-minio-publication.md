# WP-0003：真实对象存储与知识发布闭环证据

## 结论

WP-0003 的最后交付缺口已经关闭。演示租户管理员可从控制台或接口完成：

`登录 -> 选择知识空间 -> 创建文本数据源 -> 上传 -> 发布 -> 查看发布终态`

原始内容进入私有 MinIO 桶，PostgreSQL 只保存对象引用和受治理元数据。
门店与校园数据在不同租户边界内独立完成发布。

## 固定实现

- 实现提交：`5a5a0ad83e5879979725b60304441568211aaeb4`
- 分支：`codex/trusted-rag-foundation-v1`
- Java：26.0.2
- MinIO Java 客户端：8.6.0
- MinIO 镜像：
  `minio/minio:RELEASE.2025-09-07T16-13-09Z@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e`
- 研究依据：`docs/research/WP-0003-minio-client.md`

## 红绿验证

首先新增真实容器合同测试，并执行：

```bash
./mvnw -pl backend/contexts/knowledge-data -am \
  -Dtest=MinioTextContentObjectStorageAdapterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

红灯结果为适配器不存在。实现迁移到独立扩展后执行：

```bash
./mvnw -q -pl backend/extensions/object-storage/object-storage-minio -am \
  -Dtest=MinioTextContentObjectStorageAdapterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

三项真实 MinIO 合同测试通过：

- 内容寻址写入、元数据读取、相同请求幂等、匿名读取拒绝和删除。
- 生产模式不允许隐式创建缺失桶。
- 缺少 SHA-256 受治理元数据的对象不能通过完整性验证。

## 架构证据

- `knowledge-data` 只保留对象存储端口、内容引用和不可用适配器，
  不依赖 MinIO、OkHttp 或 SpotBugs 注解。
- 厂商实现位于 `backend/extensions/object-storage/object-storage-minio`。
- `control-plane` 作为组合根选择扩展并注入端点、凭据、桶和超时。
- MinIO 构件的传递元数据不能满足严格编译，因此扩展显式固定
  `okhttp-jvm` 和 `spotbugs-annotations`；依赖未进入领域核心。
- 输入流使用已知内容长度写入，不在对象存储适配器中把最多 20 MiB
  的内容重新缓冲到堆内存。
- 网络调用具有连接、写入和读取超时；公共错误不包含底层异常消息。

## 全新卷验收

执行以下操作，仅删除并重建本项目三个演示数据卷：

```bash
docker compose \
  --env-file deploy/compose/.env.example \
  -f deploy/compose/compose.yml \
  down --volumes --remove-orphans
make deploy-up
make deploy-smoke
make acceptance-knowledge-space
```

被删除的卷为：

- `trusted-rag-foundation-v1-postgres`
- `trusted-rag-foundation-v1-redis`
- `trusted-rag-foundation-v1-minio`

它们只包含可重建演示数据，删除不可恢复。重建后八个服务全部健康，
五个应用入口通过，验收输出为：

```text
store 场景知识发布验收通过
campus 场景知识发布验收通过
两租户、两知识空间、两对象和两条发布事实验收通过
```

验收不是只检查接口响应，还独立核对：

- 数据源状态为 `PUBLISHED`。
- 聚合版本为 2，内容版本和发布版本均为 1。
- 数据库对象键包含租户、知识空间、数据源、版本和摘要。
- 数据库摘要与字节数等于固定合成文件。
- 使用受认证 MinIO 客户端读取的对象摘要等于固定合成文件。

## 浏览器终态

在 `http://127.0.0.1:13000/` 使用 `demo-store` 租户管理员执行真实浏览器任务，
创建“浏览器发布验收文本”，上传合成商品记录并发布。页面终态显示：

```text
首份知识数据已发布
“浏览器发布验收文本”已进入“门店商品知识”。后续索引工作包可以消费已发布版本。
```

浏览器控制台错误为 0，随后注销并回到登录页。数据库二次核对得到：

- 状态 `PUBLISHED`。
- 聚合版本 2。
- 内容版本 1。
- 发布版本 1。
- 对象长度 161 字节。
- 数据库与对象内容 SHA-256 均为
  `b671d852dacf06f6915da2e58d8ca1a10a2c317029895f53c9edd8f01b50a065`。

## 组合部署故障与修复

全新启动验收真实暴露并关闭了以下问题：

- 控制台先于控制面就绪时，Nginx 固化了失效地址并返回 502。
  现使用 Docker 内部解析器动态解析，并要求控制面健康后启动控制台。
- 验收脚本曾在接口失败后继续解析空响应；现立即保留原失败并退出。
- PostgreSQL 命令变量写法不适用于当前组合执行；现先严格验证 UUID，
  再使用受限值查询。
- 匿名对象读取失败证明桶是私有的；验收改用容器内临时受认证别名读取，
  且不输出凭据。
- 浏览器额外发布数据后，全局发布数量不再恒为 2；验收只统计两条固定场景事实，
  避免把其他合法演示数据误判为失败。

## 全量质量门禁

```bash
make validate
make deploy-config
make deploy-up
make deploy-smoke
make acceptance-knowledge-space
git diff --check
bash -n scripts/development/acceptance-knowledge-space.sh
```

结果：

- 当前 Maven 反应堆后端测试 86 项，失败 0，错误 0，跳过 0。
- 前端测试 12 项通过，类型检查、代码检查和两个生产构建通过。
- 演示数据测试 5 项通过，2 个场景和 8 个检索用例通过。
- 项目治理 7 项测试和控制面验证通过。
- 八个服务健康，控制面镜像标识为
  `sha256:18fae6a8df81a7b21855b3a0268cdade4a1a28a9990d963f886eef1f9ee060ac`。
- 控制台镜像标识为
  `sha256:ed3666f51e8668989763a6465fe79995ccd162fa0a8712bfe665b3b2a858a21a`。
- 控制面使用用户 `10001:10001`、只读根文件系统、禁止权限提升并移除全部能力。

## 六维审计

| 维度 | 结论 |
| --- | --- |
| 正确性 | 真实 MinIO 合同、接口验收、数据库与对象双重核对、浏览器终态均通过 |
| 复杂度 | 只新增一个厂商扩展和一个组合根装配，不增加巨型对象存储抽象 |
| 冗余 | 不可用适配器负责关闭失败，MinIO 适配器负责正式存储，职责不重复 |
| 性能 | 上传流式处理、已知长度写入、20 MiB 上限和有限网络超时 |
| 架构 | 领域核心不依赖厂商客户端，具体实现位于批准的独立扩展目录 |
| 测试 | 红灯可复现，真实容器、生产限制、权限、全新卷、接口和浏览器均有证据 |

## 遗留边界

本工作包不实现分块、Embedding、索引版本、检索或问答。
这些内容进入 WP-0004，不扩大 WP-0003 的完成定义。
