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
            long version) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.knowledgeSpaceId = Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        this.id = Objects.requireNonNull(id, "数据源标识不能为空");
        this.type = Objects.requireNonNull(type, "数据源类型不能为空");
        this.name = requireName(name);
        this.createdBy = Objects.requireNonNull(createdBy, "创建成员不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.status = Objects.requireNonNull(status, "数据源状态不能为空");
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
     * 退役来源，禁止后续探测和同步。
     *
     * @throws DataSourceStateException 当前状态或修订不允许退役时抛出。
     */
    public GovernedDataSource retire(long expectedVersion) {
        if (status == GovernedDataSourceStatus.RETIRED || expectedVersion != version) {
            throw new DataSourceStateException("数据源当前修订不允许退役");
        }
        return copy(GovernedDataSourceStatus.RETIRED);
    }

    public GovernedDataSourceStatus status() {
        return status;
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

    private GovernedDataSource transition(
            long expectedVersion,
            GovernedDataSourceStatus expectedStatus,
            GovernedDataSourceStatus targetStatus) {
        if (expectedVersion != version || status != expectedStatus) {
            throw new DataSourceStateException("数据源当前修订不允许状态迁移");
        }
        return copy(targetStatus);
    }

    private GovernedDataSource copy(GovernedDataSourceStatus targetStatus) {
        return new GovernedDataSource(
                tenantId,
                knowledgeSpaceId,
                id,
                type,
                name,
                createdBy,
                createdAt,
                targetStatus,
                version + 1);
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }
        return value.trim();
    }
}
