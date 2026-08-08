package com.aiplatform.foundation.assistantdelivery.application.release.facade;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 跨上下文发布复核的最小不可变结果，不包含评测内容或内部失败原因。
 */
public record ReleasePublicationAuthorizationResult(boolean allowed) implements ValueObject {
}
