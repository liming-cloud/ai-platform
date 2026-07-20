package com.aiplatform.approval.infrastructure.messaging.model;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope;
import com.aiplatform.shared.kernel.ModelApprovalRuntimeOptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Consumer-side structural contract for model approval evidence. A self-consistent hash is insufficient on its own.
 */
public final class ModelApprovalEvidenceContract {
    private static final Set<String> TOP_LEVEL_KEYS = Set.of(
            "tenantId", "approvalRequestId", "approvalType", "action", "sourceContext",
            "resourceType", "resourceId", "providerId", "modelConfigId", "requesterId",
            "requestedAt", "riskLevel", "expectedPostDecisionState", "before", "after",
            "target", "changedFields"
    );
    private static final Set<String> PROVIDER_KEYS = Set.of(
            "providerId", "providerCode", "providerKind", "providerName", "endpoint", "authType",
            "credentialConfigured", "outboundMode", "approvalRequiredForConfigChange", "expectedStatus"
    );
    private static final Set<String> PROVIDER_CONTEXT_KEYS = Set.of(
            "providerId", "providerCode", "providerKind", "providerName", "endpoint"
    );
    private static final Set<String> CONFIG_TARGET_KEYS = Set.of(
            "modelConfigId", "modelCode", "modelName", "modelType", "contextWindow",
            "embeddingDimension", "inputPricePer1k", "outputPricePer1k", "runtimeOptions",
            "expectedStatus", "connectivityStatus", "connectivityCheckedAt", "connectivityMessage"
    );
    private static final Set<String> CONFIG_AFTER_KEYS;
    static {
        Set<String> keys = new TreeSet<>(CONFIG_TARGET_KEYS);
        keys.add("provider");
        CONFIG_AFTER_KEYS = Set.copyOf(keys);
    }

    private ModelApprovalEvidenceContract() {
    }

    public static ApprovalEvidenceEnvelope validate(
            DomainEventMessage message,
            ModelConfigApprovalPayload payload
    ) {
        Objects.requireNonNull(message, "领域事件不能为空");
        Objects.requireNonNull(payload, "模型审批载荷不能为空");
        ApprovalEvidenceEnvelope envelope = new ApprovalEvidenceEnvelope(
                payload.evidenceSchema(),
                payload.evidenceDigest(),
                payload.evidence()
        );
        Map<String, Object> facts = envelope.facts();
        requireExactKeys(facts, TOP_LEVEL_KEYS, "模型审批证据顶层");
        requireFact(facts, "tenantId", message.tenantId());
        requireFact(facts, "approvalRequestId", payload.approvalRequestId());
        requireFact(facts, "approvalType", payload.approvalType());
        requireFact(facts, "sourceContext", "model-hub");
        requireFact(facts, "resourceType", payload.sourceResourceType());
        requireFact(facts, "resourceId", payload.sourceResourceId());
        requireFact(facts, "providerId", payload.providerId());
        requireFact(facts, "modelConfigId", payload.modelConfigId());
        requireFact(facts, "requesterId", payload.operatorId());
        requireCanonicalRequestedAt(facts, message.occurredAt());
        requireFact(facts, "riskLevel", payload.riskLevel());

        Map<String, Object> after = requireMap(facts.get("after"), "after");
        Map<String, Object> target = requireMap(facts.get("target"), "target");
        String expectedTargetDigest = ApprovalEvidenceEnvelope.digestFacts(Map.of("target", target));
        if (!constantTimeEquals(expectedTargetDigest, requireDigest(payload.targetDigest(), "模型审批目标摘要"))) {
            throw new IllegalArgumentException("模型审批目标摘要与证据 target 不一致");
        }

        switch (payload.sourceResourceType()) {
            case "model_provider" -> validateProvider(payload, facts, after, target);
            case "model_config" -> validateConfig(payload, facts, after, target);
            default -> throw new IllegalArgumentException("模型审批证据资源类型不支持：" + payload.sourceResourceType());
        }
        validateChangedFields(facts, after);
        return envelope;
    }

