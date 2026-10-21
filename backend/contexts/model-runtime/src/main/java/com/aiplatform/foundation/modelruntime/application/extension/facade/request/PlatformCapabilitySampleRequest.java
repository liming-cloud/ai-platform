package com.aiplatform.foundation.modelruntime.application.extension.facade.request;

import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 一次平台合成样本的传输合同；不接受厂商密钥、任意参数或租户身份。 */
public record PlatformCapabilitySampleRequest(String binding, ModelType type, String providerPresetId,
        URI endpoint, String credentialReference, long connectionRevision, String modelCode, String input, List<String> documents,
        int maxOutputTokens, int embeddingDimension, int rerankCandidateLimit, Duration timeout) {
    /**
     * 在平台扩展边界再次约束样本及费用相关上限。
     * @throws IllegalArgumentException 绑定、平台引用、能力参数、输入或期限不符合固定合同。
     */
    public PlatformCapabilitySampleRequest {
        if (binding == null || !binding.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("样本绑定不正确");
        Objects.requireNonNull(type, "能力不能为空");
        Objects.requireNonNull(providerPresetId, "厂商预设不能为空");
        Objects.requireNonNull(endpoint, "平台地址不能为空");
        if (connectionRevision < 0 || credentialReference == null || !credentialReference.startsWith("secret://platform-provider-connection/"))
            throw new IllegalArgumentException("只能使用平台推理凭据引用");
        if (modelCode == null || modelCode.isBlank() || modelCode.length() > 200) throw new IllegalArgumentException("模型编码不正确");
        if (input == null || input.isBlank()) throw new IllegalArgumentException("合成输入不能为空");
        documents = List.copyOf(Objects.requireNonNull(documents, "样本材料不能为空"));
        long codePoints = checkedLength(input);
        for (var text : documents) codePoints += checkedLength(Objects.requireNonNull(text, "样本材料不能包含空值"));
        if (codePoints > 128) throw new IllegalArgumentException("样本总输入超过 128 码点");
        if (type == ModelType.CHAT ? maxOutputTokens < 1 || maxOutputTokens > 64 : maxOutputTokens != 0)
            throw new IllegalArgumentException("样本生成上限不正确");
        if (type == ModelType.EMBEDDING ? embeddingDimension <= 0 : embeddingDimension != 0)
            throw new IllegalArgumentException("向量声明不正确");
        if (type == ModelType.RERANK ? documents.size() != 3 || rerankCandidateLimit < 3
                : !documents.isEmpty() || rerankCandidateLimit != 0) throw new IllegalArgumentException("重排声明不正确");
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0)
            throw new IllegalArgumentException("样本期限不正确");
    }

    private static long checkedLength(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new IllegalArgumentException("样本包含无效 Unicode");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("样本包含无效 Unicode");
        }
        return text.codePoints().count();
    }

    @Override
    public String toString() { return "PlatformCapabilitySampleRequest[type=" + type + ", 内容已隐藏]"; }
}
