package com.aiplatform.foundation.modelruntime.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.application.configuration.dto.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatInvocationTransientException;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedChatRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatMessage;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatResult;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRole;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelUsage;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultChatRouteExecutorTest {

    @Test
    void 仅调用前瞬态失败时应按冻结顺序尝试后备候选() {
        var attempts = new AtomicInteger();
        GovernedChatRuntime chats = (context, configuration, request) -> {
            if (attempts.incrementAndGet() == 1) {
                throw ChatInvocationTransientException.beforeOutput(new IllegalStateException());
            }
            return new ChatResult(request.deployment(), "后备回答", "STOP", ModelUsage.UNKNOWN);
        };
        var executor = new DefaultChatRouteExecutor(chats);

        var execution = executor.execute(
                context(), List.of(candidate(41), candidate(42)),
                List.of(new ChatMessage(ChatRole.USER, "问题")), 128);

        assertEquals(2, attempts.get());
        assertEquals(42, execution.selected().configuration().configurationId().value());
        assertEquals("后备回答", execution.result().content());
    }

    @Test
    void 配置不可用不得尝试后备候选() {
        var attempts = new AtomicInteger();
        GovernedChatRuntime chats = (context, configuration, request) -> {
            attempts.incrementAndGet();
            throw new ModelRuntimeUnavailableException("冻结配置不可用");
        };
        var executor = new DefaultChatRouteExecutor(chats);

        assertThrows(
                ModelRuntimeUnavailableException.class,
                () -> executor.execute(
                        context(), List.of(candidate(41), candidate(42)),
                        List.of(new ChatMessage(ChatRole.USER, "问题")), 128));
        assertEquals(1, attempts.get());
    }

    private static com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.GovernedChatRouteCandidate candidate(long id) {
        return new com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.GovernedChatRouteCandidate(
                new UsableModelConfigurationSnapshot(
                        new ModelConfigurationId(id), 1,
                        new ControlledModelCatalogEntryId(id + 100), 1,
                        ModelType.CHAT, ModelRuntimeCapabilities.chat(8192, 512)));
    }

    private static TrustedRequestContext context() {
        return new TrustedRequestContext(
                new TenantId(2001), new SubjectId(16001), new MembershipId(6001),
                CredentialType.PERSON, AccessChannel.CONSOLE, 1,
                Set.of(new PermissionCode("model:read")), "trace-route-executor");
    }
}
