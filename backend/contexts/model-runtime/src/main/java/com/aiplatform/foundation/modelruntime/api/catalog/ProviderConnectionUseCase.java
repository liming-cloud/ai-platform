package com.aiplatform.foundation.modelruntime.api.catalog;

import com.aiplatform.foundation.modelruntime.api.catalog.model.provider.CreateProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.provider.CreateProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.provider.ListProviderConnectionsQuery;
import com.aiplatform.foundation.modelruntime.api.catalog.model.provider.ProbeProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.provider.ProbeProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.provider.ProviderConnectionPage;

/**
 * 供应商连接管理的公开应用契约。
 */
public interface ProviderConnectionUseCase {

    CreateProviderConnectionResult create(CreateProviderConnectionCommand command);

    ProviderConnectionPage list(ListProviderConnectionsQuery query);

    ProbeProviderConnectionResult probe(ProbeProviderConnectionCommand command);
}
