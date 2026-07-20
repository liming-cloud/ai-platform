package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.command.CreateAiApplicationCommand;
import com.aiplatform.appstudio.application.command.UpdateAiApplicationCommand;
import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.appstudio.domain.port.AiApplicationRepository;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.domain.port.chat.ChatCompletionPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunQueryPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunSnapshotPort;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeRetrievalPort;
import com.aiplatform.appstudio.presentation.permission.AppStudioPermissionCatalog;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.port.PermissionCheckPort;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringJUnitConfig(AppStudioApplicationMutationTransactionTest.TestConfiguration.class)
class AppStudioApplicationMutationTransactionTest {
    private static final TenantId TENANT = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");

    @jakarta.annotation.Resource
    private AppStudioApplicationService service;

    @jakarta.annotation.Resource
    private TransactionalApplicationRepository applications;

    @jakarta.annotation.Resource
    private RecordingTransactionManager transactionManager;

    @BeforeEach
    void reset() {
        applications.reset();
        transactionManager.reset();
    }

    @Test
    void createCommitsMainRowAndKnowledgeBindingsTogether() {
        service.createApplication(createCommand());

        assertThat(applications.transactionActiveAtSave).isTrue();
        assertThat(applications.committedApplication).isNotNull();
        assertThat(applications.committedKnowledgeBaseIds).containsExactly("20", "21");
        assertThat(transactionManager.commitCount).isOne();
        assertThat(transactionManager.rollbackCount).isZero();
    }

    @Test
    void createBindingFailureRollsBackTheAlreadyAttemptedMainRow() {
        applications.failBinding = true;

        assertThatThrownBy(() -> service.createApplication(createCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("binding persistence failed");

        assertThat(applications.mainRowWriteAttempted).isTrue();
        assertThat(applications.committedApplication).isNull();
        assertThat(applications.committedKnowledgeBaseIds).isEmpty();
        assertThat(transactionManager.commitCount).isZero();
        assertThat(transactionManager.rollbackCount).isOne();
    }

    @Test
    void updateBindingFailurePreservesThePreviousMainRowAndBindings() {
        AiApplication original = application("原应用", "9001", List.of("20"));
        applications.seed(original);
        applications.failBinding = true;

        assertThatThrownBy(() -> service.updateApplication(new UpdateAiApplicationCommand(
                TENANT,
                "1001",
                "3001",
                "新应用",
                "新描述",
                "9002",
                List.of("21", "22"),
                "新提示词"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("binding persistence failed");

        assertThat(applications.transactionActiveAtLock).isTrue();
        assertThat(applications.committedApplication.name()).isEqualTo("原应用");
        assertThat(applications.committedApplication.modelConfigId()).isEqualTo("9001");
        assertThat(applications.committedKnowledgeBaseIds).containsExactly("20");
        assertThat(transactionManager.commitCount).isZero();
        assertThat(transactionManager.rollbackCount).isOne();
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        TransactionalApplicationRepository applications() {
            return new TransactionalApplicationRepository();
        }

        @Bean
        AppStudioApplicationService service(TransactionalApplicationRepository applications) {
            RbacAuthorizationQueryPort authorization = mock(RbacAuthorizationQueryPort.class);
            when(authorization.findPermissionCodes(any(), any(), any())).thenReturn(Set.of(
                    AppStudioPermissionCatalog.APPLICATION_CREATE,
                    AppStudioPermissionCatalog.APPLICATION_UPDATE
            ));
            PermissionCheckPort permissionCheck = mock(PermissionCheckPort.class);
            when(permissionCheck.canAccess(any(), any(), any(), any(), any())).thenReturn(true);
            return AppStudioApplicationServiceTestFactory.create(
                    applications,
                    mock(ChatSessionRepository.class),
                    mock(KnowledgeRetrievalPort.class),
                    mock(ModelUsagePolicyPort.class),
                    mock(ChatCompletionPort.class),
                    mock(AppStudioDebugRunSnapshotPort.class),
                    mock(AppStudioDebugRunQueryPort.class),
                    permissionCheck,
                    authorization,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );
        }
    }

    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private int commitCount;
        private int rollbackCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // AbstractPlatformTransactionManager establishes synchronization after begin.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }

        void reset() {
            commitCount = 0;
            rollbackCount = 0;
        }
    }

    static final class TransactionalApplicationRepository implements AiApplicationRepository {
        private AiApplication committedApplication;
        private List<String> committedKnowledgeBaseIds = List.of();
        private boolean transactionActiveAtSave;
        private boolean transactionActiveAtLock;
        private boolean mainRowWriteAttempted;
        private boolean failBinding;

        @Override
        public String nextApplicationId() {
            return "3001";
        }

        @Override
        public void acquireMutationLock(TenantId tenantId, String applicationId) {
            transactionActiveAtLock = TransactionSynchronizationManager.isActualTransactionActive();
            if (committedApplication == null
                    || !committedApplication.tenantId().equals(tenantId)
                    || !committedApplication.applicationId().equals(applicationId)) {
                throw new IllegalStateException("application missing");
            }
        }

        @Override
        public AiApplication save(AiApplication application) {
            transactionActiveAtSave = TransactionSynchronizationManager.isActualTransactionActive();
            mainRowWriteAttempted = true;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committedApplication = application;
                }
            });
            if (failBinding) {
                throw new IllegalStateException("binding persistence failed");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committedKnowledgeBaseIds = List.copyOf(application.knowledgeBaseIds());
                }
            });
            return application;
        }

        @Override
        public Optional<AiApplication> findById(TenantId tenantId, String applicationId) {
            return Optional.ofNullable(committedApplication)
                    .filter(application -> application.tenantId().equals(tenantId))
                    .filter(application -> application.applicationId().equals(applicationId));
        }

        @Override
        public List<AiApplication> findCatalogWindowByTenant(TenantId tenantId, int limit) {
            return findAllByTenant(tenantId);
        }

        @Override
        public List<AiApplication> findAllByTenant(TenantId tenantId) {
            return Optional.ofNullable(committedApplication)
                    .filter(application -> application.tenantId().equals(tenantId))
                    .map(List::of)
                    .orElseGet(List::of);
        }

        @Override
        public List<AiApplication> findVisibleApplications(TenantId tenantId, String principalId) {
            return findAllByTenant(tenantId).stream()
                    .filter(application -> application.visiblePrincipalIds().contains(principalId))
                    .toList();
        }

        void seed(AiApplication application) {
            committedApplication = application;
            committedKnowledgeBaseIds = List.copyOf(application.knowledgeBaseIds());
        }

        void reset() {
            committedApplication = null;
            committedKnowledgeBaseIds = List.of();
            transactionActiveAtSave = false;
            transactionActiveAtLock = false;
            mainRowWriteAttempted = false;
            failBinding = false;
        }
    }

    private static CreateAiApplicationCommand createCommand() {
        return new CreateAiApplicationCommand(
                TENANT,
                "1001",
                AiApplicationType.KNOWLEDGE_QA,
                "法务助手",
                "受控法务问答",
                "9001",
                List.of("20", "21"),
                "仅根据授权知识回答"
        );
    }

    private static AiApplication application(String name, String modelConfigId, List<String> knowledgeBaseIds) {
        return new AiApplication(
                TENANT,
                "3001",
                AiApplicationType.KNOWLEDGE_QA,
                name,
                "企业知识问答",
                modelConfigId,
                knowledgeBaseIds,
                List.of(),
                Set.of("1001"),
                "仅根据授权知识回答",
                ApplicationPublicationStatus.DRAFT,
                NOW,
                NOW
        );
    }
}
