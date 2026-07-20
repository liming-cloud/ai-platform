package com.aiplatform.modelhub.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlConfigPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlConfigMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MdlConfigRepositoryAdapterTest {
    private static final String EVIDENCE_DIGEST = "a".repeat(64);
    private static final String TARGET_DIGEST = "b".repeat(64);
    @Test
    void nextModelConfigIdReturnsGeneratedDatabaseId() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());
        when(mapper.nextModelConfigId()).thenReturn(4001L);

        assertThat(repository.nextModelConfigId()).isEqualTo("4001");
    }

    @Test
    void nextModelConfigIdFailsClosedWhenSequenceReturnsNull() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());
        when(mapper.nextModelConfigId()).thenReturn(null);

        assertThatThrownBy(repository::nextModelConfigId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型配置 ID生成失败：数据库序列未返回主键");
    }

    @Test
    void findByIdRejectsMissingOptionsJsonInsteadOfEmptyRuntimeOptions() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());
        when(mapper.selectOne(anyQuery())).thenReturn(row(null));

        assertThatThrownBy(() -> repository.findById(TenantId.of("2001"), "3001", "4001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型 options JSON 不能为空");
    }

    @Test
    void findByIdRestoresRequiredOptionsJson() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());
        when(mapper.selectOne(anyQuery())).thenReturn(row("{\"providerKind\":\"openai-compatible\",\"temperature\":0.2}"));

        ModelRuntimeOptions options = repository.findById(TenantId.of("2001"), "3001", "4001")
                .orElseThrow()
                .options();

        assertThat(options.providerKind()).isEqualTo("openai-compatible");
        assertThat(options.values()).containsEntry("temperature", 0.2);
    }

    @Test
    void findByModelConfigIdUsesTenantScopedLookup() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());
        when(mapper.selectOne(anyQuery())).thenReturn(row("{\"providerKind\":\"openai-compatible\"}"));

        ModelConfig config = repository.findByModelConfigId(TenantId.of("2001"), "4001").orElseThrow();

        assertThat(config.modelConfigId()).isEqualTo("4001");
        assertThat(config.providerId()).isEqualTo("3001");
    }

    @Test
    void insertPersistsANewConfigWithoutReadBeforeWrite() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());
        when(mapper.insert(any(MdlConfigPersistenceEntity.class))).thenReturn(1);

        repository.insert(config());

        verify(mapper).insert(any(MdlConfigPersistenceEntity.class));
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(MdlConfigPersistenceEntity.class));
    }

    @Test
    void insertFailsClosedWhenConfigInsertAffectsNoRows() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());
        when(mapper.insert(any(MdlConfigPersistenceEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> repository.insert(config()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型配置保存失败：数据库未写入记录");
    }

    @Test
    void compareAndSetUpdatesOnlyExpectedConfigVersionAndStatus() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());
        when(mapper.compareAndSet(any(MdlConfigPersistenceEntity.class), eq(0), eq("ACTIVE")))
                .thenReturn(1);

        assertThat(repository.compareAndSet(config(), 0, ModelConfigStatus.ACTIVE))
                .get()
                .extracting(ModelConfig::version)
                .isEqualTo(1);

        verify(mapper).compareAndSet(any(MdlConfigPersistenceEntity.class), eq(0), eq("ACTIVE"));
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(MdlConfigPersistenceEntity.class));
    }

    @Test
    void compareAndSetReturnsEmptyWhenConfigUpdateIsLost() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());
        when(mapper.compareAndSet(any(MdlConfigPersistenceEntity.class), eq(0), eq("ACTIVE")))
                .thenReturn(0);

        assertThat(repository.compareAndSet(config(), 0, ModelConfigStatus.ACTIVE)).isEmpty();
        verify(mapper, never()).updateById(any(MdlConfigPersistenceEntity.class));
    }

    @Test
    void approvalDecisionCasReturnsTrueOnlyWhenExactlyOnePendingRequestIsUpdated() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        when(mapper.compareAndSetApprovalDecision(
                any(MdlConfigPersistenceEntity.class), eq(0), eq("PENDING_APPROVAL"), eq("REQ-TEST"),
                eq(EVIDENCE_DIGEST), eq(TARGET_DIGEST)
        ))
                .thenReturn(1);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());

        assertThat(repository.compareAndSetApprovalDecision(
                rejectedConfig(), 0, ModelConfigStatus.PENDING_APPROVAL,
                " REQ-TEST ", EVIDENCE_DIGEST, TARGET_DIGEST
        )).get().extracting(ModelConfig::version).isEqualTo(1);

        ArgumentCaptor<MdlConfigPersistenceEntity> decision =
                ArgumentCaptor.forClass(MdlConfigPersistenceEntity.class);
        verify(mapper).compareAndSetApprovalDecision(
                decision.capture(), eq(0), eq("PENDING_APPROVAL"),
                eq("REQ-TEST"), eq(EVIDENCE_DIGEST), eq(TARGET_DIGEST)
        );
        assertThat(decision.getValue().getTenantId()).isEqualTo(2001L);
        assertThat(decision.getValue().getId()).isEqualTo(4001L);
        assertThat(decision.getValue().getStatus()).isEqualTo("REJECTED");
        assertThat(decision.getValue().getConnectivityStatus()).isEqualTo("NOT_TESTED");
        assertThat(decision.getValue().getConnectivityCheckedAt()).isNull();
    }

    @Test
    void approvalDecisionCasReturnsFalseWhenThePendingRequestChangedConcurrently() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        when(mapper.compareAndSetApprovalDecision(
                any(MdlConfigPersistenceEntity.class), eq(0), eq("PENDING_APPROVAL"), eq("REQ-TEST"),
                eq(EVIDENCE_DIGEST), eq(TARGET_DIGEST)
        ))
                .thenReturn(0);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());

        assertThat(repository.compareAndSetApprovalDecision(
                rejectedConfig(), 0, ModelConfigStatus.PENDING_APPROVAL,
                "REQ-TEST", EVIDENCE_DIGEST, TARGET_DIGEST
        )).isEmpty();
    }

    @Test
    void approvalDecisionCasRejectsARequestIdThatDoesNotMatchTheDecidedAggregate() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());

        assertThatThrownBy(() -> repository.compareAndSetApprovalDecision(
                rejectedConfig(), 0, ModelConfigStatus.PENDING_APPROVAL,
                "REQ-OTHER", EVIDENCE_DIGEST, TARGET_DIGEST
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("与预期审批请求 ID 不一致");
        verify(mapper, never()).compareAndSetApprovalDecision(
                any(MdlConfigPersistenceEntity.class),
                anyInt(), any(), any(), any(), any()
        );
    }

    @Test
    void approvalDecisionCasFailsClosedWhenMoreThanOneRowIsReported() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        when(mapper.compareAndSetApprovalDecision(
                any(MdlConfigPersistenceEntity.class), eq(0), eq("PENDING_APPROVAL"), eq("REQ-TEST"),
                eq(EVIDENCE_DIGEST), eq(TARGET_DIGEST)
        ))
                .thenReturn(2);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());

        assertThatThrownBy(() -> repository.compareAndSetApprovalDecision(
                rejectedConfig(), 0, ModelConfigStatus.PENDING_APPROVAL,
                "REQ-TEST", EVIDENCE_DIGEST, TARGET_DIGEST
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型配置原子写入返回异常行数");
    }

    @Test
    void findByProviderIdFailsClosedWhenMapperReturnsNullRows() {
        MdlConfigMapper mapper = mock(MdlConfigMapper.class);
        MdlConfigRepositoryAdapter repository = new MdlConfigRepositoryAdapter(mapper, new JsonMapper());
        when(mapper.selectList(anyQuery())).thenReturn(null);

        assertThatThrownBy(() -> repository.findByProviderId(TenantId.of("2001"), "3001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型配置查询结果不能为空");
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<MdlConfigPersistenceEntity> anyQuery() {
        return any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<MdlConfigPersistenceEntity> anyUpdate() {
        return any(Wrapper.class);
    }

    private static ModelConfig config() {
        return new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                ModelType.CHAT,
                128000,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of("providerKind", "openai-compatible", "temperature", 0.2)),
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "",
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        );
    }

    private static ModelConfig rejectedConfig() {
        return new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                ModelType.CHAT,
                128000,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.REJECTED,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of("providerKind", "openai-compatible", "temperature", 0.2)),
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "",
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T03:00:00Z")
        );
    }

    private static MdlConfigPersistenceEntity row(String optionsJson) {
        MdlConfigPersistenceEntity row = new MdlConfigPersistenceEntity();
        row.setId(4001L);
        row.setTenantId(2001L);
        row.setProviderId(3001L);
        row.setModelCode("gpt-4.1-mini");
        row.setModelName("GPT 4.1 Mini");
        row.setModelType("CHAT");
        row.setContextWindow(128000);
        row.setInputPricePer1k(BigDecimal.ZERO);
        row.setOutputPricePer1k(BigDecimal.ZERO);
        row.setOptionsJson(optionsJson);
        row.setConnectivityStatus("NOT_TESTED");
        row.setStatus("ACTIVE");
        row.setCreatedAt(Instant.parse("2026-05-25T02:00:00Z"));
        row.setUpdatedAt(Instant.parse("2026-05-25T02:00:00Z"));
        row.setVersion(0);
        return row;
    }
}
