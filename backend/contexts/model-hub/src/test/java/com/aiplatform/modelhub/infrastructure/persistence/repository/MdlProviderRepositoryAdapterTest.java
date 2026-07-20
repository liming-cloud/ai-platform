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

import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlProviderPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlSecretPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlProviderMapper;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlSecretMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MdlProviderRepositoryAdapterTest {
    private static final String EVIDENCE_DIGEST = "a".repeat(64);
    private static final String TARGET_DIGEST = "b".repeat(64);
    @Test
    void nextProviderIdReturnsGeneratedDatabaseId() {
        MdlProviderMapper mapper = mock(MdlProviderMapper.class);
        when(mapper.nextProviderId()).thenReturn(3001L);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(mapper, mock(MdlSecretMapper.class));

        assertThat(repository.nextProviderId()).isEqualTo("3001");
    }

    @Test
    void nextProviderIdFailsClosedWhenSequenceReturnsNull() {
        MdlProviderMapper mapper = mock(MdlProviderMapper.class);
        when(mapper.nextProviderId()).thenReturn(null);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(mapper, mock(MdlSecretMapper.class));

        assertThatThrownBy(repository::nextProviderId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型供应商 ID生成失败：数据库序列未返回主键");
    }

    @Test
    void compareAndSetUpdatesOnlyExpectedProviderVersionAndStatus() {
        MdlProviderMapper mapper = mock(MdlProviderMapper.class);
        when(mapper.compareAndSet(any(MdlProviderPersistenceEntity.class), eq(0), eq("ACTIVE")))
                .thenReturn(1);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(mapper, mock(MdlSecretMapper.class));

        assertThat(repository.compareAndSet(provider(), 0, ModelProviderStatus.ACTIVE))
                .get()
                .extracting(ModelProvider::version)
                .isEqualTo(1);

        verify(mapper).compareAndSet(any(MdlProviderPersistenceEntity.class), eq(0), eq("ACTIVE"));
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(MdlProviderPersistenceEntity.class));
    }

    @Test
    void insertPersistsANewProviderWithoutReadBeforeWrite() {
        MdlProviderMapper mapper = mock(MdlProviderMapper.class);
        when(mapper.insert(any(MdlProviderPersistenceEntity.class))).thenReturn(1);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(mapper, mock(MdlSecretMapper.class));

        repository.insert(provider());

        ArgumentCaptor<MdlProviderPersistenceEntity> captor =
                ArgumentCaptor.forClass(MdlProviderPersistenceEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getApprovalRequestId()).isEqualTo("REQ-TEST");
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(MdlProviderPersistenceEntity.class));
    }

    @Test
    void insertFailsClosedWhenProviderInsertAffectsNoRows() {
        MdlProviderMapper mapper = mock(MdlProviderMapper.class);
        when(mapper.insert(any(MdlProviderPersistenceEntity.class))).thenReturn(0);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(mapper, mock(MdlSecretMapper.class));

        assertThatThrownBy(() -> repository.insert(provider()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型供应商保存失败：数据库未写入记录");
    }

    @Test
    void compareAndSetReturnsEmptyWhenProviderUpdateIsLost() {
        MdlProviderMapper mapper = mock(MdlProviderMapper.class);
        when(mapper.compareAndSet(any(MdlProviderPersistenceEntity.class), eq(0), eq("ACTIVE")))
                .thenReturn(0);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(mapper, mock(MdlSecretMapper.class));

        assertThat(repository.compareAndSet(provider(), 0, ModelProviderStatus.ACTIVE)).isEmpty();
    }

    @Test
    void approvalDecisionCasReturnsTrueOnlyWhenExactlyOnePendingRequestIsUpdated() {
        MdlProviderMapper mapper = mock(MdlProviderMapper.class);
        when(mapper.compareAndSetApprovalDecision(
                any(MdlProviderPersistenceEntity.class), eq(0), eq("PENDING_APPROVAL"), eq("REQ-TEST"),
                eq(EVIDENCE_DIGEST), eq(TARGET_DIGEST)
        ))
                .thenReturn(1);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(
                mapper,
                mock(MdlSecretMapper.class)
        );

        assertThat(repository.compareAndSetApprovalDecision(
                provider(), 0, ModelProviderStatus.PENDING_APPROVAL,
                " REQ-TEST ", EVIDENCE_DIGEST, TARGET_DIGEST
        )).get().extracting(ModelProvider::version).isEqualTo(1);

        ArgumentCaptor<MdlProviderPersistenceEntity> decision =
                ArgumentCaptor.forClass(MdlProviderPersistenceEntity.class);
        verify(mapper).compareAndSetApprovalDecision(
                decision.capture(), eq(0), eq("PENDING_APPROVAL"),
                eq("REQ-TEST"), eq(EVIDENCE_DIGEST), eq(TARGET_DIGEST)
        );
        assertThat(decision.getValue().getTenantId()).isEqualTo(2001L);
        assertThat(decision.getValue().getId()).isEqualTo(3001L);
        assertThat(decision.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void approvalDecisionCasReturnsFalseWhenThePendingRequestChangedConcurrently() {
        MdlProviderMapper mapper = mock(MdlProviderMapper.class);
        when(mapper.compareAndSetApprovalDecision(
                any(MdlProviderPersistenceEntity.class), eq(0), eq("PENDING_APPROVAL"), eq("REQ-TEST"),
                eq(EVIDENCE_DIGEST), eq(TARGET_DIGEST)
        ))
                .thenReturn(0);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(
                mapper,
                mock(MdlSecretMapper.class)
        );

        assertThat(repository.compareAndSetApprovalDecision(
                provider(), 0, ModelProviderStatus.PENDING_APPROVAL,
                "REQ-TEST", EVIDENCE_DIGEST, TARGET_DIGEST
        )).isEmpty();
    }

    @Test
    void approvalDecisionCasRejectsARequestIdThatDoesNotMatchTheDecidedAggregate() {
        MdlProviderMapper mapper = mock(MdlProviderMapper.class);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(
                mapper,
                mock(MdlSecretMapper.class)
        );

        assertThatThrownBy(() -> repository.compareAndSetApprovalDecision(
                provider(), 0, ModelProviderStatus.PENDING_APPROVAL,
                "REQ-OTHER", EVIDENCE_DIGEST, TARGET_DIGEST
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("与预期审批请求 ID 不一致");
        verify(mapper, never()).compareAndSetApprovalDecision(
                any(MdlProviderPersistenceEntity.class),
                anyInt(), any(), any(), any(), any()
        );
    }

    @Test
    void approvalDecisionCasFailsClosedWhenMoreThanOneRowIsReported() {
        MdlProviderMapper mapper = mock(MdlProviderMapper.class);
        when(mapper.compareAndSetApprovalDecision(
                any(MdlProviderPersistenceEntity.class), eq(0), eq("PENDING_APPROVAL"), eq("REQ-TEST"),
                eq(EVIDENCE_DIGEST), eq(TARGET_DIGEST)
        ))
                .thenReturn(2);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(
                mapper,
                mock(MdlSecretMapper.class)
        );

        assertThatThrownBy(() -> repository.compareAndSetApprovalDecision(
                provider(), 0, ModelProviderStatus.PENDING_APPROVAL,
                "REQ-TEST", EVIDENCE_DIGEST, TARGET_DIGEST
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型供应商原子写入返回异常行数");
    }

    @Test
    void findByIdRejectsNonNumericProviderIdWithChineseMessage() {
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(
                mock(MdlProviderMapper.class),
                mock(MdlSecretMapper.class)
        );

        assertThatThrownBy(() -> repository.findById(TenantId.of("2001"), "openai"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型供应商 ID 必须是数字型数据库主键");
    }

    @Test
    void findByIdLoadsLatestActiveSecretReferenceWithoutPlaintext() {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlSecretMapper secretMapper = mock(MdlSecretMapper.class);
        when(providerMapper.selectOne(anyQuery())).thenReturn(existingEntity());
        when(secretMapper.selectOne(anySecretQuery())).thenReturn(activeSecret());
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(providerMapper, secretMapper);

        ModelProvider provider = repository.findById(TenantId.of("2001"), "3001").orElseThrow();

        assertThat(provider.secretRef().secretId()).isEqualTo("9001");
        assertThat(provider.secretRef().maskedValue()).isEqualTo("sk-***tail");
    }

    @Test
    void findByIdFailsClosedWhenActiveSecretLacksMaskedValue() {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlSecretMapper secretMapper = mock(MdlSecretMapper.class);
        MdlSecretPersistenceEntity secret = activeSecret();
        secret.setSecretMaskedValue(null);
        when(providerMapper.selectOne(anyQuery())).thenReturn(existingEntity());
        when(secretMapper.selectOne(anySecretQuery())).thenReturn(secret);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(providerMapper, secretMapper);

        assertThatThrownBy(() -> repository.findById(TenantId.of("2001"), "3001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("凭证材料脱敏值不能为空");
    }

    @Test
    void findByIdFailsClosedWhenActiveSecretLacksId() {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlSecretMapper secretMapper = mock(MdlSecretMapper.class);
        MdlSecretPersistenceEntity secret = activeSecret();
        secret.setId(null);
        when(providerMapper.selectOne(anyQuery())).thenReturn(existingEntity());
        when(secretMapper.selectOne(anySecretQuery())).thenReturn(secret);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(providerMapper, secretMapper);

        assertThatThrownBy(() -> repository.findById(TenantId.of("2001"), "3001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型供应商凭证材料损坏：数据库主键不能为空");
    }

    @Test
    void findByIdFailsClosedWhenCredentialProviderHasNoActiveSecret() {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlSecretMapper secretMapper = mock(MdlSecretMapper.class);
        when(providerMapper.selectOne(anyQuery())).thenReturn(existingEntity());
        when(secretMapper.selectOne(anySecretQuery())).thenReturn(null);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(providerMapper, secretMapper);

        assertThatThrownBy(() -> repository.findById(TenantId.of("2001"), "3001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型供应商凭证材料缺失：providerId=3001");
    }

    @Test
    void findByIdReturnsNoAuthProviderWithoutSecretLookup() {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlSecretMapper secretMapper = mock(MdlSecretMapper.class);
        when(providerMapper.selectOne(anyQuery())).thenReturn(noAuthEntity());
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(providerMapper, secretMapper);

        ModelProvider provider = repository.findById(TenantId.of("2001"), "3002").orElseThrow();

        assertThat(provider.authType()).isEqualTo("no_auth");
        assertThat(provider.secretRef()).isNull();
        verify(secretMapper, never()).selectOne(anySecretQuery());
    }

    @Test
    void findAllFailsClosedWhenMapperReturnsNullRows() {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(providerMapper, mock(MdlSecretMapper.class));
        when(providerMapper.selectList(anyQuery())).thenReturn(null);

        assertThatThrownBy(() -> repository.findAll(TenantId.of("2001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型供应商查询结果不能为空");
    }

    @Test
    void findAllDetailsMapsApprovalRequestIdForConsoleReadModel() {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlProviderPersistenceEntity row = existingEntity();
        row.setApprovalRequestId("REQ-3001");
        when(providerMapper.selectList(anyQuery())).thenReturn(java.util.List.of(row));
        MdlProviderRepositoryAdapter repository = new MdlProviderRepositoryAdapter(providerMapper, mock(MdlSecretMapper.class));

        assertThat(repository.findAllDetails(TenantId.of("2001")))
                .singleElement()
                .satisfies(detail -> {
                    assertThat(detail.providerId()).isEqualTo("3001");
                    assertThat(detail.approvalRequestId()).isEqualTo("REQ-3001");
                });
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<MdlProviderPersistenceEntity> anyQuery() {
        return any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<MdlSecretPersistenceEntity> anySecretQuery() {
        return any(LambdaQueryWrapper.class);
    }

    private static MdlProviderPersistenceEntity existingEntity() {
        MdlProviderPersistenceEntity entity = new MdlProviderPersistenceEntity();
        entity.setId(3001L);
        entity.setTenantId(2001L);
        entity.setProviderCode("openai");
        entity.setProviderName("OpenAI");
        entity.setBaseUrl("https://api.openai.com/v1");
        entity.setAuthType("api_key");
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(Instant.parse("2026-05-22T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-05-22T01:00:00Z"));
        entity.setVersion(0);
        return entity;
    }

    private static ModelProvider provider() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "openai",
                "OpenAI",
                "https://api.openai.com/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***123"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-22T00:00:00Z"),
                Instant.parse("2026-05-22T01:00:00Z")
        );
    }

    private static MdlProviderPersistenceEntity noAuthEntity() {
        MdlProviderPersistenceEntity entity = existingEntity();
        entity.setId(3002L);
        entity.setProviderCode("ollama");
        entity.setProviderName("Ollama");
        entity.setBaseUrl("http://localhost:11434");
        entity.setAuthType("no_auth");
        return entity;
    }

    private static MdlSecretPersistenceEntity activeSecret() {
        MdlSecretPersistenceEntity entity = new MdlSecretPersistenceEntity();
        entity.setId(9001L);
        entity.setTenantId(2001L);
        entity.setProviderId(3001L);
        entity.setStatus("ACTIVE");
        entity.setSecretCiphertext("aes-gcm:v1:test");
        entity.setSecretMaskedValue("sk-***tail");
        return entity;
    }
}
