package org.fujay.ai.platform.tenant.domain.aggregate;

import org.fujay.ai.platform.shared.tenant.TenantId;

public record Tenant(TenantId id, String name) {
}
