package com.aiplatform.foundation.shared.kernel.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import org.junit.jupiter.api.Test;

class DomainMarkerTest {

    @Test
    void 租户标识应具有值对象语义() {
        var tenantId = new TenantId(1001L);

        assertInstanceOf(ValueObject.class, tenantId);
        assertEquals("1001", tenantId.toString());
    }

    @Test
    void 聚合根应同时具有实体和领域对象语义() {
        AggregateRoot<TestId> aggregate = () -> new TestId(2001L);

        assertInstanceOf(Entity.class, aggregate);
        assertInstanceOf(DomainObject.class, aggregate);
        assertEquals(2001L, aggregate.id().value());
    }

    private record TestId(long value) implements ValueObject {
    }
}
