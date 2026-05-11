package org.fujay.ai.platform.model.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.fujay.ai.platform.model.infrastructure.persistence.po.PlatformProviderTypePO;

@Mapper
public interface PlatformProviderTypeMapper extends BaseMapper<PlatformProviderTypePO> {
}
