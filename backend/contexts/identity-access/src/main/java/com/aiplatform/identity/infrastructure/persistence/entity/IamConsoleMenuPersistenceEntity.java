package com.aiplatform.identity.infrastructure.persistence.entity;

import com.aiplatform.identity.application.result.CurrentUserMenuResult;

/**
 * `iam_menu` 授权查询投影，承载 /auth/me 返回控制台菜单所需的最小字段。
 */
public class IamConsoleMenuPersistenceEntity {
    private String code;
    private String name;
    private String platform;
    private String path;
    private String routeName;
    private String componentKey;
    private String permission;
    private Integer sort;

    public CurrentUserMenuResult toResult() {
        if (sort == null) {
            throw new IllegalStateException("菜单排序不能为空");
        }
        return new CurrentUserMenuResult(
                code,
                name,
                platform,
                path,
                routeName,
                componentKey,
                permission,
                sort
        );
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

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
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

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
