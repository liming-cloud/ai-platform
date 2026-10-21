package com.aiplatform.foundation.modelruntime.domain.configuration.repository;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/**
 * 受控目录与模型配置的聚合持久化端口。
 */
public interface ModelConfigurationRepository {

    Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id);

    /**
     * 返回可由配置者选择、来源当前可用的已发布目录条目。
     *
     * <p>实现必须在分页和总数计算前排除来源部署修订、连接修订或验证时效已经失真的快照，
     * 防止选择器展示随后必然失败关闭的模型。</p>
     */
    default ControlledModelCatalogPage findPublishedCatalogPage(
            long pageNumber, long pageSize) {
        throw new UnsupportedOperationException("不再允许缺少可信租户的全局供应目录读取");
    }

    /** 在分页和同范围总数之前过滤当前租户的活动供应授权，不能回退到全局目录。 */
    default ControlledModelCatalogPage findPublishedCatalogPage(TenantId tenantId, long pageNumber, long pageSize) {
        throw new UnsupportedOperationException("当前仓储缺少精确租户供应事实");
    }

    /** 每次新使用检查来源及显式供应授权；旧实现不能证明时失败关闭。 */
    default boolean isSupplyAvailable(TenantId tenantId, ControlledModelCatalogEntryId catalogEntryId) { return false; }

    /** 在已有短写事务中锁定当前来源、部署、活动租户及精确授权，撤权不能穿越命令提交。 */
    default void lockSupply(TenantId tenantId, ControlledModelCatalogEntryId catalogEntryId) {
        throw new UnsupportedOperationException("当前仓储缺少供应提交锁");
    }

    /** 原子复核供应并保存新配置，不生成额外租户授权。 */
    default void saveWithSupply(TenantId tenantId, ModelConfiguration configuration) {
        throw new UnsupportedOperationException("当前仓储缺少受控配置提交");
    }

    /** 原子复核供应并启用精确配置，失效不得改变原配置状态。 */
    default void activateWithSupply(TenantId tenantId, ModelConfiguration configuration) {
        throw new UnsupportedOperationException("当前仓储缺少受控启用提交");
    }

    /** 只读恢复原子发布已经冻结的唯一目录，禁止独立创建第二份供应。 */
    default Optional<ControlledModelCatalogEntry> findPublishedByDeployment(
            com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId id, long revision) {
        return Optional.empty();
    }

    void saveCatalogEntry(ControlledModelCatalogEntry entry);

    void updateCatalogEntry(ControlledModelCatalogEntry entry);

    Optional<ModelConfiguration> findConfiguration(ModelConfigurationId id);

    /**
     * 返回已按范围与活动平台授权过滤的当前成员可见配置；凭据状态由应用层投影。
     */
    default ModelConfigurationPage findVisiblePage(
            TenantId tenantId,
            ModelConfigurationOwnerId membershipId,
            long pageNumber,
            long pageSize) {
        throw new UnsupportedOperationException("当前模型配置仓储不支持可见配置分页");
    }

    void updateConfiguration(ModelConfiguration configuration);

    void save(ModelConfiguration configuration);
}
