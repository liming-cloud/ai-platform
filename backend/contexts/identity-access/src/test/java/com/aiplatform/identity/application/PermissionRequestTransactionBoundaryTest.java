package com.aiplatform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.identity.application.port.PermissionRequestRepository;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequest;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestPage;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestQuery;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestStatus;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.model.ResourceAclSubject;
import com.aiplatform.shared.security.port.ResourceAclCommandPort;
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

@SpringJUnitConfig(PermissionRequestTransactionBoundaryTest.TestConfiguration.class)
class PermissionRequestTransactionBoundaryTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

    @jakarta.annotation.Resource
    private PermissionRequestApplicationService service;

    @jakarta.annotation.Resource
    private RecordingTransactionManager transactionManager;

    @jakarta.annotation.Resource
    private TransactionalPermissionRequestRepository repository;

    @jakarta.annotation.Resource
    private TransactionalAclPort acl;

    @jakarta.annotation.Resource
    private FailingTransactionalAuditPublisher auditPublisher;

    @BeforeEach
    void reset() {
        transactionManager.reset();
        repository.reset();
        acl.reset();
        auditPublisher.reset();
    }

    @Test
    void auditFailureRollsBackDecisionAndAclWithinOneActiveTransaction() {
        assertThatThrownBy(() -> service.decide(
                TENANT_ID,
                "1002",
                "request-transaction",
                "APPROVE",
                "批准",
                "TRACE-TRANSACTION-001"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("audit persistence failed");

        assertThat(repository.transactionActiveAtSave).isTrue();
        assertThat(acl.transactionActiveAtGrant).isTrue();
        assertThat(auditPublisher.transactionActiveAtPublish).isTrue();
        assertThat(transactionManager.rollbackCount).isOne();
        assertThat(transactionManager.commitCount).isZero();
        assertThat(repository.committed.status()).isEqualTo(PermissionRequestStatus.PENDING);
        assertThat(acl.committedSubjects).isEmpty();
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        TransactionalPermissionRequestRepository permissionRequestRepository() {
            return new TransactionalPermissionRequestRepository();
        }

        @Bean
        TransactionalAclPort resourceAclCommandPort() {
            return new TransactionalAclPort();
        }

        @Bean
        FailingTransactionalAuditPublisher auditEventPublisher() {
            return new FailingTransactionalAuditPublisher();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        PermissionRequestApplicationService permissionRequestApplicationService(
                PermissionRequestRepository repository,
                Clock clock,
                ResourceAclCommandPort acl,
                AuditEventPublisher auditPublisher
        ) {
            return new PermissionRequestApplicationService(repository, clock, acl, auditPublisher);
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
            // AbstractPlatformTransactionManager 在 begin 后建立真实同步上下文。
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

    static final class TransactionalPermissionRequestRepository implements PermissionRequestRepository {
        private PermissionRequest committed;
        private boolean transactionActiveAtSave;

        private TransactionalPermissionRequestRepository() {
            reset();
        }

        @Override
        public PermissionRequest save(PermissionRequest request) {
            transactionActiveAtSave = TransactionSynchronizationManager.isActualTransactionActive();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committed = request;
                }
            });
            return request;
        }

        @Override
        public Optional<PermissionRequest> findById(TenantId tenantId, String requestId) {
            return TENANT_ID.equals(tenantId) && committed.requestId().equals(requestId)
                    ? Optional.of(committed)
                    : Optional.empty();
        }

        @Override
        public PermissionRequestPage findPage(PermissionRequestQuery query) {
            List<PermissionRequest> items = TENANT_ID.equals(query.tenantId()) ? List.of(committed) : List.of();
            return new PermissionRequestPage(items, items.size(), query.page(), query.pageSize());
        }

        void reset() {
            transactionActiveAtSave = false;
            committed = new PermissionRequest(
                    TENANT_ID,
                    "request-transaction",
                    "1001",
                    "AI_APPLICATION",
                    "3001",
                    "app-studio:application:use",
                    "需要完成合同审核",
                    PermissionRequestStatus.PENDING,
                    null,
                    null,
                    NOW.minusSeconds(60),
                    NOW.minusSeconds(60)
            );
        }
    }

    static final class TransactionalAclPort implements ResourceAclCommandPort {
        private Set<ResourceAclSubject> committedSubjects = Set.of();
        private boolean transactionActiveAtGrant;

        @Override
        public void grantAllowingSubject(
                TenantId tenantId,
                String resourceType,
                String resourceId,
                PermissionCode permissionCode,
                ResourceAclSubject subject,
                String actorId
        ) {
            transactionActiveAtGrant = TransactionSynchronizationManager.isActualTransactionActive();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committedSubjects = Set.of(subject);
                }
            });
        }

        @Override
        public void replaceAllowingSubjects(
                TenantId tenantId,
                String resourceType,
                String resourceId,
                PermissionCode permissionCode,
                Set<ResourceAclSubject> subjects,
                String actorId
        ) {
            throw new UnsupportedOperationException("本测试只覆盖单主体授权");
        }

        void reset() {
            transactionActiveAtGrant = false;
            committedSubjects = Set.of();
        }
    }

    static final class FailingTransactionalAuditPublisher implements AuditEventPublisher {
        private boolean transactionActiveAtPublish;

        @Override
        public void publish(AuditEvent event) {
            transactionActiveAtPublish = TransactionSynchronizationManager.isActualTransactionActive();
            throw new IllegalStateException("audit persistence failed");
        }

        void reset() {
            transactionActiveAtPublish = false;
        }
    }
}
