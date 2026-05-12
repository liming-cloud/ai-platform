package org.fujay.ai.platform.model.domain.value;

import org.fujay.ai.platform.shared.ddd.ValueObject;

public enum ProviderType implements ValueObject {
    OPENAI,
    OPENAI_COMPATIBLE,
    AZURE_OPENAI,
    NATIVE_VENDOR
}
