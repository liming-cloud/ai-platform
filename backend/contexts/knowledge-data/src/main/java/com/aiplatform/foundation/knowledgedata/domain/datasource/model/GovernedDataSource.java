package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 管理数据来源身份及其探测、同步和退役状态。
 *
 * <p>该聚合不保存连接配置、同步内容或质量结果；这些事实必须由不可变修订与产物拥有，防止连接器的
 * 失败语义扩散到来源身份。</p>
 */
public final class GovernedDataSource implements AggregateRoot<DataSourceId> {

    private final TenantId tenantId;
    private final KnowledgeSpaceId knowledgeSpaceId;
    private final DataSourceId id;
    private final GovernedDataSourceType type;
    private final String name;
    private final CreatorId createdBy;
    private final Instant createdAt;
    private final GovernedDataSourceStatus status;
    private final DataSourceOperation failedOperation;
    private final long currentRevision;
    private final long version;

    private GovernedDataSource(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId id,
            GovernedDataSourceType type,
            String name,
            CreatorId createdBy,
            Instant createdAt,
            GovernedDataSourceStatus status,
            DataSourceOperation failedOperation,
            long currentRevision,
            long version) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.knowledgeSpaceId = Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        this.id = Objects.requireNonNull(id, "数据源标识不能为空");
        this.type = Objects.requireNonNull(type, "数据源类型不能为空");
        this.name = requireName(name);
        this.createdBy = Objects.requireNonNull(createdBy, "创建成员不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.status = Objects.requireNonNull(status, "数据源状态不能为空");
        if ((status == GovernedDataSourceStatus.FAILED) != (failedOperation != null)) {
            throw new IllegalArgumentException("失败状态与失败操作不一致");
        }
        this.failedOperation = failedOperation;
        if (currentRevision < 1) {
            throw new IllegalArgumentException("当前来源修订必须为正数");
        }
        this.currentRevision = currentRevision;
        if (version < 0) {
            throw new IllegalArgumentException("数据源修订不能为负数");
        }
        this.version = version;
    }

    /**
     * 创建尚未探测的受治理数据源。
     */
    public static GovernedDataSource create(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId id,
            GovernedDataSourceType type,
            String name,
            CreatorId createdBy,
            Instant createdAt) {
        return new GovernedDataSource(
                tenantId,
                knowledgeSpaceId,
                id,
                type,
                name,
                createdBy,
                createdAt,
                GovernedDataSourceStatus.DRAFT,
                null,
                1,
                0);
    }

    /**
     * 开始连接或文件可用性探测。
     *
     * @throws DataSourceStateException 当前状态或修订不允许探测时抛出。
     */
    public GovernedDataSource startProbe(long expectedVersion) {
        return transition(expectedVersion, GovernedDataSourceStatus.DRAFT, GovernedDataSourceStatus.PROBING);
    }

    /**
     * 登记成功的探测结果，使来源可开始同步。
     *
     * @throws DataSourceStateException 当前状态或修订不允许完成探测时抛出。
     */
    public GovernedDataSource completeProbe(long expectedVersion) {
        return transition(expectedVersion, GovernedDataSourceStatus.PROBING, GovernedDataSourceStatus.READY);
    }

    /**
     * 登记探测失败，供后续按探测语义恢复。
     *
     * @throws DataSourceStateException 当前状态或修订不允许登记探测失败时抛出。
     */
    public GovernedDataSource failProbe(long expectedVersion) {
        return fail(expectedVersion, GovernedDataSourceStatus.PROBING, DataSourceOperation.PROBE);
    }

    /**
     * 开始一次产生不可变同步产物的同步。
     *
     * @throws DataSourceStateException 当前状态或修订不允许同步时抛出。
     */
    public GovernedDataSource startSynchronization(long expectedVersion) {
        return transition(expectedVersion, GovernedDataSourceStatus.READY, GovernedDataSourceStatus.SYNCING);
    }

    /**
     * 完成一次同步，使来源回到可再次同步的就绪状态。
     *
     * @throws DataSourceStateException 当前状态或修订不允许完成同步时抛出。
     */
    public GovernedDataSource completeSynchronization(long expectedVersion) {
        return transition(expectedVersion, GovernedDataSourceStatus.SYNCING, GovernedDataSourceStatus.READY);
    }

