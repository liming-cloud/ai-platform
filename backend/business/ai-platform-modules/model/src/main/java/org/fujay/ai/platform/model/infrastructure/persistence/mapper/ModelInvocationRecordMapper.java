package org.fujay.ai.platform.model.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelInvocationRecordPO;

@Mapper
public interface ModelInvocationRecordMapper extends BaseMapper<ModelInvocationRecordPO> {
}
