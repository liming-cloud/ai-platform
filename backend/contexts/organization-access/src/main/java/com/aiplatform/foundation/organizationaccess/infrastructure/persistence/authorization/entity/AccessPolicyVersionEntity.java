package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** 组织策略事实的无秘密映射；复合键条件由仓储明确约束，不作为浏览器契约。 */
@TableName("organization_access.access_policy_version")
public class AccessPolicyVersionEntity {
    private Long tenantId;
    private Long policyId;
    private Long versionId;
    private Integer versionNumber;
    private String status;
    private Long revision;
    private Long assistantId;
    private Long assistantVersionId;
    private String assistantName;
    private Long knowledgeSpaceId;
    private String knowledgeSpaceName;
    private Long authorizationRevision;
    private Long tenantFeatureVersion;
    private String previewFingerprint;
    private Integer allowCount;
    private Integer denyCount;
    private Integer leakCount;
    private java.time.Instant previewedAt;
    private Long publishedAuthorizationRevision;
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long value) { policyId = value; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long value) { versionId = value; }
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer value) { versionNumber = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getRevision() { return revision; }
    public void setRevision(Long value) { revision = value; }
    public Long getAssistantId() { return assistantId; }
    public void setAssistantId(Long value) { assistantId = value; }
    public Long getAssistantVersionId() { return assistantVersionId; }
    public void setAssistantVersionId(Long value) { assistantVersionId = value; }
    public String getAssistantName() { return assistantName; }
    public void setAssistantName(String value) { assistantName = value; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long value) { knowledgeSpaceId = value; }
    public String getKnowledgeSpaceName() { return knowledgeSpaceName; }
    public void setKnowledgeSpaceName(String value) { knowledgeSpaceName = value; }
    public Long getAuthorizationRevision() { return authorizationRevision; }
    public void setAuthorizationRevision(Long value) { authorizationRevision = value; }
    public Long getTenantFeatureVersion() { return tenantFeatureVersion; }
    public void setTenantFeatureVersion(Long value) { tenantFeatureVersion = value; }
    public String getPreviewFingerprint() { return previewFingerprint; }
    public void setPreviewFingerprint(String value) { previewFingerprint = value; }
    public Integer getAllowCount() { return allowCount; }
    public void setAllowCount(Integer value) { allowCount = value; }
    public Integer getDenyCount() { return denyCount; }
    public void setDenyCount(Integer value) { denyCount = value; }
    public Integer getLeakCount() { return leakCount; }
    public void setLeakCount(Integer value) { leakCount = value; }
    public java.time.Instant getPreviewedAt() { return previewedAt; }
    public void setPreviewedAt(java.time.Instant value) { previewedAt = value; }
    public Long getPublishedAuthorizationRevision() { return publishedAuthorizationRevision; }
    public void setPublishedAuthorizationRevision(Long value) { publishedAuthorizationRevision = value; }
}
