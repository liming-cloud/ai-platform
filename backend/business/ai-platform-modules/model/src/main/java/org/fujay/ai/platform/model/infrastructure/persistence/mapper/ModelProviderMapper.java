package org.fujay.ai.platform.model.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelProviderPO;

@Mapper
public interface ModelProviderMapper extends BaseMapper<ModelProviderPO> {
}
