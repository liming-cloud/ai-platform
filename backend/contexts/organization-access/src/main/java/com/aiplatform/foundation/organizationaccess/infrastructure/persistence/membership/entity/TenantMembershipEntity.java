package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * MyBatis-Plus 使用的租户成员资格持久化实体。
 */
@TableName("organization_access.tenant_membership")
public class TenantMembershipEntity {

    @TableId(type = IdType.INPUT)
    private Long membershipId;

    private Long tenantId;
    private Long subjectId;
    private String membershipRole;
    private String status;
    private Long permissionVersion;
    private Long version;

    public Long getMembershipId() {
        return membershipId;
    }

    public void setMembershipId(Long membershipId) {
        this.membershipId = membershipId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getMembershipRole() {
        return membershipRole;
    }

    public void setMembershipRole(String membershipRole) {
        this.membershipRole = membershipRole;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getPermissionVersion() {
        return permissionVersion;
    }

    public void setPermissionVersion(Long permissionVersion) {
        this.permissionVersion = permissionVersion;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
