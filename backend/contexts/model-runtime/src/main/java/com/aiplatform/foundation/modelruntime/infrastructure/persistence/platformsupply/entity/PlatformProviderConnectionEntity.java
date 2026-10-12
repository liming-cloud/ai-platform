package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 平台连接及其不可变预设来源的基础设施映射，不进入公开契约。 */
@TableName("model_runtime.platform_provider_connection")
public class PlatformProviderConnectionEntity {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String scope;
    private String providerKind;
    private String discoveryProfile;
    private String providerPresetId;
    private String providerPresetVersion;
    private String capabilityIntent;
    private String workspaceId;
    private String displayName;
    private String endpoint;
    private String region;
    private String authenticationType;
    private String credentialReference;
    private String status;
    private Long version;
    private Boolean lastProbeSucceeded;
    private String lastProbeCode;
    private Instant lastProbedAt;
    private Instant createdAt;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getScope() { return scope; }
    public void setScope(String value) { this.scope = value; }
    public String getProviderKind() { return providerKind; }
    public void setProviderKind(String value) { this.providerKind = value; }
    public String getDiscoveryProfile() { return discoveryProfile; }
    public void setDiscoveryProfile(String value) { this.discoveryProfile = value; }
    public String getProviderPresetId() { return providerPresetId; }
    public void setProviderPresetId(String value) { this.providerPresetId = value; }
    public String getProviderPresetVersion() { return providerPresetVersion; }
    public void setProviderPresetVersion(String value) { this.providerPresetVersion = value; }
    public String getCapabilityIntent() { return capabilityIntent; }
    public void setCapabilityIntent(String value) { this.capabilityIntent = value; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String value) { this.workspaceId = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { this.displayName = value; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String value) { this.endpoint = value; }
    public String getRegion() { return region; }
    public void setRegion(String value) { this.region = value; }
    public String getAuthenticationType() { return authenticationType; }
    public void setAuthenticationType(String value) { this.authenticationType = value; }
    public String getCredentialReference() { return credentialReference; }
    public void setCredentialReference(String value) { this.credentialReference = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }
    public Boolean getLastProbeSucceeded() { return lastProbeSucceeded; }
    public void setLastProbeSucceeded(Boolean value) { this.lastProbeSucceeded = value; }
    public String getLastProbeCode() { return lastProbeCode; }
    public void setLastProbeCode(String value) { this.lastProbeCode = value; }
    public Instant getLastProbedAt() { return lastProbedAt; }
    public void setLastProbedAt(Instant value) { this.lastProbedAt = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }
}
