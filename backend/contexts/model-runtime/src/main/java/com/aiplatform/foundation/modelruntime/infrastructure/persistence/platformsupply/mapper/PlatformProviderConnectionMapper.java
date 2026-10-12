package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformProviderConnectionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/** 连接 CRUD 使用 MyBatis-Plus；创建咨询锁独立声明在 XML。 */
public interface PlatformProviderConnectionMapper extends BaseMapper<PlatformProviderConnectionEntity> {
    /** 全局名称和幂等键属于平台范围，创建期间串行化其检查与提交。 */
    String lockCreation(@Param("key") long key);
}
