package org.fujay.ai.platform.model.infrastructure.bootstrap;

import org.fujay.ai.platform.model.application.service.ModelProviderApplicationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class PlatformModelCatalogBootstrapRunner implements ApplicationRunner {

    private final ModelProviderApplicationService modelProviderApplicationService;

    public PlatformModelCatalogBootstrapRunner(ModelProviderApplicationService modelProviderApplicationService) {
        this.modelProviderApplicationService = modelProviderApplicationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        modelProviderApplicationService.bootstrapPlatformCatalog();
    }
}
