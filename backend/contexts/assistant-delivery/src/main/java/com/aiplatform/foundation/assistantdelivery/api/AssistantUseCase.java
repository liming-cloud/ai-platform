package com.aiplatform.foundation.assistantdelivery.api;

import com.aiplatform.foundation.assistantdelivery.api.model.assistant.AssistantPage;
import com.aiplatform.foundation.assistantdelivery.api.model.version.AssistantVersionView;
import com.aiplatform.foundation.assistantdelivery.api.model.assistant.CreateAssistantCommand;
import com.aiplatform.foundation.assistantdelivery.api.model.assistant.CreateAssistantResult;
import com.aiplatform.foundation.assistantdelivery.api.model.version.CreateAssistantVersionCommand;
import com.aiplatform.foundation.assistantdelivery.api.model.version.CreateAssistantVersionResult;
import com.aiplatform.foundation.assistantdelivery.api.model.assistant.ListAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.api.model.version.PublishAssistantVersionCommand;
import com.aiplatform.foundation.assistantdelivery.api.model.version.PublishAssistantVersionResult;

/**
 * 助手身份、版本冻结与发布的公共用例。
 */
public interface AssistantUseCase {

    CreateAssistantResult create(CreateAssistantCommand command);

    AssistantPage list(ListAssistantsQuery query);

    CreateAssistantVersionResult createVersion(CreateAssistantVersionCommand command);

    PublishAssistantVersionResult publishVersion(PublishAssistantVersionCommand command);
}
