package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.domain.release.ApplicationReleaseConflictException;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppApplicationPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppBoundKnowledgeBasePersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppApplicationMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppAiApplicationRepositoryAdapterTest {
    private static final TenantId TENANT = TenantId.of("2001");

    @Test
    void catalogWindowHydratesEveryApplicationWithOneBatchBindingQuery() {
        AppApplicationMapper mapper = mock(AppApplicationMapper.class);
        AppAiApplicationRepositoryAdapter repository = new AppAiApplicationRepositoryAdapter(mapper);
        when(mapper.selectList(any())).thenReturn(List.of(application(3001L), application(3002L)));
        when(mapper.findActiveBindingsByApplicationIds(2001L, List.of(3001L, 3002L)))
                .thenReturn(List.of(binding(3001L, 20L), binding(3002L, 21L), binding(3002L, 22L)));

        assertThat(repository.findCatalogWindowByTenant(TENANT, 201))
                .satisfiesExactly(
                        first -> assertThat(first.knowledgeBaseIds()).containsExactly("20"),
                        second -> assertThat(second.knowledgeBaseIds()).containsExactly("21", "22")
                );

        verify(mapper).selectList(any());
        verify(mapper).findActiveBindingsByApplicationIds(2001L, List.of(3001L, 3002L));
        verify(mapper, never()).findActiveBindings(any(), any());
    }

    @Test
    void catalogWindowRejectsCrossTenantBindingInsteadOfHydratingIt() {
        AppApplicationMapper mapper = mock(AppApplicationMapper.class);
        AppAiApplicationRepositoryAdapter repository = new AppAiApplicationRepositoryAdapter(mapper);
        when(mapper.selectList(any())).thenReturn(List.of(application(3001L)));
        AppBoundKnowledgeBasePersistenceEntity foreign = binding(3001L, 20L);
        foreign.setTenantId(2999L);
        when(mapper.findActiveBindingsByApplicationIds(2001L, List.of(3001L))).thenReturn(List.of(foreign));

        assertThatThrownBy(() -> repository.findCatalogWindowByTenant(TENANT, 201))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("越界数据");
    }

    @Test
    void catalogWindowCannotExceedTheSinglePageProbeBound() {
        AppApplicationMapper mapper = mock(AppApplicationMapper.class);
        AppAiApplicationRepositoryAdapter repository = new AppAiApplicationRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.findCatalogWindowByTenant(TENANT, 202))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("201");
        verifyNoInteractions(mapper);
    }

    @Test
    void mutationLockUsesTheTenantScopedApplicationRowAndFailsClosedWhenMissing() {
        AppApplicationMapper mapper = mock(AppApplicationMapper.class);
        AppAiApplicationRepositoryAdapter repository = new AppAiApplicationRepositoryAdapter(mapper);
        when(mapper.lockApplication(2001L, 3001L)).thenReturn(Instant.parse("2026-07-22T12:00:00Z"));

        repository.acquireMutationLock(TENANT, "3001");
        verify(mapper).lockApplication(2001L, 3001L);

        assertThatThrownBy(() -> repository.acquireMutationLock(TENANT, "3002"))
                .isInstanceOf(ApplicationReleaseConflictException.class)
                .hasMessageContaining("不存在或已删除");
    }

    private static AppApplicationPersistenceEntity application(Long applicationId) {
        AppApplicationPersistenceEntity entity = new AppApplicationPersistenceEntity();
        entity.setId(applicationId);
        entity.setTenantId(2001L);
        entity.setAppCode("APP-" + applicationId);
        entity.setAppName("应用 " + applicationId);
        entity.setAppType("KNOWLEDGE_QA");
        entity.setDescription("企业知识问答");
        entity.setDefaultModelConfigId(9001L);
        entity.setSystemPrompt("请基于企业知识回答");
        entity.setStatus("DRAFT");
        entity.setCreatedAt(Instant.parse("2026-07-22T12:00:00Z"));
        entity.setCreatedBy(1001L);
        entity.setUpdatedAt(Instant.parse("2026-07-22T12:00:00Z"));
        entity.setUpdatedBy(1001L);
        entity.setVersion(0);
        return entity;
    }

    private static AppBoundKnowledgeBasePersistenceEntity binding(Long applicationId, Long knowledgeBaseId) {
        AppBoundKnowledgeBasePersistenceEntity binding = new AppBoundKnowledgeBasePersistenceEntity();
        binding.setTenantId(2001L);
        binding.setApplicationId(applicationId);
        binding.setKnowledgeBaseId(knowledgeBaseId);
        binding.setStatus("ACTIVE");
        return binding;
    }
}
