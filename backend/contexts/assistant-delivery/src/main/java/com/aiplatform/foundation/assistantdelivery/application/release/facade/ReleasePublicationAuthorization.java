package com.aiplatform.foundation.assistantdelivery.application.release.facade;

/**
 * 由评测上下文实现的发布授权查询；助手上下文只消费冻结目标与决定标识，不读取评测根。
 */
@FunctionalInterface
public interface ReleasePublicationAuthorization {

    /**
     * 复核某项冻结决定是否允许发布当前草稿修订。
     */
    ReleasePublicationAuthorizationResult authorize(ReleasePublicationRequest request);
}
