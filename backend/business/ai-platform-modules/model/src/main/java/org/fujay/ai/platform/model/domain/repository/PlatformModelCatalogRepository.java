package org.fujay.ai.platform.model.domain.repository;

import java.util.List;
import org.fujay.ai.platform.model.domain.aggregate.PlatformModelOption;
import org.fujay.ai.platform.model.domain.aggregate.PlatformProviderTypeOption;

public interface PlatformModelCatalogRepository {

    List<PlatformProviderTypeOption> listEnabledProviderTypes();

    List<PlatformModelOption> listEnabledModelOptions();

    List<PlatformProviderTypeOption> listAllProviderTypes();

    List<PlatformModelOption> listAllModelOptions();

    void saveProviderType(PlatformProviderTypeOption option);

    void saveModelOption(PlatformModelOption option);
}
