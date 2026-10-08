package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** 唯一成员—助手关系的复合键映射；写条件始终包含租户及资格。 */
@TableName("organization_access.member_assistant_authorization")
public class MemberAssistantAuthorizationEntity {
    private Long tenantId;
    private Long membershipId;
    private Long assistantId;
    public Long getTenantId() { return tenantId; }
    public Long getMembershipId() { return membershipId; }
    public Long getAssistantId() { return assistantId; }
}
