package org.fujay.ai.platform.tenant.interfaces.request;

import java.util.List;

public record RoleBindingRequest(List<String> permissionCodes, List<String> menuCodes) {
}
