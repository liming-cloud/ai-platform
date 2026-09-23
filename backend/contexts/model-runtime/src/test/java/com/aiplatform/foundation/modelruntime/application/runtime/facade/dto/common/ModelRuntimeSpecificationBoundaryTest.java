package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ModelRuntimeSpecificationBoundaryTest {

    @Test
    void 扩展可见的模型规格不得暴露领域类型() {
        assertFalse(Arrays.stream(ModelRuntimeSpecification.class.getRecordComponents())
                .anyMatch(component -> component.getType().getName().contains(".domain.")));
    }
}
