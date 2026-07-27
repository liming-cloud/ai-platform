package com.aiplatform.foundation.trustedquery.domain.query.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
/**
 * 平台主动拒绝生成回答的稳定原因。
 */
public enum QueryRefusalReason implements ValueObject {
    INSUFFICIENT_EVIDENCE
}
