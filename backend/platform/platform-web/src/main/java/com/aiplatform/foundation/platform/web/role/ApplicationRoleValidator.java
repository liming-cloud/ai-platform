package com.aiplatform.foundation.platform.web.role;

/**
 * 启动期应用角色校验器。
 */
final class ApplicationRoleValidator {

    ApplicationRoleValidator(ApplicationRoleProperties properties) {
        if (properties.enabledRoleCount() != 1) {
            throw new IllegalStateException(
                    "控制面、查询和摄取应用角色必须且只能启用一个");
        }
    }
}
