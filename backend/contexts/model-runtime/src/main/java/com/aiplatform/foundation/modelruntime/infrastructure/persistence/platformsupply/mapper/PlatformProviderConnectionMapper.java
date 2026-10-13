package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformProviderConnectionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.metadata.IPage;

/** 连接 CRUD 使用 MyBatis-Plus；创建咨询锁独立声明在 XML。 */
public interface PlatformProviderConnectionMapper extends BaseMapper<PlatformProviderConnectionEntity> {
    /** 全局名称和幂等键属于平台范围，创建期间串行化其检查与提交。 */
    String lockCreation(@Param("key") long key);

    /** 发布投影参与筛选，分页与总数复用同一平台范围谓词。 */
    IPage<PlatformProviderConnectionEntity> selectDirectoryPage(IPage<PlatformProviderConnectionEntity> page, @Param("q") String q);

    /** 当前连接修订的独立发布计数，不依赖目录筛选或分页。 */
    long countPublished();

    /** 只选存在性与修订；目录通过一条查询取得全部配置事实，避免逐行读取。 */
    java.util.List<com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionFacts.Fact>
            selectFacts(@Param("ids") java.util.Set<Long> ids);
}
