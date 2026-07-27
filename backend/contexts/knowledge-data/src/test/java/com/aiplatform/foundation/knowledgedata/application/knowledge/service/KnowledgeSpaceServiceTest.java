package com.aiplatform.foundation.knowledgedata.application.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.knowledge.command.CreateKnowledgeSpaceCommand;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.query.ListKnowledgeSpacesQuery;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceCreation;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceCreationOutcome;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceRepository;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpacePage;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpace;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeSpaceServiceTest {

    private static final TenantId TENANT_ID =
            new TenantId(100000000000000001L);
    private static final MembershipId MEMBERSHIP_ID =
            new MembershipId(4921825928971026434L);
    private static final long SPACE_ID = 210000000000000001L;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 有写权限时使用可信租户创建知识空间() {
        var repository = new RecordingRepository();
        var service = new KnowledgeSpaceService(repository, CLOCK, () -> SPACE_ID, 3);

        var result = service.create(new CreateKnowledgeSpaceCommand(
                context("knowledge:write"),
                "request-00000001",
                "门店商品知识",
                "供门店机器人使用"));

        assertEquals(Long.toString(SPACE_ID), result.knowledgeSpace().id());
        assertEquals(TENANT_ID, repository.creation.space().tenantId());
        assertEquals(MEMBERSHIP_ID.value(), repository.creation.space().createdBy().value());
        assertEquals(3, repository.tenantLimit);
    }

    @Test
    void 缺少写权限时在访问仓储前失败关闭() {
        var repository = new RecordingRepository();
        var service = new KnowledgeSpaceService(repository, CLOCK, () -> SPACE_ID, 3);

        assertThrows(KnowledgePermissionDeniedException.class, () -> service.create(
                new CreateKnowledgeSpaceCommand(
                        context("knowledge:read"),
                        "request-00000001",
                        "门店商品知识",
                        null)));
        assertTrue(repository.creation == null);
    }

    @Test
    void 列表只使用可信上下文中的租户() {
        var repository = new RecordingRepository();
        var service = new KnowledgeSpaceService(repository, CLOCK, () -> SPACE_ID, 3);

        var page = service.list(new ListKnowledgeSpacesQuery(
                context("knowledge:read"),
                PageQuery.defaults()));

        assertEquals(TENANT_ID, repository.listTenant);
        assertTrue(page.items().isEmpty());
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TENANT_ID,
                new SubjectId(1843651857942052866L),
                MEMBERSHIP_ID,
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode(permission)),
                "trace-0001");
    }

    private static final class RecordingRepository implements KnowledgeSpaceRepository {

        private KnowledgeSpaceCreation creation;
        private int tenantLimit;
        private TenantId listTenant;

        @Override
        public KnowledgeSpaceCreationOutcome create(
                KnowledgeSpaceCreation creation,
                int tenantLimit) {
            this.creation = creation;
            this.tenantLimit = tenantLimit;
            return new KnowledgeSpaceCreationOutcome(creation.space(), false);
        }

        @Override
        public KnowledgeSpacePage findPage(
                TenantId tenantId,
                long pageNumber,
                long pageSize) {
            listTenant = tenantId;
            return new KnowledgeSpacePage(List.of(), pageNumber, pageSize, 0);
        }
    }
}
