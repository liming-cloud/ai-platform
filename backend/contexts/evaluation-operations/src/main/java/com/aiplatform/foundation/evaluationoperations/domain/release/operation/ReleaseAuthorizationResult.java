package com.aiplatform.foundation.evaluationoperations.domain.release.operation;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 发布复核返回的不可变授权结果，不携带评测正文或内部失败细节。
 */
public record ReleaseAuthorizationResult(boolean allowed) implements ValueObject {
}
