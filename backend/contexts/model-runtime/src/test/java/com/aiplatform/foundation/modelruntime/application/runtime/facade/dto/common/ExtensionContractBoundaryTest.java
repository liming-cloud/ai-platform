package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.aiplatform.foundation.modelruntime.application.extension.facade.DiscoveredModel;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelCandidateClassification;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderModelDiscoveryObservation;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtensionContractBoundaryTest {

    @Test
    void 扩展公开契约的方法不得暴露模型领域类型() {
        var contracts = List.of(
                ProviderKind.class, ModelType.class, ModelCapability.class,
                PlatformModelCandidateClassification.class, PlatformModelDiscoveryProfile.class,
                DiscoveredModel.class, ProviderModelDiscoveryObservation.class,
                PlatformDiscoveredModelCandidate.class,
                PlatformProviderModelDiscoveryObservation.class);

        assertFalse(contracts.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .anyMatch(method -> isDomain(method.getReturnType())
                        || Arrays.stream(method.getParameterTypes()).anyMatch(ExtensionContractBoundaryTest::isDomain)));
    }

    private static boolean isDomain(Class<?> type) {
        return type.getName().contains(".domain.");
    }
}
