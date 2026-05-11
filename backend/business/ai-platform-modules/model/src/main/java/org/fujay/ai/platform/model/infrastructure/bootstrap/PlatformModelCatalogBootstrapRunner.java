package org.fujay.ai.platform.model.infrastructure.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.PlatformModelOptionMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.PlatformProviderTypeMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.po.PlatformModelOptionPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.PlatformProviderTypePO;
import org.fujay.ai.platform.shared.id.SnowflakeIdGenerator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class PlatformModelCatalogBootstrapRunner implements ApplicationRunner {

    private static final String STATUS_ENABLED = "ENABLED";

    private final PlatformProviderTypeMapper providerTypeMapper;
    private final PlatformModelOptionMapper modelOptionMapper;

    public PlatformModelCatalogBootstrapRunner(
            PlatformProviderTypeMapper providerTypeMapper, PlatformModelOptionMapper modelOptionMapper) {
        this.providerTypeMapper = providerTypeMapper;
        this.modelOptionMapper = modelOptionMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        PlatformProviderTypePO openAi = upsertProviderType(
                "OPENAI", "OpenAI", "https://api.openai.com", 10, now);
        PlatformProviderTypePO compatible = upsertProviderType(
                "OPENAI_COMPATIBLE", "OpenAI 兼容供应商", "https://api.siliconflow.cn", 20, now);
        for (DefaultModel model : defaultModels(openAi.getId(), compatible.getId())) {
            upsertModelOption(model, now);
        }
    }

    private PlatformProviderTypePO upsertProviderType(
            String providerType, String displayName, String defaultBaseUrl, Integer sortOrder, LocalDateTime now) {
        PlatformProviderTypePO po = providerTypeMapper.selectOne(Wrappers.<PlatformProviderTypePO>lambdaQuery()
                .eq(PlatformProviderTypePO::getProviderType, providerType));
        if (po == null) {
            po = new PlatformProviderTypePO();
            po.setId(SnowflakeIdGenerator.nextId());
            po.setProviderType(providerType);
            po.setCreatedAt(now);
        }
        po.setDisplayName(displayName);
        po.setDefaultBaseUrl(defaultBaseUrl);
        po.setStatus(STATUS_ENABLED);
        po.setSortOrder(sortOrder);
        po.setUpdatedAt(now);
        if (providerTypeMapper.selectById(po.getId()) == null) {
            providerTypeMapper.insert(po);
        } else {
            providerTypeMapper.updateById(po);
        }
        return po;
    }

    private void upsertModelOption(DefaultModel model, LocalDateTime now) {
        PlatformModelOptionPO po = modelOptionMapper.selectOne(Wrappers.<PlatformModelOptionPO>lambdaQuery()
                .eq(PlatformModelOptionPO::getProviderTypeId, model.providerTypeId())
                .eq(PlatformModelOptionPO::getModelCode, model.modelCode())
                .eq(PlatformModelOptionPO::getCapabilityType, model.capabilityType()));
        if (po == null) {
            po = new PlatformModelOptionPO();
            po.setId(SnowflakeIdGenerator.nextId());
            po.setProviderTypeId(model.providerTypeId());
            po.setModelCode(model.modelCode());
            po.setCapabilityType(model.capabilityType());
            po.setCreatedAt(now);
        }
        po.setDisplayName(model.displayName());
        po.setContextWindow(model.contextWindow());
        po.setMaxOutputTokens(model.maxOutputTokens());
        po.setEmbeddingDimensions(model.embeddingDimensions());
        po.setDefaultTemperature(model.temperature());
        po.setDefaultTopP(model.topP());
        po.setDefaultReasoningEffort(model.reasoningEffort());
        po.setStatus(STATUS_ENABLED);
        po.setSortOrder(model.sortOrder());
        po.setUpdatedAt(now);
        if (modelOptionMapper.selectById(po.getId()) == null) {
            modelOptionMapper.insert(po);
        } else {
            modelOptionMapper.updateById(po);
        }
    }

    private List<DefaultModel> defaultModels(Long openAiProviderTypeId, Long compatibleProviderTypeId) {
        return List.of(
                new DefaultModel(
                        openAiProviderTypeId,
                        "o3",
                        "o3",
                        "CHAT",
                        200000,
                        1024,
                        null,
                        null,
                        null,
                        "medium",
                        10),
                new DefaultModel(
                        compatibleProviderTypeId,
                        "tencent/Hunyuan-MT-7B",
                        "Hunyuan-MT-7B 免费翻译模型",
                        "CHAT",
                        33792,
                        33792,
                        null,
                        BigDecimal.valueOf(0.2),
                        BigDecimal.valueOf(0.7),
                        null,
                        20));
    }

    private record DefaultModel(
            Long providerTypeId,
            String modelCode,
            String displayName,
            String capabilityType,
            Integer contextWindow,
            Integer maxOutputTokens,
            Integer embeddingDimensions,
            BigDecimal temperature,
            BigDecimal topP,
            String reasoningEffort,
            Integer sortOrder) {
    }
}
