package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformProviderModelCandidateRow;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** 当前修订的未过期候选先过滤再分页；数据和计数使用相同的安全范围。 */
public interface PlatformProviderModelCandidateMapper {
    /** 不读取任何凭据，返回固定排序的有界候选窗口。 */
    IPage<PlatformProviderModelCandidateRow> selectCandidatePage(IPage<PlatformProviderModelCandidateRow> page,
            @Param("connectionId") long connectionId, @Param("revision") long revision,
            @Param("now") Instant now, @Param("q") String q);
}
