package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import static org.junit.jupiter.api.Assertions.*;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate;
import org.junit.jupiter.api.Test;

class PlatformDiscoveredModelCandidateNameTest {
    @Test
    void 独立名称不改变调用编码或猜测能力() {
        var candidate = PlatformDiscoveredModelCandidate.requiresAdministratorConfirmation("ep-synthetic-opaque", "受控模型名称");
        assertEquals("ep-synthetic-opaque", candidate.modelCode());
        assertEquals("受控模型名称", candidate.displayName());
        assertNull(candidate.modelType());
        assertTrue(candidate.capabilities().isEmpty());
    }

    @Test
    void 无名称保持缺失且畸形名称不能冻结() {
        assertNull(PlatformDiscoveredModelCandidate.requiresAdministratorConfirmation("ep-synthetic-opaque").displayName());
        for (var name : java.util.List.of("", " ", " 带空格", "含\n控制字符", "长".repeat(121))) {
            assertThrows(IllegalArgumentException.class,
                    () -> PlatformDiscoveredModelCandidate.requiresAdministratorConfirmation("ep-synthetic-opaque", name));
        }
    }
}
