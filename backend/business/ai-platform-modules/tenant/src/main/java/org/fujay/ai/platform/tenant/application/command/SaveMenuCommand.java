package org.fujay.ai.platform.tenant.application.command;

public record SaveMenuCommand(
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
