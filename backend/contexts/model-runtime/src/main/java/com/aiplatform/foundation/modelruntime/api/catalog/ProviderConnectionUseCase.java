package com.aiplatform.foundation.modelruntime.api.catalog;

import com.aiplatform.foundation.modelruntime.api.catalog.model.CreateProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.CreateProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ListProviderConnectionsQuery;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProbeProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProbeProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProviderConnectionPage;

/**
 * 供应商连接管理的公开应用契约。
 */
public interface ProviderConnectionUseCase {

    CreateProviderConnectionResult create(CreateProviderConnectionCommand command);

    ProviderConnectionPage list(ListProviderConnectionsQuery query);

    ProbeProviderConnectionResult probe(ProbeProviderConnectionCommand command);
}
