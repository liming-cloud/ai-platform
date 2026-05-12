package org.fujay.ai.platform.model.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.fujay.ai.platform.model.domain.model.ConfiguredModel;
import org.fujay.ai.platform.model.domain.aggregate.ModelInvocation;
import org.fujay.ai.platform.model.domain.aggregate.ModelProvider;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.ModelCatalogMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.ModelInvocationRecordMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.ModelProviderMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelCatalogPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelInvocationRecordPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelProviderPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MybatisPlusModelProviderRepositoryTest {

    @Mock
    private ModelProviderMapper providerMapper;

    @Mock
    private ModelCatalogMapper modelMapper;

    @Mock
    private ModelInvocationRecordMapper invocationMapper;

    @Test
    void saveUpdatesExistingModelAndDisablesRemovedModelsWithoutDeletingRows() {
        MybatisPlusModelProviderRepository repository =
                new MybatisPlusModelProviderRepository(providerMapper, modelMapper);
        LocalDateTime now = LocalDateTime.now();
        ConfiguredModel activeModel = new ConfiguredModel(
                200L,
                10L,
                100L,
                "tencent/Hunyuan-MT-7B",
                "Hunyuan-MT-7B 免费翻译模型",
                ModelCapabilityType.CHAT,
                "ENABLED",
                new ModelParameterDefaults(
                        33792, 33792, null, BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.7),
                        null, null, null, null),
                now.minusDays(1),
                now);
        ModelProvider provider = new ModelProvider(
                100L,
                10L,
                "SiliconFlow",
                ProviderType.OPENAI_COMPATIBLE,
                "https://api.siliconflow.cn",
                "v1:ciphertext",
                "last",
                "ENABLED",
                BigDecimal.ZERO,
                now.minusDays(1),
                now,
                List.of(activeModel));
        when(providerMapper.selectById(100L)).thenReturn(new ModelProviderPO());
        when(modelMapper.selectList(any())).thenReturn(List.of(existingModel(200L, "ENABLED"), existingModel(201L, "ENABLED")));

        repository.save(provider);

        verify(providerMapper).updateById(any(ModelProviderPO.class));
        ArgumentCaptor<ModelCatalogPO> modelCaptor = ArgumentCaptor.forClass(ModelCatalogPO.class);
        verify(modelMapper, times(2)).updateById(modelCaptor.capture());
        assertThat(modelCaptor.getAllValues())
                .anySatisfy(model -> {
                    assertThat(model.getId()).isEqualTo(200L);
                    assertThat(model.getModelCode()).isEqualTo("tencent/Hunyuan-MT-7B");
                    assertThat(model.getStatus()).isEqualTo("ENABLED");
                })
                .anySatisfy(model -> {
                    assertThat(model.getId()).isEqualTo(201L);
                    assertThat(model.getStatus()).isEqualTo("DISABLED");
                });
        verify(modelMapper, never()).delete(any());
        verify(modelMapper, never()).insert(any(ModelCatalogPO.class));
    }

    @Test
    void appendInvocationPersistsTraceUsageAndCostMetadataOnly() {
        MybatisPlusModelInvocationRepository repository =
                new MybatisPlusModelInvocationRepository(invocationMapper);
        LocalDateTime now = LocalDateTime.now();
        ConfiguredModel model = new ConfiguredModel(
                200L,
                10L,
                100L,
                "Qwen/Qwen2.5-7B-Instruct",
                "Qwen2.5",
                ModelCapabilityType.CHAT,
                "ENABLED",
                new ModelParameterDefaults(33792, 2048, null, null, null, null, null, null, null),
                now,
                now);
        ModelProvider provider = new ModelProvider(
                100L,
                10L,
                "SiliconFlow",
                ProviderType.OPENAI_COMPATIBLE,
                "https://api.siliconflow.cn",
                "v1:ciphertext",
                "last",
                "ENABLED",
                BigDecimal.ZERO,
                now,
                now,
                List.of(model));

        repository.append(ModelInvocation.success(
                9001L,
                provider,
                model,
                "CHAT",
                "trace-s5-chat",
                128,
                11,
                7,
                18,
                12,
                8,
                BigDecimal.valueOf(0.0012),
                null));

        ArgumentCaptor<ModelInvocationRecordPO> invocationCaptor =
                ArgumentCaptor.forClass(ModelInvocationRecordPO.class);
        verify(invocationMapper).insert(invocationCaptor.capture());
        ModelInvocationRecordPO po = invocationCaptor.getValue();
        assertThat(po.getInvocationKind()).isEqualTo("CHAT");
        assertThat(po.getTraceId()).isEqualTo("trace-s5-chat");
        assertThat(po.getPromptTokens()).isEqualTo(11);
        assertThat(po.getCompletionTokens()).isEqualTo(7);
        assertThat(po.getTotalTokens()).isEqualTo(18);
        assertThat(po.getInputChars()).isEqualTo(12);
        assertThat(po.getOutputChars()).isEqualTo(8);
        assertThat(po.getCostUsd()).isEqualByComparingTo("0.0012");
        assertThat(po.getCostMissingReason()).isNull();
        assertThat(po.getErrorMessage()).isNull();
    }

    private ModelCatalogPO existingModel(Long id, String status) {
        ModelCatalogPO po = new ModelCatalogPO();
        po.setId(id);
        po.setTenantId(10L);
        po.setProviderId(100L);
        po.setModelCode("old-model-" + id);
        po.setDisplayName("old model");
        po.setCapabilityType("CHAT");
        po.setStatus(status);
        po.setContextWindow(1024);
        po.setMaxOutputTokens(1024);
        po.setCreatedAt(LocalDateTime.now().minusDays(1));
        po.setUpdatedAt(LocalDateTime.now().minusDays(1));
        return po;
    }
}
