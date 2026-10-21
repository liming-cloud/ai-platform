package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.exc.ValueInstantiationException;

class PlatformSupplyRevokeRequestTest {
    @Test void 只接受两个明确的非负整数修订() throws Exception {
        var json = JsonMapper.builder().build();
        var request = json.readValue("{\"expectedVersion\":2,\"expectedGrantVersion\":0}", PlatformSupplyRevokeRequest.class);
        assertEquals(2, request.deploymentVersion()); assertEquals(0, request.grantVersion());
        for (var value : new String[]{"null", "\"0\"", "false", "0.5", "-1", "9223372036854775808"}) {
            assertInstanceOf(IllegalArgumentException.class, assertThrows(ValueInstantiationException.class, () -> json.readValue("{\"expectedVersion\":" + value + ",\"expectedGrantVersion\":0}", PlatformSupplyRevokeRequest.class)).getCause());
            assertInstanceOf(IllegalArgumentException.class, assertThrows(ValueInstantiationException.class, () -> json.readValue("{\"expectedVersion\":2,\"expectedGrantVersion\":" + value + "}", PlatformSupplyRevokeRequest.class)).getCause());
        }
        assertInstanceOf(IllegalArgumentException.class, assertThrows(ValueInstantiationException.class, () -> json.readValue("{}", PlatformSupplyRevokeRequest.class)).getCause());
    }
}
