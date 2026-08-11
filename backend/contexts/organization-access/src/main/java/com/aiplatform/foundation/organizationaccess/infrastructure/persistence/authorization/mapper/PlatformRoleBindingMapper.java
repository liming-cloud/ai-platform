package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.projection.PlatformRoleAuthorizationRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 读取活动平台角色绑定认证事实的 MyBatis 映射器。 */
public interface PlatformRoleBindingMapper {

    List<PlatformRoleAuthorizationRow> findActiveAuthorizationBySubject(@Param("subjectId") long subjectId);
}
