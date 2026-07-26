package com.aiplatform.foundation.assistantdelivery.api;

import com.aiplatform.foundation.assistantdelivery.api.model.AssistantPage;
import com.aiplatform.foundation.assistantdelivery.api.model.AssistantVersionView;
import com.aiplatform.foundation.assistantdelivery.api.model.CreateAssistantCommand;
import com.aiplatform.foundation.assistantdelivery.api.model.CreateAssistantResult;
import com.aiplatform.foundation.assistantdelivery.api.model.CreateAssistantVersionCommand;
import com.aiplatform.foundation.assistantdelivery.api.model.CreateAssistantVersionResult;
import com.aiplatform.foundation.assistantdelivery.api.model.ListAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.api.model.PublishAssistantVersionCommand;
import com.aiplatform.foundation.assistantdelivery.api.model.PublishAssistantVersionResult;

/**
 * 助手身份、版本冻结与发布的公共用例。
 */
public interface AssistantUseCase {

    CreateAssistantResult create(CreateAssistantCommand command);

    AssistantPage list(ListAssistantsQuery query);

    CreateAssistantVersionResult createVersion(CreateAssistantVersionCommand command);

    PublishAssistantVersionResult publishVersion(PublishAssistantVersionCommand command);
}
