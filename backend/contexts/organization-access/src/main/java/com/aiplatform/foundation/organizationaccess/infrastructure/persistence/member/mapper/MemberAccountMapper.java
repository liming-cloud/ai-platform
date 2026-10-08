package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** 组织成员配置和审计的受限 XML 映射，不读取凭据字段。 */
public interface MemberAccountMapper {
    Long lockTenant(@Param("tenantId") long tenantId);
    Long lockAuthorization(@Param("tenantId") long tenantId);
    Long authorizationRevision(@Param("tenantId") long tenantId);
    Map<String, Object> operation(@Param("tenantId") long tenantId, @Param("actor") long actor,
            @Param("key") String key);
    int insertFeatures(@Param("tenantId") long tenantId, @Param("membershipId") long membershipId,
            @Param("features") Map<String, Boolean> features);
    int insertAssistant(@Param("tenantId") long tenantId, @Param("membershipId") long membershipId,
            @Param("assistantId") long assistantId);
    int advanceAuthorization(@Param("tenantId") long tenantId, @Param("expected") long expected);
    int record(@Param("tenantId") long tenantId, @Param("actor") long actor, @Param("key") String key,
            @Param("operation") String operation, @Param("membershipId") long membershipId,
            @Param("fingerprint") String fingerprint, @Param("identityVersion") long identityVersion);
    com.baomidou.mybatisplus.core.metadata.IPage<Long> pendingRevocations(com.baomidou.mybatisplus.core.metadata.IPage<Long> page);
    int markRevoked(@Param("membershipId") long membershipId);
}
