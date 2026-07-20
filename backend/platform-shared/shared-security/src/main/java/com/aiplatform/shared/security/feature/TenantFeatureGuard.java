package com.aiplatform.shared.security.feature;

import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.ErrorCode;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.feature.port.TenantFeatureAccessPort;
import java.util.Objects;

/**
 * 业务功能入口的租户功能开关守卫，未启用时失败关闭。
 */
public final class TenantFeatureGuard {
    private static final ErrorCode FEATURE_DISABLED = ErrorCode.of(
            "AIP-FEATURE-PERMISSIONDENIED",
            "租户功能未启用"
    );

    private final TenantFeatureAccessPort accessPort;

    public TenantFeatureGuard(TenantFeatureAccessPort accessPort) {
        this.accessPort = Objects.requireNonNull(accessPort, "租户功能读取端口不能为空");
    }

    public void requireEnabled(TenantId tenantId, String featureCode) {
        TenantId requiredTenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        String requiredFeatureCode = requireText(featureCode, "功能编码");
        if (!accessPort.isFeatureEnabled(requiredTenantId, requiredFeatureCode)) {
            throw new ApplicationException(
                    FEATURE_DISABLED,
                    "租户 " + requiredTenantId.value() + " 未启用功能 " + requiredFeatureCode
            );
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
