package org.fujay.ai.platform.model.domain.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.fujay.ai.platform.model.domain.model.ConfiguredModel;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelErrorCode;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.shared.ddd.AggregateRoot;

public record ModelInvocation(
        Long id,
        Long tenantId,
        Long providerId,
        Long modelId,
        ProviderType providerType,
        String modelCode,
        ModelCapabilityType capabilityType,
        String invocationKind,
        String traceId,
        String status,
        long latencyMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer inputChars,
        Integer outputChars,
        BigDecimal costUsd,
        String costMissingReason,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt) implements AggregateRoot {

    private static final int MAX_ERROR_LENGTH = 512;

    public static ModelInvocation success(
            Long id,
            ModelProvider provider,
            ConfiguredModel model,
            String invocationKind,
            String traceId,
            long latencyMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            int inputChars,
            int outputChars,
            BigDecimal costUsd,
            String costMissingReason) {
        return new ModelInvocation(
                id,
                provider.tenantId(),
                provider.id(),
                model.id(),
                provider.providerType(),
                model.modelCode(),
                model.capabilityType(),
                invocationKind,
                traceId,
                "SUCCESS",
                latencyMs,
                promptTokens,
                completionTokens,
                totalTokens,
                inputChars,
                outputChars,
                costUsd,
                costMissingReason,
                null,
                null,
                LocalDateTime.now());
    }

    public static ModelInvocation failed(
            Long id,
            ModelProvider provider,
            ConfiguredModel model,
            String invocationKind,
            String traceId,
            long latencyMs,
            int inputChars,
            String costMissingReason,
            ModelErrorCode errorCode,
            RuntimeException failure) {
        String message = failure.getMessage() == null ? errorCode.message() : failure.getMessage();
        return new ModelInvocation(
                id,
                provider.tenantId(),
                provider.id(),
                model.id(),
                provider.providerType(),
                model.modelCode(),
                model.capabilityType(),
                invocationKind,
                traceId,
                "FAILED",
                latencyMs,
                null,
                null,
                null,
                inputChars,
                null,
                null,
                costMissingReason,
                errorCode.code(),
                message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message,
                LocalDateTime.now());
    }
}
