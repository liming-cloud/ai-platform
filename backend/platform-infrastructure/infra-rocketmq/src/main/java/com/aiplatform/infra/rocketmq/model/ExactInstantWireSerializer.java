package com.aiplatform.infra.rocketmq.model;

import java.time.Instant;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * Serializes event time as the canonical ISO-8601 value without applying a mapper-wide
 * millisecond truncation policy. Approval evidence binds this value cryptographically,
 * so losing sub-millisecond precision on the broker wire would invalidate the event.
 */
public final class ExactInstantWireSerializer extends StdScalarSerializer<Instant> {
    public ExactInstantWireSerializer() {
        super(Instant.class);
    }

    @Override
    public void serialize(
            Instant value,
            JsonGenerator generator,
            SerializationContext context
    ) throws JacksonException {
        generator.writeString(value.toString());
    }
}
