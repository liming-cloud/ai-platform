package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** 组织策略事实的无秘密映射；复合键条件由仓储明确约束，不作为浏览器契约。 */
@TableName("organization_access.access_policy_version_member")
public class AccessPolicyVersionMemberEntity {
    private Long tenantId;
    private Long policyId;
    private Long versionId;
    private Long membershipId;
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long value) { policyId = value; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long value) { versionId = value; }
    public Long getMembershipId() { return membershipId; }
    public void setMembershipId(Long value) { membershipId = value; }
}
