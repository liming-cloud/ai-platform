package com.aiplatform.appstudio.application.result;

/**
 * 服务端按主体、应用范围与租户权限共同计算的逐应用能力。
 */
public record ApplicationCapabilitiesResult(
        boolean canEditConfiguration,
        boolean canRunPublishPreflight,
        boolean canPublish,
        boolean canRollback,
        boolean canManageAccessPolicy,
        boolean canReadReleaseEvidence
) {
    public static ApplicationCapabilitiesResult owner(
            boolean canEditConfiguration,
            boolean canPublish,
            boolean canManageAccessPolicy
    ) {
        return new ApplicationCapabilitiesResult(
                canEditConfiguration,
                canPublish,
                canPublish,
                canPublish,
                canManageAccessPolicy,
                canPublish || canManageAccessPolicy
        );
    }

    public static ApplicationCapabilitiesResult governanceReadOnly() {
        return new ApplicationCapabilitiesResult(false, false, false, false, false, true);
    }

    public static ApplicationCapabilitiesResult businessUser() {
        return new ApplicationCapabilitiesResult(false, false, false, false, false, false);
    }
}
