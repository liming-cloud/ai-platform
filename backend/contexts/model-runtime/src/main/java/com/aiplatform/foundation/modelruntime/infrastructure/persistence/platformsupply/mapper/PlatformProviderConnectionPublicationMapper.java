package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformProviderConnectionPublicationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/** 先锁定平台连接再分配发布修订，避免探测、轮换和发布竞态。 */
public interface PlatformProviderConnectionPublicationMapper extends BaseMapper<PlatformProviderConnectionPublicationEntity> {
    /** 与连接更新共用行锁，缺少平台连接时不允许创建发布事实。 */
    Long lockConnection(@Param("id") long id);

    /** 在连接行锁内读取已提交发布序号，为下一次发布分配连续修订。 */
    Long latestRevision(@Param("id") long id);
}
