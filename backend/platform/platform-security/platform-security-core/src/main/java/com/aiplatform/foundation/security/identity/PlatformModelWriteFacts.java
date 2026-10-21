package com.aiplatform.foundation.security.identity;

/** 平台模型写入短事务所需的当前主体、权限修订与既有模型管理权限事实。 */
@FunctionalInterface
public interface PlatformModelWriteFacts {
    /**
     * 在调用者的短事务内锁定权限所有者的主体根并复核当前事实，不读取凭据。
     *
     * @throws com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException 主体或授权修订已失效。
     */
    void requireCurrent(PlatformRequestContext context);
}
