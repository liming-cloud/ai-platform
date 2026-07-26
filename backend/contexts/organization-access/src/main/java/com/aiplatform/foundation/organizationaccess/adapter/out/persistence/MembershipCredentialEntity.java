package com.aiplatform.foundation.organizationaccess.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.UUID;

/**
 * MyBatis-Plus 使用的成员凭据持久化实体。
 */
@TableName("organization_access.membership_credential")
public class MembershipCredentialEntity {

    @TableId(type = IdType.INPUT)
    private UUID membershipId;

    private UUID tenantId;
    private UUID subjectId;
    private String loginName;
    private String passwordHash;
    private String status;
    private Long permissionVersion;
    private Long version;

    public UUID getMembershipId() {
        return membershipId;
    }

    public void setMembershipId(UUID membershipId) {
        this.membershipId = membershipId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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
