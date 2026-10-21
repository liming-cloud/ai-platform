package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity.ControlledModelCatalogEntryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

/**
 * 受控模型目录的 MyBatis-Plus 映射器。
 */
public interface ControlledModelCatalogEntryMapper
        extends BaseMapper<ControlledModelCatalogEntryEntity> {

    IPage<ControlledModelCatalogEntryEntity> selectPublishedAvailablePage(IPage<ControlledModelCatalogEntryEntity> page,
            @Param("tenantId") long tenantId);

    /** 精确授权与来源谓词与分页完全相同，不能猜目录 ID 绕过。 */
    boolean isSupplyAvailable(@Param("tenantId") long tenantId, @Param("entryId") long entryId);

    /** 平台旧入口只读恢复原子发布事实，不再独立建立或发布目录。 */
    ControlledModelCatalogEntryEntity selectPublishedByDeployment(@Param("deploymentId") long deploymentId,
            @Param("revision") long revision);

    /** 与连接轮换／新发现共用来源根，不能持旧快照申请新授权。 */
    Long lockSupplySource(@Param("entryId") long entryId);
    /** 来源先于部署共享锁，与原子发布和撤权保持一致顺序。 */
    Long lockSupplyDeployment(@Param("entryId") long entryId);
    /** 活动授权共享锁保护配置提交，撤权等待已开始的提交完成。 */
    Long lockSupplyGrant(@Param("tenantId") long tenantId, @Param("entryId") long entryId);
}
