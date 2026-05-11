package org.fujay.ai.platform.tenant.interfaces.request;

public record MenuSaveRequest(
        String menuId,
        String menuCode,
        String menuName,
        String parentCode,
        String routePath,
        String componentKey,
        String platformScope,
        Integer sortOrder,
        String status) {
}
