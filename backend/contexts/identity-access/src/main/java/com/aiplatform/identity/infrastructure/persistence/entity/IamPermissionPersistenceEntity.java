package com.aiplatform.identity.infrastructure.persistence.entity;

import com.aiplatform.identity.application.result.IamPermissionResult;

public class IamPermissionPersistenceEntity {
    private Long id;
    private String code;
    private String name;
    private String resourceType;
    private String actionCode;
    private String description;
    private String status;

    public IamPermissionResult toResult() {
        return new IamPermissionResult(
                requireId(id, "权限 ID"),
                requireText(code, "权限编码"),
                requireText(name, "权限名称"),
                requireText(resourceType, "资源类型"),
                requireText(actionCode, "动作编码"),
                description == null ? null : description.trim(),
                requireText(status, "权限状态")
        );
    }

    private static String requireId(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value.toString();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value.trim();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
