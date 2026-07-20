package com.aiplatform.identity.infrastructure.persistence.entity;

import com.aiplatform.identity.application.result.IamUserConfigurationResult;
import java.util.Arrays;
import java.util.List;

public class IamUserConfigurationPersistenceEntity {
    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String status;
    private String roleCodes;

    public IamUserConfigurationResult toResult() {
        List<String> roles = roleCodes == null || roleCodes.isBlank() ? List.of() : Arrays.stream(roleCodes.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
        return new IamUserConfigurationResult(String.valueOf(id), username, displayName, email, status, roles);
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRoleCodes() { return roleCodes; }
    public void setRoleCodes(String roleCodes) { this.roleCodes = roleCodes; }
}
