package org.fujay.ai.platform.model.domain.value;

import org.fujay.ai.platform.shared.ddd.ValueObject;

public enum ModelCapabilityType implements ValueObject {
    CHAT,
    EMBEDDING,
    RERANK,
    IMAGE,
    AUDIO,
    MODERATION
}
