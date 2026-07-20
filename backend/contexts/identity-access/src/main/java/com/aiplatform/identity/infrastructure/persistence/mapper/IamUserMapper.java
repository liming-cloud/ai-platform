package com.aiplatform.identity.infrastructure.persistence.mapper;

import com.aiplatform.identity.infrastructure.persistence.entity.IamUserPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamUserCredentialPersistenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * `iam_user` Mapper，复杂 SQL 必须在 XML Mapper 中声明，便于集中治理和审查。
 */
@Mapper
public interface IamUserMapper extends BaseMapper<IamUserPersistenceEntity> {
    IamUserPersistenceEntity findActiveTenantUserByLoginName(
            @Param("tenantId") Long tenantId,
            @Param("loginName") String loginName
    );

    IamUserPersistenceEntity findActiveTenantUserById(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId
    );

    Set<String> findActiveRoleCodes(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    IamUserCredentialPersistenceEntity findActiveCredential(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId
    );
}
