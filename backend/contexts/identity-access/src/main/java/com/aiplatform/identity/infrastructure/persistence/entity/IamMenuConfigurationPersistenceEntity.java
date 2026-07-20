package com.aiplatform.identity.infrastructure.persistence.entity;

import com.aiplatform.identity.application.result.IamMenuConfigurationResult;
import java.util.Arrays;
import java.util.List;

public class IamMenuConfigurationPersistenceEntity {
    private Long id;
    private String code;
    private String name;
    private String scope;
    private String path;
    private String routeName;
    private String componentKey;
    private String icon;
    private String requiredPermissionCode;
    private Integer sort;
    private String status;
    private Boolean systemBuiltin;
    private String featureCodes;
    private Boolean tenantFeatureEnabled;

    public IamMenuConfigurationResult toResult() {
        return new IamMenuConfigurationResult(
                requireId(id, "菜单 ID"),
                requireText(code, "菜单编码"),
                requireText(name, "菜单名称"),
                requireText(scope, "菜单范围"),
                requireText(path, "菜单路径"),
                requireText(routeName, "路由名称"),
                requireText(componentKey, "组件键"),
                icon == null ? null : icon.trim(),
                requireText(requiredPermissionCode, "菜单权限编码"),
                requireValue(sort, "菜单排序"),
                requireText(status, "菜单状态"),
                requireValue(systemBuiltin, "系统内置标记"),
                splitFeatureCodes(featureCodes),
                requireValue(tenantFeatureEnabled, "租户功能启用标记")
        );
    }

    private static List<String> splitFeatureCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private static String requireId(Long value, String fieldName) {
        return requireValue(value, fieldName).toString();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value;
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

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getComponentKey() {
        return componentKey;
    }

    public void setComponentKey(String componentKey) {
        this.componentKey = componentKey;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getRequiredPermissionCode() {
        return requiredPermissionCode;
    }

    public void setRequiredPermissionCode(String requiredPermissionCode) {
        this.requiredPermissionCode = requiredPermissionCode;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getSystemBuiltin() {
        return systemBuiltin;
    }

    public void setSystemBuiltin(Boolean systemBuiltin) {
        this.systemBuiltin = systemBuiltin;
    }

    public String getFeatureCodes() {
        return featureCodes;
    }

    public void setFeatureCodes(String featureCodes) {
        this.featureCodes = featureCodes;
    }

    public Boolean getTenantFeatureEnabled() {
        return tenantFeatureEnabled;
    }

    public void setTenantFeatureEnabled(Boolean tenantFeatureEnabled) {
        this.tenantFeatureEnabled = tenantFeatureEnabled;
    }
}
