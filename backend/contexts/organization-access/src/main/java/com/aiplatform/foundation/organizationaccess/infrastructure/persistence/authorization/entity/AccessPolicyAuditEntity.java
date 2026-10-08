package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** 组织策略事实的无秘密映射；复合键条件由仓储明确约束，不作为浏览器契约。 */
@TableName("organization_access.access_policy_audit")
public class AccessPolicyAuditEntity {
    private Long tenantId;
    private Long actorSubjectId;
    private Long policyId;
    private Long versionId;
    private String action;
    private Long versionRevision;
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getActorSubjectId() { return actorSubjectId; }
    public void setActorSubjectId(Long value) { actorSubjectId = value; }
    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long value) { policyId = value; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long value) { versionId = value; }
    public String getAction() { return action; }
    public void setAction(String value) { action = value; }
    public Long getVersionRevision() { return versionRevision; }
    public void setVersionRevision(Long value) { versionRevision = value; }
}