    private static void validateProvider(
            ModelConfigApprovalPayload payload,
            Map<String, Object> facts,
            Map<String, Object> after,
            Map<String, Object> target
    ) {
        requireFact(facts, "action", "MODEL_PROVIDER_CREATE");
        requireFact(facts, "expectedPostDecisionState", "ACTIVE");
        if (facts.get("before") != null || payload.modelConfigId() != null) {
            throw new IllegalArgumentException("模型供应商创建审批 before/modelConfigId 必须为空");
        }
        requireExactKeys(after, PROVIDER_KEYS, "模型供应商 after");
        if (!after.equals(target)) {
            throw new IllegalArgumentException("模型供应商审批 target 必须等于 after");
        }
        requireFact(after, "providerId", payload.providerId());
        requireTextFact(after, "providerCode");
        requireTextFact(after, "providerKind");
        requireTextFact(after, "providerName");
        requireTextFact(after, "endpoint");
        requireTextFact(after, "authType");
        requireBooleanFact(after, "credentialConfigured");
        requireBooleanFact(after, "approvalRequiredForConfigChange");
        requireOneOf(after, "outboundMode", Set.of("PUBLIC_HTTPS_443", "LOCAL_NO_NETWORK"));
        requireFact(after, "expectedStatus", "ACTIVE");
        validateProviderSecurityFacts(after);
    }

    private static void validateConfig(
            ModelConfigApprovalPayload payload,
            Map<String, Object> facts,
            Map<String, Object> after,
            Map<String, Object> target
    ) {
        Object actionValue = facts.get("action");
        if (!(actionValue instanceof String action)
                || !("MODEL_CONFIG_CREATE".equals(action) || "MODEL_CONFIG_UPDATE".equals(action))) {
            throw new IllegalArgumentException("模型配置审批 action 不受支持");
        }
        requireFact(facts, "expectedPostDecisionState", "INACTIVE+NOT_TESTED");
        if (payload.modelConfigId() == null || !payload.modelConfigId().equals(payload.sourceResourceId())) {
            throw new IllegalArgumentException("模型配置审批 modelConfigId 与资源 ID 不一致");
        }
        requireExactKeys(after, CONFIG_AFTER_KEYS, "模型配置 after");
        requireExactKeys(target, CONFIG_TARGET_KEYS, "模型配置 target");
        Map<String, Object> afterWithoutProvider = new LinkedHashMap<>(after);
        Map<String, Object> providerContext = requireMap(afterWithoutProvider.remove("provider"), "provider");
        requireExactKeys(providerContext, PROVIDER_CONTEXT_KEYS, "模型配置 provider 上下文");
        requireFact(providerContext, "providerId", payload.providerId());
        requireTextFact(providerContext, "providerCode");
        requireTextFact(providerContext, "providerKind");
        requireTextFact(providerContext, "providerName");
        requireTextFact(providerContext, "endpoint");
        validateProviderIdentityAndEndpoint(providerContext);
        if (!afterWithoutProvider.equals(target)) {
            throw new IllegalArgumentException("模型配置审批 target 必须等于 after 中的资源事实");
        }
        requireFact(target, "modelConfigId", payload.modelConfigId());
        requireTextFact(target, "modelCode");
        requireTextFact(target, "modelName");
        requireOneOf(target, "modelType", Set.of(
                "CHAT", "REASONING", "EMBEDDING", "RERANK", "IMAGE_GENERATION",
                "IMAGE_UNDERSTANDING", "SPEECH_TO_TEXT", "TEXT_TO_SPEECH"
        ));
        requirePositiveNumberFact(target, "contextWindow");
        Object dimension = target.get("embeddingDimension");
        if (dimension != null && (!(dimension instanceof Number) || decimal(dimension).signum() <= 0)) {
            throw new IllegalArgumentException("模型审批 embeddingDimension 必须是数字或 null");
        }
        requireCanonicalPriceFact(target, "inputPricePer1k");
        requireCanonicalPriceFact(target, "outputPricePer1k");
        Map<String, Object> options = requireMap(target.get("runtimeOptions"), "runtimeOptions");
        Map<String, Object> normalizedOptions = ModelApprovalRuntimeOptions.validateAndNormalize(options);
        if (!normalizedOptions.equals(options)) {
            throw new IllegalArgumentException("模型审批 runtimeOptions 未按 canonical 类型编码");
        }
        if (!Objects.equals(options.get("providerKind"), providerContext.get("providerKind"))) {
            throw new IllegalArgumentException("模型审批 runtimeOptions.providerKind 与供应商上下文不一致");
        }
        requireFact(target, "expectedStatus", "INACTIVE");
        requireFact(target, "connectivityStatus", "NOT_TESTED");
        requireFact(target, "connectivityCheckedAt", null);
        requireFact(target, "connectivityMessage", "");

        Object before = facts.get("before");
        if ("MODEL_CONFIG_CREATE".equals(action)) {
            if (before != null) {
                throw new IllegalArgumentException("模型配置创建审批 before 必须为空");
            }
        } else {
            Map<String, Object> beforeFacts = requireMap(before, "before");
            requireExactKeys(beforeFacts, CONFIG_AFTER_KEYS, "模型配置 before");
            validateConfigSnapshot(beforeFacts, payload.providerId(), payload.modelConfigId(), "before");
        }
    }

