package com.aiplatform.modelhub.application.idempotency;

import com.aiplatform.modelhub.application.command.CreateModelConfigCommand;
import com.aiplatform.modelhub.application.command.CreateModelProviderCommand;
import com.aiplatform.modelhub.domain.ModelPriceContract;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Canonical ModelHub create-command digest.
 *
 * <p>Credential material is reduced to a one-way fingerprint before it enters the canonical command;
 * neither the canonical command nor the persisted request digest contains credential plaintext.</p>
 */
public final class ModelCreationCommandDigest {
    private static final String SCHEMA = "model-creation-command/v1";

    public String provider(CreateModelProviderCommand command, String normalizedEndpoint) {
        String credentialFingerprint = command.credentialMaterialInput() == null
                ? "absent"
                : sha256("credential/v1" + lengthPrefixed(command.credentialMaterialInput()));
        return sha256(String.join("",
                lengthPrefixed(SCHEMA),
                lengthPrefixed(ModelCreationResourceType.MODEL_PROVIDER.name()),
                lengthPrefixed(command.tenantId().value()),
                lengthPrefixed(command.operatorId()),
                lengthPrefixed(command.providerType()),
                lengthPrefixed(command.providerName()),
                lengthPrefixed(normalizedEndpoint),
                lengthPrefixed(command.authType()),
                lengthPrefixed(credentialFingerprint)
        ));
    }

    public String modelConfig(CreateModelConfigCommand command) {
        return sha256(String.join("",
                lengthPrefixed(SCHEMA),
                lengthPrefixed(ModelCreationResourceType.MODEL_CONFIG.name()),
                lengthPrefixed(command.tenantId().value()),
                lengthPrefixed(command.operatorId()),
                lengthPrefixed(command.providerId()),
                lengthPrefixed(command.modelCode()),
                lengthPrefixed(command.modelName()),
                lengthPrefixed(command.modelType().toUpperCase(java.util.Locale.ROOT)),
                lengthPrefixed(String.valueOf(command.contextWindow())),
                lengthPrefixed(command.embeddingDimension() == null ? "null" : String.valueOf(command.embeddingDimension())),
                lengthPrefixed(ModelPriceContract.canonicalDatabaseString(command.inputPricePer1k(), "输入单价")),
                lengthPrefixed(ModelPriceContract.canonicalDatabaseString(command.outputPricePer1k(), "输出单价")),
                lengthPrefixed(encode(command.options()))
        ));
    }

    private static String encode(Object value) {
        if (value == null) return "N";
        if (value instanceof String text) return "S" + lengthPrefixed(text);
        if (value instanceof Boolean bool) return bool ? "B1" : "B0";
        if (value instanceof Number number) return "D" + lengthPrefixed(canonicalNumber(number));
        if (value instanceof Map<?, ?> map) {
            List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
            StringBuilder encoded = new StringBuilder("M").append(entries.size()).append(':');
            for (Map.Entry<?, ?> entry : entries) {
                encoded.append(lengthPrefixed(String.valueOf(entry.getKey())))
                        .append(lengthPrefixed(encode(entry.getValue())));
            }
            return encoded.toString();
        }
        if (value instanceof Collection<?> collection) {
            // JSON arrays are ordered: preserve iteration order so semantically distinct requests cannot replay.
            List<String> values = collection.stream().map(ModelCreationCommandDigest::encode).toList();
            StringBuilder encoded = new StringBuilder("L").append(values.size()).append(':');
            values.forEach(item -> encoded.append(lengthPrefixed(item)));
            return encoded.toString();
        }
        throw new IllegalArgumentException("模型创建命令包含不支持的摘要值类型");
    }

    private static String canonicalNumber(Number number) {
        try {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("模型创建命令包含非法数字", exception);
        }
    }

    private static String lengthPrefixed(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return bytes.length + ":" + value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
