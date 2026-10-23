package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformModelDeploymentRow;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** 平台部署短事务映射；分页由 MyBatis-Plus 生成，锁与幂等写入使用同一连接。 */
public interface PlatformModelDeploymentMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformModelDeploymentEntity> {
    /** 全目录计数，不返回业务内容。 */
    com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCounts counts();
    /** 只返回部署明确引用的验证事实时间。 */
    java.time.Instant lastVerifiedAt(@Param("id") long id);
    /** 全局名称与幂等键采用固定事务锁，避免并发重复声明。 */
    void lockCreation();
    /** 只锁定当前已发布且验证仍有效的连接。 */
    Long lockSource(@Param("id") long id, @Param("revision") long revision, @Param("now") Instant now);
    /** 精确读取，不返回首个部署。 */
    PlatformModelDeploymentRow selectById(@Param("id") long id);
    /** 固定公开投影，范围／搜索／排序由 infrastructure 白名单条件构造，分页及总数由插件生成。 */
    IPage<PlatformModelDeploymentRow> selectDirectoryPage(IPage<PlatformModelDeploymentRow> page,
            @Param(com.baomidou.mybatisplus.core.toolkit.Constants.WRAPPER)
            com.baomidou.mybatisplus.core.conditions.Wrapper<com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformModelDeploymentEntity> filter);
    /** 保存新声明，不写租户目录、配置或授权。 */
    int insertDeployment(@Param("d") PlatformModelDeployment deployment, @Param("capabilities") String capabilities,
            @Param("tenants") String tenants);
}