    private static void validateProviderSecurityFacts(Map<String, Object> facts) {
        String authType = (String) facts.get("authType");
        if (!Set.of("api_key", "no_auth", "aws_credentials", "gcp_service_account").contains(authType)) {
            throw new IllegalArgumentException("模型审批 authType 不受支持");
        }
        boolean credentialConfigured = (Boolean) facts.get("credentialConfigured");
        if (credentialConfigured != !"no_auth".equals(authType)) {
            throw new IllegalArgumentException("模型审批 credentialConfigured 与 authType 不一致");
        }
        String providerKind = (String) facts.get("providerKind");
        if (!Set.of("openai-compatible", "azure-openai", "ollama", "mistral-ai", "bedrock",
                "vertex-ai", "local-deterministic").contains(providerKind)) {
            throw new IllegalArgumentException("模型审批 providerKind 不受支持");
        }
        String expectedOutbound = "local-deterministic".equals(providerKind)
                ? "LOCAL_NO_NETWORK"
                : "PUBLIC_HTTPS_443";
        requireFact(facts, "outboundMode", expectedOutbound);
        String expectedAuthType = switch (providerKind) {
            case "ollama", "local-deterministic" -> "no_auth";
            case "bedrock" -> "aws_credentials";
            case "vertex-ai" -> "gcp_service_account";
            default -> "api_key";
        };
        requireFact(facts, "authType", expectedAuthType);
        validateProviderIdentityAndEndpoint(facts);
    }

    private static void validateConfigSnapshot(
            Map<String, Object> snapshot,
            String providerId,
            String modelConfigId,
            String fieldName
    ) {
        Map<String, Object> provider = requireMap(snapshot.get("provider"), fieldName + ".provider");
        requireExactKeys(provider, PROVIDER_CONTEXT_KEYS, fieldName + ".provider");
        requireFact(provider, "providerId", providerId);
        requireTextFact(provider, "providerCode");
        requireTextFact(provider, "providerKind");
        requireTextFact(provider, "providerName");
        requireTextFact(provider, "endpoint");
        validateProviderIdentityAndEndpoint(provider);
        requireFact(snapshot, "modelConfigId", modelConfigId);
        requireTextFact(snapshot, "modelCode");
        requireTextFact(snapshot, "modelName");
        requireOneOf(snapshot, "modelType", Set.of(
                "CHAT", "REASONING", "EMBEDDING", "RERANK", "IMAGE_GENERATION",
                "IMAGE_UNDERSTANDING", "SPEECH_TO_TEXT", "TEXT_TO_SPEECH"
        ));
        requirePositiveNumberFact(snapshot, "contextWindow");
        Object dimension = snapshot.get("embeddingDimension");
        if (dimension != null && (!(dimension instanceof Number) || decimal(dimension).signum() <= 0)) {
            throw new IllegalArgumentException("模型审批 " + fieldName + ".embeddingDimension 必须是正数或 null");
        }
        requireCanonicalPriceFact(snapshot, "inputPricePer1k");
        requireCanonicalPriceFact(snapshot, "outputPricePer1k");
        Map<String, Object> options = requireMap(snapshot.get("runtimeOptions"), fieldName + ".runtimeOptions");
        if (!ModelApprovalRuntimeOptions.validateAndNormalize(options).equals(options)) {
            throw new IllegalArgumentException("模型审批 " + fieldName + ".runtimeOptions 未按 canonical 类型编码");
        }
        if (!Objects.equals(options.get("providerKind"), provider.get("providerKind"))) {
            throw new IllegalArgumentException("模型审批 " + fieldName + " 运行时与供应商类型不一致");
        }
        requireOneOf(snapshot, "expectedStatus", Set.of("PENDING_APPROVAL", "INACTIVE", "ACTIVE", "DEPRECATED", "REJECTED"));
        requireOneOf(snapshot, "connectivityStatus", Set.of("NOT_TESTED", "PASSED", "FAILED"));
        Object checkedAt = snapshot.get("connectivityCheckedAt");
        if (checkedAt != null && (!(checkedAt instanceof String text) || text.isBlank())) {
            throw new IllegalArgumentException("模型审批 " + fieldName + ".connectivityCheckedAt 必须是时间字符串或 null");
        }
        if (!(snapshot.get("connectivityMessage") instanceof String)) {
            throw new IllegalArgumentException("模型审批 " + fieldName + ".connectivityMessage 必须是字符串");
        }
    }

