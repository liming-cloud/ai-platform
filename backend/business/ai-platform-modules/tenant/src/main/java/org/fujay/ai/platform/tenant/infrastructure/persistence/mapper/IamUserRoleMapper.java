package org.fujay.ai.platform.tenant.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamUserRolePO;

@Mapper
public interface IamUserRoleMapper extends BaseMapper<IamUserRolePO> {
}
