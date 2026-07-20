package com.aiplatform.modelhub.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.modelhub.application.command.CreateModelConfigCommand;
import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelCreationCommandDigestTest {
    private final ModelCreationCommandDigest digest = new ModelCreationCommandDigest();

    @Test
    void mapKeyOrderIsCanonicalButListOrderRemainsSemanticallySignificant() {
        Map<String, Object> firstOrder = new LinkedHashMap<>();
        firstOrder.put("providerKind", "openai-compatible");
        firstOrder.put("temperature", new BigDecimal("0.2"));
        firstOrder.put("stopSequences", List.of("END", "STOP"));
        Map<String, Object> differentMapOrder = new LinkedHashMap<>();
        differentMapOrder.put("stopSequences", List.of("END", "STOP"));
        differentMapOrder.put("temperature", new BigDecimal("0.20"));
        differentMapOrder.put("providerKind", "openai-compatible");
        Map<String, Object> differentListOrder = new LinkedHashMap<>(firstOrder);
        differentListOrder.put("stopSequences", List.of("STOP", "END"));

        String canonical = digest.modelConfig(command(firstOrder));

        assertThat(digest.modelConfig(command(differentMapOrder))).isEqualTo(canonical);
        assertThat(digest.modelConfig(command(differentListOrder))).isNotEqualTo(canonical);
    }

    @Test
    void repeatedListElementsAreRetainedInTheCanonicalCommand() {
        Map<String, Object> repeated = Map.of(
                "providerKind", "openai-compatible",
                "stopSequences", List.of("END", "END", "STOP")
        );
        Map<String, Object> collapsed = Map.of(
                "providerKind", "openai-compatible",
                "stopSequences", List.of("END", "STOP")
        );

        assertThat(digest.modelConfig(command(repeated)))
                .isNotEqualTo(digest.modelConfig(command(collapsed)));
    }

    private static CreateModelConfigCommand command(Map<String, Object> options) {
        return new CreateModelConfigCommand(
                TenantId.of("2001"),
                "1001",
                "model-create-digest-001",
                "9001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "CHAT",
                128000,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                options
        );
    }
}
