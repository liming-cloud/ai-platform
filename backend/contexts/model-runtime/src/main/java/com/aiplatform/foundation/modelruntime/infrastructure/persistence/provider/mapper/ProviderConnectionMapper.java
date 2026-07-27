package com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.entity.ProviderConnectionEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 供应商连接 MyBatis-Plus 映射器。
 */
public interface ProviderConnectionMapper extends BaseMapper<ProviderConnectionEntity> {

    String lockTenant(@Param("tenantId") String tenantId);

}