    private static void validateChangedFields(Map<String, Object> facts, Map<String, Object> after) {
        Object changedValue = facts.get("changedFields");
        if (!(changedValue instanceof List<?> changed) || changed.isEmpty()) {
            throw new IllegalArgumentException("模型审批 changedFields 不能为空");
        }
        Set<String> actual = new TreeSet<>();
        for (Object item : changed) {
            if (!(item instanceof String path) || path.isBlank()) {
                throw new IllegalArgumentException("模型审批 changedFields 必须是非空字符串数组");
            }
            actual.add(path.trim());
        }
        Set<String> expected;
        if (facts.get("before") == null) {
            expected = new TreeSet<>(after.keySet());
        } else {
            expected = new TreeSet<>();
            collectChangedFields("", requireMap(facts.get("before"), "before"), after, expected);
        }
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("模型审批 changedFields 与 before/after 差异不一致");
        }
    }

    private static void collectChangedFields(
            String prefix,
            Map<String, Object> before,
            Map<String, Object> after,
            Set<String> changed
    ) {
        Set<String> keys = new TreeSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object left = before.get(key);
            Object right = after.get(key);
            if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
                collectChangedFields(path, stringMap(leftMap), stringMap(rightMap), changed);
            } else if (!Objects.equals(left, right)) {
                changed.add(path);
            }
        }
    }

    private static Map<String, Object> stringMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    private static void requireExactKeys(Map<String, Object> facts, Set<String> expected, String fieldName) {
        if (!facts.keySet().equals(expected)) {
            throw new IllegalArgumentException(fieldName + "字段集合不合法");
        }
    }

    private static void validateProviderIdentityAndEndpoint(Map<String, Object> provider) {
        String providerCode = (String) provider.get("providerCode");
        String providerKind = (String) provider.get("providerKind");
        String canonicalKind = canonicalProviderKind(providerCode);
        if (!canonicalKind.equals(providerKind)) {
            throw new IllegalArgumentException("模型审批 providerCode 与 providerKind 不一致");
        }
        validateEndpoint(providerKind, (String) provider.get("endpoint"));
    }

    private static String canonicalProviderKind(String providerCode) {
        String normalized = providerCode.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "openai", "openai-compatible" -> "openai-compatible";
            case "azure_openai", "azureopenai", "azure-openai" -> "azure-openai";
            case "ollama" -> "ollama";
            case "mistral_ai", "mistral", "mistral-ai" -> "mistral-ai";
            case "amazon-bedrock", "amazon_bedrock", "aws-bedrock", "aws_bedrock", "bedrock" -> "bedrock";
            case "vertex_ai", "vertexai", "google-vertex-ai", "google_vertex_ai",
                    "gcp-vertex-ai", "gcp_vertex_ai", "vertex-ai" -> "vertex-ai";
            case "local-deterministic" -> "local-deterministic";
            default -> throw new IllegalArgumentException("模型审批 providerCode 不受支持");
        };
    }

    private static void validateEndpoint(String providerKind, String endpoint) {
        if ("local-deterministic".equals(providerKind)) {
            if (!"local://deterministic".equals(endpoint)) {
                throw new IllegalArgumentException("本地模型审批 endpoint 必须是 local://deterministic");
            }
            return;
        }
        final java.net.URI uri;
        try {
            uri = new java.net.URI(endpoint);
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("模型审批 endpoint 格式不合法", exception);
        }
        if (!uri.isAbsolute()
                || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getRawUserInfo() != null
                || (uri.getRawAuthority() != null && uri.getRawAuthority().contains("@"))
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("外部模型审批 endpoint 必须是无 userinfo/query/fragment 的 HTTPS/443 地址");
        }
        String authority = uri.getRawAuthority();
        if (authority == null
                || authority.indexOf('%') >= 0
                || authority.codePoints().anyMatch(codePoint -> codePoint > 0x7f)) {
            throw new IllegalArgumentException("外部模型审批 endpoint authority 不合法");
        }
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if (host.equals("localhost") || host.equals("local") || host.endsWith(".local")
                || host.endsWith(".internal")) {
            throw new IllegalArgumentException("外部模型审批 endpoint 不允许本机或内部主机名");
        }
    }

    private static void requireFact(Map<String, Object> facts, String name, Object expected) {
        if (!Objects.equals(facts.get(name), expected) || (!facts.containsKey(name) && expected == null)) {
            throw new IllegalArgumentException("模型审批证据字段不一致：" + name);
        }
    }

    private static void requireCanonicalRequestedAt(Map<String, Object> facts, Instant occurredAt) {
        Object value = facts.get("requestedAt");
        if (!(value instanceof String requestedAt) || requestedAt.isBlank()) {
            throw new IllegalArgumentException("模型审批证据字段不一致：requestedAt");
        }
        final Instant parsed;
        try {
            parsed = Instant.parse(requestedAt);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("模型审批 requestedAt 必须是 UTC 时间", exception);
        }
        Instant expected = Objects.requireNonNull(occurredAt, "事件发生时间不能为空")
                .truncatedTo(ChronoUnit.MICROS);
        if (!parsed.equals(expected) || !requestedAt.equals(expected.toString())) {
            throw new IllegalArgumentException("模型审批证据字段不一致：requestedAt");
        }
    }

    private static void requireTextFact(Map<String, Object> facts, String name) {
        if (!(facts.get(name) instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("模型审批证据字段不能为空：" + name);
        }
    }

    private static void requireBooleanFact(Map<String, Object> facts, String name) {
        if (!(facts.get(name) instanceof Boolean)) {
            throw new IllegalArgumentException("模型审批证据字段必须是布尔值：" + name);
        }
    }

    private static void requireNumberFact(Map<String, Object> facts, String name) {
        if (!(facts.get(name) instanceof Number)) {
            throw new IllegalArgumentException("模型审批证据字段必须是数字：" + name);
        }
    }

    private static void requirePositiveNumberFact(Map<String, Object> facts, String name) {
        requireNumberFact(facts, name);
        if (decimal(facts.get(name)).signum() <= 0) {
            throw new IllegalArgumentException("模型审批证据字段必须是正数：" + name);
        }
    }

    private static void requireCanonicalPriceFact(Map<String, Object> facts, String name) {
        Object value = facts.get(name);
        if (!(value instanceof String text)
                || !text.matches("(?:0|[1-9][0-9]{0,9})(?:\\.[0-9]{1,8})?")) {
            throw new IllegalArgumentException("模型审批价格必须是 canonical NUMERIC(18,8) 字符串：" + name);
        }
        java.math.BigDecimal decimal = new java.math.BigDecimal(text);
        String canonical = decimal.signum() == 0 ? "0" : decimal.stripTrailingZeros().toPlainString();
        if (!canonical.equals(text)) {
            throw new IllegalArgumentException("模型审批价格不是 canonical 十进制值：" + name);
        }
    }

    private static java.math.BigDecimal decimal(Object value) {
        try {
            return value instanceof java.math.BigDecimal decimal
                    ? decimal
                    : new java.math.BigDecimal(value.toString());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("模型审批数字必须是有限十进制值", exception);
        }
    }

    private static void requireOneOf(Map<String, Object> facts, String name, Set<String> allowed) {
        Object value = facts.get(name);
        if (!(value instanceof String text) || !allowed.contains(text)) {
            throw new IllegalArgumentException("模型审批证据字段取值不支持：" + name);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireMap(Object value, String fieldName) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("模型审批证据 " + fieldName + " 必须是对象");
        }
        return (Map<String, Object>) map;
    }

    private static String requireDigest(String value, String fieldName) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(fieldName + "必须是 64 位 SHA-256 十六进制值");
        }
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)
        );
    }
}
