package org.fujay.ai.platform.model.application.service;

import java.util.List;
import org.fujay.ai.platform.model.application.command.SaveModelProviderCommand;
import org.fujay.ai.platform.model.application.command.SavePlatformModelOptionCommand;
import org.fujay.ai.platform.model.application.command.SavePlatformProviderTypeCommand;
import org.fujay.ai.platform.model.application.command.TestModelCommand;
import org.fujay.ai.platform.model.application.dto.ModelProviderDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto.ModelOptionDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto.ProviderTypeOptionDto;
import org.fujay.ai.platform.model.application.dto.ModelTestDto;
import org.springframework.stereotype.Service;

@Service
public class ModelProviderApplicationService {

    private final ModelProviderManagementApplicationService providerManagementService;
    private final ModelPlatformCatalogApplicationService platformCatalogService;
    private final ModelProviderTestApplicationService providerTestService;

    public ModelProviderApplicationService(
            ModelProviderManagementApplicationService providerManagementService,
            ModelPlatformCatalogApplicationService platformCatalogService,
            ModelProviderTestApplicationService providerTestService) {
        this.providerManagementService = providerManagementService;
        this.platformCatalogService = platformCatalogService;
        this.providerTestService = providerTestService;
    }

    public ModelProviderDto saveProvider(SaveModelProviderCommand command) {
        return providerManagementService.saveProvider(command);
    }

    public List<ModelProviderDto> listProviders() {
        return providerManagementService.listProviders();
    }

    public ModelProviderOptionsDto listProviderOptions() {
        return platformCatalogService.listProviderOptions();
    }

    public ModelProviderOptionsDto listPlatformCatalog() {
        return platformCatalogService.listPlatformCatalog();
    }

    public ProviderTypeOptionDto savePlatformProviderType(SavePlatformProviderTypeCommand command) {
        return platformCatalogService.savePlatformProviderType(command);
    }

    public ModelOptionDto savePlatformModelOption(SavePlatformModelOptionCommand command) {
        return platformCatalogService.savePlatformModelOption(command);
    }

    public ModelTestDto testModel(TestModelCommand command) {
        return providerTestService.testModel(command);
    }

    public void bootstrapPlatformCatalog() {
        platformCatalogService.bootstrapPlatformCatalog();
    }

    public void bootstrapProviderIfAbsent(Long tenantId, SaveModelProviderCommand command) {
        providerManagementService.bootstrapProviderIfAbsent(tenantId, command);
    }
}
