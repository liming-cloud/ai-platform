package com.aiplatform.identity.infrastructure.persistence.entity;

import com.aiplatform.identity.application.result.IamRoleResult;

public class IamRolePersistenceEntity {
    private Long id;
    private String code;
    private String name;
    private String type;
    private String defaultMenuCode;
    private String status;
    private String description;

    public IamRoleResult toResult() {
        return new IamRoleResult(
                requireId(id, "角色 ID"),
                requireText(code, "角色编码"),
                requireText(name, "角色名称"),
                requireText(type, "角色类型"),
                defaultMenuCode == null ? null : defaultMenuCode.trim(),
                requireText(status, "角色状态"),
                description == null ? null : description.trim()
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDefaultMenuCode() {
        return defaultMenuCode;
    }

    public void setDefaultMenuCode(String defaultMenuCode) {
        this.defaultMenuCode = defaultMenuCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
