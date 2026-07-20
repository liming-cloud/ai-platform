package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.appstudio.application.command.CreateAiApplicationCommand;
import com.aiplatform.appstudio.application.command.PublishAiApplicationCommand;
import com.aiplatform.appstudio.application.command.ReplaceApplicationAccessPolicyCommand;
import com.aiplatform.appstudio.application.command.RollbackApplicationReleaseCommand;
import com.aiplatform.appstudio.application.command.UpdateAiApplicationCommand;
import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.port.ApplicationReleaseRepository;
import com.aiplatform.appstudio.infrastructure.persistence.repository.AppAiApplicationRepositoryAdapter;
import com.aiplatform.appstudio.infrastructure.persistence.repository.AppApplicationReleaseRepositoryAdapter;
import com.aiplatform.shared.kernel.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class AppStudioApplicationReleaseTransactionContractTest {
    @Test
    void everyApplicationMutationUsesOneTransactionAndTheSharedApplicationRowLock() throws Exception {
        assertTransactional("createApplication", CreateAiApplicationCommand.class);
        assertTransactional("updateApplication", UpdateAiApplicationCommand.class);
        assertTransactional("replaceApplicationAccessPolicy", ReplaceApplicationAccessPolicyCommand.class);
        assertTransactional("publishApplication", PublishAiApplicationCommand.class);
        assertTransactional("rollbackApplication", RollbackApplicationReleaseCommand.class);

        Transactional saveTransaction = AppAiApplicationRepositoryAdapter.class
                .getMethod("save", AiApplication.class)
                .getAnnotation(Transactional.class);
        assertThat(saveTransaction).isNotNull();
        assertThat(saveTransaction.propagation()).isEqualTo(Propagation.MANDATORY);

        Transactional mutationLockTransaction = AppAiApplicationRepositoryAdapter.class
                .getMethod("acquireMutationLock", TenantId.class, String.class)
                .getAnnotation(Transactional.class);
        assertThat(mutationLockTransaction).isNotNull();
        assertThat(mutationLockTransaction.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void publishRollbackAndReleasePointerOutboxMutationShareSpringTransactionBoundaries() throws Exception {
        assertThat(AppStudioApplicationService.class
                .getMethod("publishApplication", PublishAiApplicationCommand.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(AppStudioApplicationService.class
                .getMethod("rollbackApplication", RollbackApplicationReleaseCommand.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(AppApplicationReleaseRepositoryAdapter.class
                .getMethod("createAndActivate", ApplicationReleaseRepository.MutationCommand.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static void assertTransactional(String methodName, Class<?> commandType) throws Exception {
        assertThat(AppStudioApplicationService.class
                .getMethod(methodName, commandType)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }
}
