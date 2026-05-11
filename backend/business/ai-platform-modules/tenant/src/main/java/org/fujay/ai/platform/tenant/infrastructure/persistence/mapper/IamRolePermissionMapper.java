package org.fujay.ai.platform.tenant.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamRolePermissionPO;

@Mapper
public interface IamRolePermissionMapper extends BaseMapper<IamRolePermissionPO> {
}