    /**
     * 登记一次显式产物发布的来源修订。
     *
     * @throws DataSourceStateException 来源尚未就绪或修订过期时抛出。
     */
    public GovernedDataSource publishArtifact(long expectedVersion) {
        return transition(expectedVersion, GovernedDataSourceStatus.READY, GovernedDataSourceStatus.READY);
    }

    /**
     * 登记同步失败，供后续按同步语义恢复。
     *
     * @throws DataSourceStateException 当前状态或修订不允许登记同步失败时抛出。
     */
    public GovernedDataSource failSynchronization(long expectedVersion) {
        return fail(
                expectedVersion,
                GovernedDataSourceStatus.SYNCING,
                DataSourceOperation.SYNCHRONIZATION);
    }

    /**
     * 按最后失败操作恢复运行状态，禁止把探测失败误当作同步重试。
     *
     * @throws DataSourceStateException 当前状态或修订不允许重试时抛出。
     */
    public GovernedDataSource retry(long expectedVersion) {
        if (expectedVersion != version || status != GovernedDataSourceStatus.FAILED) {
            throw new DataSourceStateException("数据源当前修订不允许重试");
        }
        return copy(
                failedOperation == DataSourceOperation.PROBE
                        ? GovernedDataSourceStatus.PROBING
                        : GovernedDataSourceStatus.SYNCING,
                null);
    }

    /**
     * 退役来源，禁止后续探测和同步。
     *
     * @throws DataSourceStateException 当前状态或修订不允许退役时抛出。
     */
    public GovernedDataSource retire(long expectedVersion) {
        if (status == GovernedDataSourceStatus.RETIRED || expectedVersion != version) {
            throw new DataSourceStateException("数据源当前修订不允许退役");
        }
        return copy(GovernedDataSourceStatus.RETIRED, null);
    }

    public GovernedDataSourceStatus status() {
        return status;
    }

    /**
     * 返回导致当前失败状态的运行操作；非失败状态始终为 `null`。
     */
    public DataSourceOperation failedOperation() {
        return failedOperation;
    }

    /**
     * 从已验证的持久化事实重建受治理数据源。
     */
    public static GovernedDataSource restore(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId id,
            GovernedDataSourceType type,
            String name,
            CreatorId createdBy,
            Instant createdAt,
            GovernedDataSourceStatus status,
            DataSourceOperation failedOperation,
            long currentRevision,
            long version) {
        return new GovernedDataSource(
                tenantId,
                knowledgeSpaceId,
                id,
                type,
                name,
                createdBy,
                createdAt,
                status,
                failedOperation,
                currentRevision,
                version);
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public KnowledgeSpaceId knowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public GovernedDataSourceType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public CreatorId createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /**
     * 返回受治理数据源的稳定标识。
     */
    @Override
    public DataSourceId id() {
        return id;
    }

    public long version() {
        return version;
    }

    /**
     * 返回当前可被同步操作消费的不可变来源修订。
     */
    public long currentRevision() {
        return currentRevision;
    }

    private GovernedDataSource transition(
            long expectedVersion,
            GovernedDataSourceStatus expectedStatus,
            GovernedDataSourceStatus targetStatus) {
        if (expectedVersion != version || status != expectedStatus) {
            throw new DataSourceStateException("数据源当前修订不允许状态迁移");
        }
        return copy(targetStatus, null);
    }

    private GovernedDataSource fail(
            long expectedVersion,
            GovernedDataSourceStatus expectedStatus,
            DataSourceOperation operation) {
        if (expectedVersion != version || status != expectedStatus) {
            throw new DataSourceStateException("数据源当前修订不允许登记失败");
        }
        return copy(GovernedDataSourceStatus.FAILED, operation);
    }

    private GovernedDataSource copy(
            GovernedDataSourceStatus targetStatus,
            DataSourceOperation targetFailedOperation) {
        return new GovernedDataSource(
                tenantId,
                knowledgeSpaceId,
                id,
                type,
                name,
                createdBy,
                createdAt,
                targetStatus,
                targetFailedOperation,
                currentRevision,
                version + 1);
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }
        return value.trim();
    }
}
