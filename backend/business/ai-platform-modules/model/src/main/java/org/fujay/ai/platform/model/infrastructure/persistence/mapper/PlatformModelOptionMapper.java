package org.fujay.ai.platform.model.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.fujay.ai.platform.model.infrastructure.persistence.po.PlatformModelOptionPO;

@Mapper
public interface PlatformModelOptionMapper extends BaseMapper<PlatformModelOptionPO> {
}
